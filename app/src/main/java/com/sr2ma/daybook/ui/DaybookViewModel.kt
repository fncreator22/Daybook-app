package com.sr2ma.daybook.ui

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sr2ma.daybook.R
import com.sr2ma.daybook.data.BackupCodec
import com.sr2ma.daybook.data.DaybookDatabase
import com.sr2ma.daybook.data.DaybookRepository
import com.sr2ma.daybook.domain.Dates
import com.sr2ma.daybook.domain.LogQuery
import com.sr2ma.daybook.domain.MeetingFilter
import com.sr2ma.daybook.domain.MeetingQuery
import com.sr2ma.daybook.domain.NaturalLanguageParser
import com.sr2ma.daybook.domain.ParsedIntent
import com.sr2ma.daybook.domain.ParseResult
import com.sr2ma.daybook.domain.TaskFilter
import com.sr2ma.daybook.domain.TaskQuery
import com.sr2ma.daybook.domain.TaskSort
import com.sr2ma.daybook.domain.TodayBuilder
import com.sr2ma.daybook.domain.model.LogEntry
import com.sr2ma.daybook.domain.model.LogKind
import com.sr2ma.daybook.domain.model.Meeting
import com.sr2ma.daybook.domain.model.Pass
import com.sr2ma.daybook.domain.model.Task
import com.sr2ma.daybook.domain.ScanResult
import com.sr2ma.daybook.ai.LlmEngine
import com.sr2ma.daybook.ai.ModelDownloader
import com.sr2ma.daybook.domain.ConversationLlmRouter
import com.sr2ma.daybook.data.dao.WhatsAppDao
import com.sr2ma.daybook.data.dao.GmailDao
import com.sr2ma.daybook.domain.model.GmailMessage
import com.sr2ma.daybook.domain.model.TaskStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate

/**
 * The app's only ViewModel.
 *
 * Daybook has five screens over one small dataset that they all share, so a
 * ViewModel per screen would mean five copies of the same lists and five places
 * to keep them in step. One state object, derived in one place, is the simpler
 * shape.
 *
 * Every state change goes through [update], which re-runs [derive]. That means
 * the sorted and grouped lists are computed once per change instead of once per
 * recomposition.
 */
class DaybookViewModel(
    private val repository: DaybookRepository,
    private val codec: BackupCodec = BackupCodec(),
    /** Null = AI model not yet downloaded; UNKNOWN intents show "download" prompt. */
    private val llmEngine: LlmEngine? = null,
    /** Used for fast model-present check on [openConversation]. */
    private val modelDownloader: ModelDownloader? = null,
) : ViewModel() {

    private val whatsAppDao = WhatsAppDao(repository.database)
    private val gmailDao = GmailDao(repository.database)

    private val _state = MutableStateFlow(derive(DaybookUiState(today = Dates.today())))
    val state: StateFlow<DaybookUiState> = _state.asStateFlow()

    private var messageCounter = 0L

    init {
        viewModelScope.launch {
            try {
                repository.refreshAll()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // A database that cannot be opened at all is unrecoverable from in
                // here, but the app must still become usable: the alternative is a
                // spinner that never stops.
                update { it.copy(message = nextMessage(R.string.error_load_failed)) }
            } finally {
                // In `finally` on purpose. Setting this only on the success path
                // would leave LoadingGate on screen for ever after a failed first
                // read, with no way for the user to even reach Settings.
                update { it.copy(loaded = true) }
            }
        }
        // Load recent WhatsApp and Gmail messages for the Today board.
        loadRecentWhatsAppMessages()
        loadRecentGmailMessages()
        // Every write refreshes a repository flow, which lands here and re-derives.
        // Collecting the flows separately keeps each write cheap: only the
        // list that actually changed is re-read from SQLite by the repository.
        listOf(repository.tasks, repository.logEntries, repository.meetings, repository.projects, repository.passes)
            .forEach { flow ->
                viewModelScope.launch { flow.collect { update { current -> current } } }
            }
    }

    // ---- Navigation and list controls ------------------------------------

    fun selectTab(tab: DaybookTab) = update { it.copy(tab = tab) }

    fun setTaskFilter(filter: TaskFilter) = update { it.copy(taskFilter = filter) }

    fun setTaskSort(sort: TaskSort) = update { it.copy(taskSort = sort) }

    fun setTaskProject(project: String?) = update { it.copy(taskProject = project) }

    fun setTaskSearch(query: String) = update { it.copy(taskSearch = query) }

    fun toggleGroupByProject() = update { it.copy(groupByProject = !it.groupByProject) }

    fun setLogKind(kind: LogKind?) = update { it.copy(logKind = kind) }

    fun setLogSearch(query: String) = update { it.copy(logSearch = query) }

    fun setMeetingFilter(filter: MeetingFilter) = update { it.copy(meetingFilter = filter) }

    fun setMeetingSearch(query: String) = update { it.copy(meetingSearch = query) }

    /** Called when the app returns to the foreground, in case the date rolled over. */
    fun refreshToday() = update { it.copy(today = Dates.today()) }

    fun consumeMessage() = update { it.copy(message = null) }

    /** Refresh the WhatsApp messages shown in the Today board. */
    fun loadRecentWhatsAppMessages() {
        viewModelScope.launch {
            val msgs = withContext(Dispatchers.IO) { whatsAppDao.recentMessages(20) }
            update { it.copy(recentWhatsAppMessages = msgs) }
        }
    }

    /** Refresh recent actionable or primary Gmail messages shown in the Today board. */
    fun loadRecentGmailMessages() {
        viewModelScope.launch {
            val msgs = withContext(Dispatchers.IO) { gmailDao.recentMessages(15, filterSpamAndPromo = true) }
            update { it.copy(recentGmailMessages = msgs) }
        }
    }

    /**
     * Converts a suggested action from an email into a real Task or Meeting with 1 tap.
     */
    fun convertGmailAction(msg: GmailMessage) {
        val action = msg.suggestedAction ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                gmailDao.markActioned(msg.id)
                if (action.startsWith("Task:", ignoreCase = true)) {
                    val raw = action.removePrefix("Task:").trim()
                    val parsed = NaturalLanguageParser.parse(raw, _state.value.today)
                    val task = Task(
                        title = parsed.taskTitle ?: raw,
                        dueDate = parsed.dueDate ?: _state.value.today,
                        priority = parsed.priority,
                        status = TaskStatus.OPEN,
                    )
                    repository.saveTask(task)
                } else if (action.startsWith("Meeting:", ignoreCase = true)) {
                    val raw = action.removePrefix("Meeting:").trim()
                    val parsed = NaturalLanguageParser.parse(raw, _state.value.today)
                    val meeting = Meeting(
                        title = parsed.meetingTitle ?: raw,
                        day = parsed.dueDate ?: _state.value.today,
                        attendees = parsed.meetingAttendees.joinToString(", "),
                    )
                    repository.saveMeeting(meeting)
                }
            }
            loadRecentGmailMessages()
            update { it.copy(message = nextMessage(R.string.gmail_action_converted)) }
        }
    }

    /** Dismiss an email card from the Today board. */
    fun dismissGmailMessage(msg: GmailMessage) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                gmailDao.delete(msg.id)
            }
            loadRecentGmailMessages()
        }
    }

    // ---- Editor sheets ---------------------------------------------------

    /**
     * Opens the task sheet on a blank task. [dueToday] pre-fills the due date for
     * the Today screen's add button, and [meetingId] attaches the new task to a
     * meeting when it is being captured as an action item.
     */
    fun newTask(dueToday: Boolean = false, meetingId: Long? = null) = update {
        it.copy(
            editor = Editor.TaskSheet(
                Task(
                    title = "",
                    dueDate = if (dueToday) it.today else null,
                    meetingId = meetingId,
                ),
            ),
        )
    }

    fun editTask(task: Task) = update { it.copy(editor = Editor.TaskSheet(task)) }

    fun newLogEntry(day: LocalDate? = null) = update {
        it.copy(editor = Editor.LogSheet(LogEntry(day = day ?: it.today, body = "")))
    }

    fun editLogEntry(entry: LogEntry) = update { it.copy(editor = Editor.LogSheet(entry)) }

    fun newMeeting() = update {
        it.copy(editor = Editor.MeetingSheet(Meeting(title = "", day = it.today)))
    }

    fun editMeeting(meeting: Meeting) = update { it.copy(editor = Editor.MeetingSheet(meeting)) }

    fun closeEditor() = update { it.copy(editor = null) }

    // ---- Wallet ----------------------------------------------------------

    fun openWalletScanner() = update { it.copy(walletScanOpen = true) }

    fun closeWalletScanner() = update { it.copy(walletScanOpen = false) }

    /**
     * Called when a barcode is decoded by the camera or gallery picker.
     * Closes the scanner and opens the PassSheet pre-populated with scan data.
     */
    fun onScanResult(result: ScanResult) = update {
        val seed = Pass(
            title = result.suggestedTitle ?: "",
            category = result.suggestedCategory,
            barcodeValue = result.barcodeValue,
            barcodeFormat = result.barcodeFormat,
            ocrText = result.ocrText,
        )
        it.copy(walletScanOpen = false, editor = Editor.PassSheet(seed))
    }

    fun editPass(pass: Pass) = update { it.copy(editor = Editor.PassSheet(pass)) }

    /**
     * Opens a blank pass sheet for manual card entry.
     * The user fills in all fields by hand — no camera needed.
     */
    fun newPassManual() = update {
        it.copy(editor = Editor.PassSheet(Pass(title = "", barcodeValue = "", barcodeFormat = "")))
    }

    /** Navigate to the Wallet tab from the Today dashboard "View more" button. */
    fun openWalletTab() = update { it.copy(tab = DaybookTab.WALLET) }

    /** Navigate to Wallet tab and open the add-pass scanner from the Today dashboard "+" button. */
    fun openWalletAdd() = update { it.copy(tab = DaybookTab.WALLET, walletScanOpen = true) }

    fun savePass(pass: Pass) {
        if (pass.title.isBlank()) return
        write(onSuccess = { it.copy(editor = null) }) { repository.savePass(pass) }
    }

    fun deletePass(pass: Pass) {
        write(
            failure = R.string.error_delete_failed,
            onSuccess = { it.copy(editor = null) },
        ) { repository.deletePass(pass) }
    }

    /**
     * Processes a gallery image URI through ML Kit barcode scanner + OCR and
     * opens PassSheet with whatever was found. If no barcode is detected the sheet
     * still opens so the user can type everything manually; a snackbar explains.
     */
    fun onGalleryImageSelected(context: android.content.Context, uri: android.net.Uri) {
        viewModelScope.launch {
            update { it.copy(busy = true) }
            try {
                val image = withContext(Dispatchers.IO) {
                    com.google.mlkit.vision.common.InputImage.fromFilePath(context, uri)
                }

                val barcodesDeferred = kotlinx.coroutines.CompletableDeferred<List<com.google.mlkit.vision.barcode.common.Barcode>>()
                com.google.mlkit.vision.barcode.BarcodeScanning.getClient().process(image)
                    .addOnSuccessListener { barcodesDeferred.complete(it) }
                    .addOnFailureListener { barcodesDeferred.complete(emptyList()) }
                val barcodes = barcodesDeferred.await()

                val ocrDeferred = kotlinx.coroutines.CompletableDeferred<String>()
                com.google.mlkit.vision.text.TextRecognition.getClient(
                    com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS
                ).process(image)
                    .addOnSuccessListener { ocrDeferred.complete(it.text) }
                    .addOnFailureListener { ocrDeferred.complete("") }
                val ocrText = ocrDeferred.await()

                val first = barcodes.firstOrNull()
                val seed = Pass(
                    title = "",
                    barcodeValue = first?.rawValue ?: "",
                    barcodeFormat = first?.let { barcodeFormatName(it.format) } ?: "",
                    ocrText = ocrText.take(500),
                )
                update { it.copy(busy = false, editor = Editor.PassSheet(seed)) }
                if (first == null) {
                    update { it.copy(message = nextMessage(R.string.wallet_no_barcode)) }
                }
            } catch (_: Exception) {
                update { it.copy(busy = false, message = nextMessage(R.string.wallet_no_barcode)) }
            }
        }
    }

    private fun barcodeFormatName(format: Int): String = when (format) {
        com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE    -> "QR_CODE"
        com.google.mlkit.vision.barcode.common.Barcode.FORMAT_EAN_13     -> "EAN_13"
        com.google.mlkit.vision.barcode.common.Barcode.FORMAT_EAN_8      -> "EAN_8"
        com.google.mlkit.vision.barcode.common.Barcode.FORMAT_CODE_128   -> "CODE_128"
        com.google.mlkit.vision.barcode.common.Barcode.FORMAT_CODE_39    -> "CODE_39"
        com.google.mlkit.vision.barcode.common.Barcode.FORMAT_PDF417     -> "PDF417"
        com.google.mlkit.vision.barcode.common.Barcode.FORMAT_AZTEC      -> "AZTEC"
        com.google.mlkit.vision.barcode.common.Barcode.FORMAT_DATA_MATRIX-> "DATA_MATRIX"
        else                                                               -> "UNKNOWN"
    }

    // ---- Voice agent --------------------------------------------------------

    /** Called by the UI when mic button is pressed; sets the listening flag. */
    fun startListening() = update { it.copy(isListening = true, voiceResult = null, voiceRetried = false, voiceRetryMessage = null) }

    /** Called when SpeechRecognizer returns a result (or error). */
    fun onVoiceResult(text: String) {
        val parsed = NaturalLanguageParser.parse(text, referenceDate = _state.value.today)
        update { it.copy(isListening = false, voiceRetried = false, voiceRetryMessage = null, voiceResult = VoiceAgentResult(text, parsed)) }
    }

    /** Called when SpeechRecognizer returns nothing (silence timeout, no match, error). */
    fun onVoiceNoMatch() = update {
        it.copy(
            isListening = false,
            voiceRetried = false,
            voiceRetryMessage = null,
            message = nextMessage(R.string.voice_error_no_match),
        )
    }

    /**
     * Called after the first recognition failure to show a hint in ListeningSheet
     * without closing it. Retry is handled by the LaunchedEffect in DaybookApp.kt.
     */
    fun setRetryMessage() = update {
        it.copy(voiceRetryMessage = "Didn't catch that - try again")
    }

    fun onVoiceStop() = update {
        it.copy(isListening = false, voiceRetried = false, voiceRetryMessage = null)
    }

    fun onVoiceError(code: Int) = update {
        val msg = if (code == android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
            nextMessage(R.string.mic_permission_denied)
        } else if (code == android.speech.SpeechRecognizer.ERROR_CLIENT) {
            nextMessage(R.string.voice_error_unavailable)
        } else {
            nextMessage(R.string.voice_error_no_match)
        }
        it.copy(isListening = false, voiceRetried = false, voiceRetryMessage = null, message = msg)
    }

    /** User tapped "Add it" on the confirmation sheet — commit to DB. */
    fun confirmVoiceResult() {
        val result = _state.value.voiceResult ?: return
        update { it.copy(voiceResult = null) }
        val parsed: ParseResult = result.parseResult
        val today = _state.value.today
        when (parsed.intent) {
            ParsedIntent.CREATE_TASK -> {
                val title = parsed.taskTitle?.takeIf { it.isNotBlank() } ?: result.spokenText
                write {
                    repository.saveTask(
                        Task(
                            title    = title,
                            priority = parsed.priority,
                            dueDate  = parsed.dueDate,
                        )
                    )
                }
            }
            ParsedIntent.CREATE_LOG -> {
                val body = parsed.logBody?.takeIf { it.isNotBlank() } ?: result.spokenText
                write {
                    repository.saveLogEntry(
                        LogEntry(body = body, kind = parsed.logKind, day = today)
                    )
                }
            }
            ParsedIntent.CREATE_MEETING -> {
                val title = parsed.meetingTitle?.takeIf { it.isNotBlank() } ?: result.spokenText
                // Use the parser-extracted date if present, fall back to today
                val day = parsed.dueDate ?: today
                write {
                    repository.saveMeeting(Meeting(title = title, day = day))
                }
            }
            ParsedIntent.UNKNOWN -> {
                // Treat as a plain task so nothing the user says is ever silently lost
                write { repository.saveTask(Task(title = result.spokenText)) }
            }
        }
    }

    /** User dismissed the confirmation sheet without saving. */
    fun dismissVoiceResult() = update { it.copy(voiceResult = null) }

    // ── Agent conversation ────────────────────────────────────────────────────

    /**
     * Opens the ConversationSheet and snaps whether the Gemma model is present.
     *
     * The model-present check is a fast [File.exists] call (no I/O).
     * [llmModelReady] drives whether UNKNOWN intents attempt inference or
     * show the "Download AI model in Settings" inline message.
     */
    fun openConversation() {
        val modelReady = modelDownloader?.isModelPresent() ?: false
        update { it.copy(conversationOpen = true, llmModelReady = modelReady) }
    }

    /**
     * Closes the ConversationSheet and runs the memory extraction pass.
     *
     * [ConversationMemoryEngine.extract] writes only the signal (entities,
     * preferences, one-line summary) to the DB — the full message list is
     * discarded from RAM here and never touches disk.
     */
    fun closeConversation() {
        val session = _state.value.conversationMessages
        update {
            it.copy(
                conversationOpen = false,
                conversationMessages = emptyList(),
                agentThinking = false,
                lastConversationParseResult = null,
            )
        }
        // Reset in-RAM session state (rate limit, dedup) for the next session.
        com.sr2ma.daybook.domain.ConversationSession.clear()
        if (session.isNotEmpty()) {
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    com.sr2ma.daybook.domain.ConversationMemoryEngine.extract(
                        session,
                        repository.database,
                    )
                }
            }
        }
    }

    /**
     * Deletes all rows from `conversation_summaries`.
     * Called from Settings → "Clear conversation memory".
     *
     * The in-RAM [ConversationSession] is also reset so any running session
     * starts fresh. Only the persisted signal is deleted — no user content
     * (chat text) is ever on disk, so there is nothing else to erase.
     */
    fun clearConversationHistory() {
        com.sr2ma.daybook.domain.ConversationSession.clear()
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                com.sr2ma.daybook.data.dao.ConversationDao(repository.database).deleteAll()
            }
        }
        update { it.copy(message = nextMessage(R.string.ai_memory_cleared)) }
    }

    /**
     * Deletes all rows from `user_preferences`.
     * Called from Settings → "Reset learned preferences".
     *
     * Preferences are soft signals (e.g. preferred meeting time, common
     * project names) extracted from conversation sessions. Resetting them
     * lets the agent start learning from scratch.
     */
    fun resetLearnedPreferences() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                repository.database.writableDatabase.delete(
                    DaybookDatabase.TABLE_USER_PREFERENCES, null, null,
                )
            }
        }
        update { it.copy(message = nextMessage(R.string.ai_preferences_reset)) }
    }

    /**
     * Downloads the Gemma 270M model from HuggingFace to internal storage.
     *
     * Progress is reported via [DaybookUiState.modelDownloadProgress] (0.0–1.0).
     * On success: model is marked ready and [DaybookUiState.llmModelReady] becomes true.
     * On failure: [DaybookUiState.modelDownloadError] is set for display, download UI resets.
     *
     * Requires HuggingFace account + Gemma license acceptance.
     */
    fun downloadModel() {
        val downloader = modelDownloader ?: return
        if (downloader.isModelPresent()) {
            update { it.copy(llmModelReady = true) }
            return
        }
        if (state.value.modelDownloadProgress != null) return  // already downloading
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            update { it.copy(modelDownloadProgress = 0f) }
            val result = downloader.download(
                url = com.sr2ma.daybook.ai.ModelDownloader.GEMMA_270M_V1_URL,
                expectedSha256 = com.sr2ma.daybook.ai.ModelDownloader.GEMMA_270M_V1_SHA256,
                versionTag = "v1",
                onProgress = com.sr2ma.daybook.ai.ModelDownloader.ProgressListener { downloaded, total ->
                    val progress = if (total > 0) downloaded.toFloat() / total else 0f
                    update { it.copy(modelDownloadProgress = progress) }
                },
            )
            when (result) {
                is com.sr2ma.daybook.ai.ModelDownloader.DownloadResult.Success -> {
                    update { it.copy(
                        modelDownloadProgress = null,
                        llmModelReady = true,
                        message = nextMessage(R.string.ai_model_downloaded),
                    ) }
                }
                is com.sr2ma.daybook.ai.ModelDownloader.DownloadResult.Failure -> {
                    update { it.copy(
                        modelDownloadProgress = null,
                        modelDownloadError = result.reason,
                    ) }
                }
            }
        }
    }

    /** Clears the model download error after the Settings screen has shown it. */
    fun dismissModelDownloadError() {
        update { it.copy(modelDownloadError = null) }
    }

    /**
     * Appends a user message, runs [NaturalLanguageParser], and produces a
     * structured agent reply with action chips.
     *
     * The full conversation text is never written to disk; only the extracted
     * signal (entities, preferences, 1-line summary) is persisted when the sheet
     * closes via [closeConversation].
     *
     * Intent → agent response + chips:
     * - CREATE_TASK    → "Got it — task: \"X\" …" + [Add task | Dismiss]
     * - CREATE_MEETING → "Meeting: \"X\" …"        + [Add meeting | Dismiss]
     * - CREATE_LOG     → "Logging a note: \"X\" …" + [Save | Dismiss]
     * - UNKNOWN        → clarifying question        + [Add as task | Add as meeting | Add as log | Dismiss]
     */
    fun sendConversationMessage(text: String) {
        if (text.isBlank()) return
        val safText = text.take(com.sr2ma.daybook.domain.ConversationSession.MAX_INPUT_CHARS)
        val userMsg = com.sr2ma.daybook.domain.ConversationMessage(
            text = safText,
            isUser = true,
        )

        // ── Stage 5 guardrails ──────────────────────────────────────────────
        // ConversationSession enforces: 5 msgs/10s rate limit, 60s dedup,
        // 20-msg hard cap. If add() returns false, surface feedback and stop.
        if (!com.sr2ma.daybook.domain.ConversationSession.add(userMsg)) {
            val guardMsg = when {
                com.sr2ma.daybook.domain.ConversationSession.isRateLimited() ->
                    com.sr2ma.daybook.domain.ConversationMessage(
                        text = "Slow down — give me a moment to catch up.",
                        isUser = false,
                    )
                _state.value.conversationMessages.size >=
                    com.sr2ma.daybook.domain.ConversationSession.MAX_MESSAGES ->
                    com.sr2ma.daybook.domain.ConversationMessage(
                        text = "This conversation is getting long. Close and start a new one.",
                        isUser = false,
                        suggestions = listOf("Dismiss"),
                    )
                else ->
                    // Duplicate text within 60 s — silently ignore (no message)
                    return
            }
            update { it.copy(conversationMessages = it.conversationMessages + guardMsg) }
            return
        }

        update {
            it.copy(
                conversationMessages = it.conversationMessages + userMsg,
                agentThinking = true,
            )
        }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            val today = _state.value.today
            val parsed = NaturalLanguageParser.parse(safText, referenceDate = today)

            val (agentText, chips) = when (parsed.intent) {
                ParsedIntent.CREATE_TASK -> {
                    val title = parsed.taskTitle?.takeIf { it.isNotBlank() } ?: safText
                    val priorityLabel = when (parsed.priority) {
                        com.sr2ma.daybook.domain.model.Priority.URGENT -> " (urgent)"
                        com.sr2ma.daybook.domain.model.Priority.HIGH   -> " (high priority)"
                        com.sr2ma.daybook.domain.model.Priority.LOW    -> " (low priority)"
                        else                                           -> ""
                    }
                    val dateLabel = parsed.dueDate?.let { ", due ${it}" } ?: ""
                    "Got it — task: \"$title\"$priorityLabel$dateLabel. Add it?" to
                        listOf("Add task", "Dismiss")
                }
                ParsedIntent.CREATE_MEETING -> {
                    val title = parsed.meetingTitle?.takeIf { it.isNotBlank() } ?: safText
                    val who = if (parsed.meetingAttendees.isNotEmpty())
                        " with ${parsed.meetingAttendees.joinToString(", ")}" else ""
                    val day = parsed.dueDate ?: today
                    "Meeting: \"$title\"$who on $day. Add it?" to
                        listOf("Add meeting", "Dismiss")
                }
                ParsedIntent.CREATE_LOG -> {
                    val body = parsed.logBody?.takeIf { it.isNotBlank() } ?: safText
                    val kindLabel = when (parsed.logKind) {
                        com.sr2ma.daybook.domain.model.LogKind.WIN      -> "win"
                        com.sr2ma.daybook.domain.model.LogKind.BLOCKER  -> "blocker"
                        com.sr2ma.daybook.domain.model.LogKind.DECISION -> "decision"
                        else                                            -> "note"
                    }
                    "Logging a $kindLabel: \"${body.take(80)}\". Save it?" to
                        listOf("Save", "Dismiss")
                }
                ParsedIntent.UNKNOWN -> {
                    // ── Stage 4: attempt LLM classification ──────────────────
                    val engine  = llmEngine
                    val modelOk = _state.value.llmModelReady

                    if (engine != null && modelOk) {
                        // LLM path — runs on IO (engine is thread-safe via Mutex).
                        val routeResult = withContext(Dispatchers.IO) {
                            ConversationLlmRouter.route(
                                userText  = safText,
                                today     = today,
                                llmEngine = engine,
                                db        = repository.database,
                            )
                        }
                        when (routeResult) {
                            is ConversationLlmRouter.RouteResult.Classified -> {
                                // LLM succeeded — re-run the Stage 3 render path
                                // with the LLM-produced ParseResult.
                                val llmParsed = routeResult.result
                                val (llmText, llmChips) = when (llmParsed.intent) {
                                    ParsedIntent.CREATE_TASK -> {
                                        val t = llmParsed.taskTitle?.takeIf { it.isNotBlank() } ?: safText
                                        val pl = when (llmParsed.priority) {
                                            com.sr2ma.daybook.domain.model.Priority.URGENT -> " (urgent)"
                                            com.sr2ma.daybook.domain.model.Priority.HIGH   -> " (high priority)"
                                            com.sr2ma.daybook.domain.model.Priority.LOW    -> " (low priority)"
                                            else -> ""
                                        }
                                        val dl = llmParsed.dueDate?.let { ", due $it" } ?: ""
                                        "Got it — task: \"$t\"$pl$dl. Add it?" to
                                            listOf("Add task", "Dismiss")
                                    }
                                    ParsedIntent.CREATE_MEETING -> {
                                        val t = llmParsed.meetingTitle?.takeIf { it.isNotBlank() } ?: safText
                                        val who = if (llmParsed.meetingAttendees.isNotEmpty())
                                            " with ${llmParsed.meetingAttendees.joinToString(", ")}" else ""
                                        val day = llmParsed.dueDate ?: today
                                        "Meeting: \"$t\"$who on $day. Add it?" to
                                            listOf("Add meeting", "Dismiss")
                                    }
                                    ParsedIntent.CREATE_LOG -> {
                                        val b = llmParsed.logBody?.takeIf { it.isNotBlank() } ?: safText
                                        val k = when (llmParsed.logKind) {
                                            com.sr2ma.daybook.domain.model.LogKind.WIN      -> "win"
                                            com.sr2ma.daybook.domain.model.LogKind.BLOCKER  -> "blocker"
                                            com.sr2ma.daybook.domain.model.LogKind.DECISION -> "decision"
                                            else -> "note"
                                        }
                                        "Logging a $k: \"${b.take(80)}\". Save it?" to
                                            listOf("Save", "Dismiss")
                                    }
                                    ParsedIntent.UNKNOWN -> {
                                        "I'm not sure what to do with that. What would you like?" to
                                            listOf("Add as task", "Add as meeting", "Add as log", "Dismiss")
                                    }
                                }
                                val llmMsg = com.sr2ma.daybook.domain.ConversationMessage(
                                    text = llmText, isUser = false, suggestions = llmChips,
                                )
                                update {
                                    it.copy(
                                        conversationMessages = it.conversationMessages + llmMsg,
                                        agentThinking = false,
                                        lastConversationParseResult = llmParsed,
                                    )
                                }
                                return@launch
                            }
                            is ConversationLlmRouter.RouteResult.Timeout -> {
                                val msg = com.sr2ma.daybook.domain.ConversationMessage(
                                    text = "AI took too long. Try again or add it manually.",
                                    isUser = false,
                                    suggestions = listOf("Add as task", "Add as meeting", "Dismiss"),
                                )
                                update { it.copy(conversationMessages = it.conversationMessages + msg, agentThinking = false) }
                                return@launch
                            }
                            is ConversationLlmRouter.RouteResult.ModelNotReady -> {
                                // Fall through to the no-model message below
                            }
                            is ConversationLlmRouter.RouteResult.ParseError,
                            is ConversationLlmRouter.RouteResult.Failure -> {
                                val msg = com.sr2ma.daybook.domain.ConversationMessage(
                                    text = "I couldn't process that. Add it manually?",
                                    isUser = false,
                                    suggestions = listOf("Add as task", "Add as meeting", "Add as log", "Dismiss"),
                                )
                                update { it.copy(conversationMessages = it.conversationMessages + msg, agentThinking = false) }
                                return@launch
                            }
                        }
                    }

                    // No model (or ModelNotReady fallback) — degraded response.
                    "I'm not sure what to do with that. " +
                        (if (!modelOk) "Download the AI model in Settings for smarter replies. " else "") +
                        "Or pick manually:" to
                        listOf("Add as task", "Add as meeting", "Add as log", "Dismiss")
                }
            }

            val agentMsg = com.sr2ma.daybook.domain.ConversationMessage(
                text = agentText,
                isUser = false,
                suggestions = chips,
            )
            update {
                it.copy(
                    conversationMessages = it.conversationMessages + agentMsg,
                    agentThinking = false,
                    lastConversationParseResult = parsed,
                )
            }
        }
    }

    /**
     * Called when the user taps a suggestion chip in the conversation.
     *
     * Saves the item described by [lastConversationParseResult] — the NLP result
     * from the most recent agent turn — then closes the sheet.
     *
     * Fallback for UNKNOWN chips: creates a plain task from the last user message.
     */
    fun onConversationSuggestion(suggestion: String) {
        if (suggestion == "Dismiss") {
            closeConversation()
            return
        }
        val parsed = _state.value.lastConversationParseResult
        val today  = _state.value.today
        // Grab last user text as fallback title/body
        val lastUserText = _state.value.conversationMessages
            .lastOrNull { it.isUser }?.text ?: ""

        when (suggestion) {
            "Add task" -> {
                val title = parsed?.taskTitle?.takeIf { it.isNotBlank() } ?: lastUserText
                write {
                    repository.saveTask(
                        Task(
                            title    = title,
                            priority = parsed?.priority ?: com.sr2ma.daybook.domain.model.Priority.MEDIUM,
                            dueDate  = parsed?.dueDate,
                        )
                    )
                }
            }
            "Add meeting" -> {
                val title = parsed?.meetingTitle?.takeIf { it.isNotBlank() } ?: lastUserText
                val day   = parsed?.dueDate ?: today
                write {
                    repository.saveMeeting(Meeting(title = title, day = day))
                }
            }
            "Save" -> {
                val body = parsed?.logBody?.takeIf { it.isNotBlank() } ?: lastUserText
                write {
                    repository.saveLogEntry(
                        LogEntry(
                            body = body,
                            kind = parsed?.logKind ?: com.sr2ma.daybook.domain.model.LogKind.NOTE,
                            day  = today,
                        )
                    )
                }
            }
            // UNKNOWN fallbacks — user picked a type manually
            "Add as task" -> write {
                repository.saveTask(Task(title = lastUserText))
            }
            "Add as meeting" -> write {
                repository.saveMeeting(Meeting(title = lastUserText, day = today))
            }
            "Add as log" -> write {
                repository.saveLogEntry(LogEntry(body = lastUserText, day = today))
            }
        }
        closeConversation()
    }

    /**
     * Called from [com.sr2ma.daybook.MainActivity] when the user shares text from another app
     * (Gmail, WhatsApp, browser) into Daybook. Runs the text through
     * NaturalLanguageParser and shows the VoiceResultSheet confirmation card
     * so the user can review before saving — identical UX to voice input.
     */
    fun confirmFromShare(text: String) {
        val parsed = NaturalLanguageParser.parse(text, referenceDate = _state.value.today)
        update { it.copy(voiceResult = VoiceAgentResult(text, parsed)) }
    }

    // ---- Tasks -----------------------------------------------------------


    /** Saves the sheet and closes it. A blank title is ignored rather than stored. */
    fun saveTask(task: Task) {
        if (task.title.isBlank()) return
        write(onSuccess = { it.copy(editor = null) }) { repository.saveTask(task) }
    }

    /**
     * Smart quick-add: runs the text through NaturalLanguageParser so the user
     * can type "meeting with Tom tomorrow" or "shipped the feature" in the quick-add
     * field and have it correctly saved as a meeting or log entry, not a task.
     */
    fun quickAddTask(title: String) {
        if (title.isBlank()) return
        val today = _state.value.today
        val parsed = com.sr2ma.daybook.domain.NaturalLanguageParser.parse(title, today)
        when (parsed.intent) {
            com.sr2ma.daybook.domain.ParsedIntent.CREATE_MEETING -> {
                val day = parsed.dueDate ?: today
                write {
                    repository.saveMeeting(
                        com.sr2ma.daybook.domain.model.Meeting(
                            title = parsed.meetingTitle?.ifBlank { null } ?: title.trim(),
                            attendees = parsed.meetingAttendees.joinToString(", "),
                            day = day,
                        )
                    )
                }
            }
            com.sr2ma.daybook.domain.ParsedIntent.CREATE_LOG -> {
                write {
                    repository.saveLogEntry(
                        com.sr2ma.daybook.domain.model.LogEntry(
                            day = today,
                            body = parsed.logBody?.ifBlank { null } ?: title.trim(),
                            kind = parsed.logKind,
                        )
                    )
                }
            }
            else -> {
                write {
                    repository.saveTask(
                        com.sr2ma.daybook.domain.model.Task(
                            title = parsed.taskTitle?.ifBlank { null } ?: title.trim(),
                            priority = parsed.priority,
                            dueDate = parsed.dueDate ?: today,
                        )
                    )
                }
            }
        }
    }


    /** Captures an action item from inside the meeting sheet, without leaving it. */
    fun addActionItem(meetingId: Long, title: String) {
        if (title.isBlank()) return
        write { repository.saveTask(Task(title = title, meetingId = meetingId)) }
    }

    fun toggleTaskDone(task: Task) {
        write { repository.toggleTaskDone(task) }
    }

    fun deleteTask(task: Task) {
        write(
            failure = R.string.error_delete_failed,
            onSuccess = { it.copy(editor = null) },
        ) { repository.deleteTask(task.id) }
    }

    // ---- Log -------------------------------------------------------------

    fun saveLogEntry(entry: LogEntry) {
        if (entry.body.isBlank()) return
        write(onSuccess = { it.copy(editor = null) }) { repository.saveLogEntry(entry) }
    }

    fun deleteLogEntry(entry: LogEntry) {
        write(
            failure = R.string.error_delete_failed,
            onSuccess = { it.copy(editor = null) },
        ) { repository.deleteLogEntry(entry.id) }
    }

    // ---- Meetings --------------------------------------------------------

    fun saveMeeting(meeting: Meeting) {
        if (meeting.title.isBlank()) return
        write(onSuccess = { it.copy(editor = null) }) { repository.saveMeeting(meeting) }
    }

    /**
     * Ticks a follow-up off, or reopens it. Kept separate from [saveMeeting] so it
     * can be driven straight from a list row without opening the sheet.
     */
    fun toggleFollowUpDone(meeting: Meeting) {
        write { repository.saveMeeting(meeting.copy(followUpDone = !meeting.followUpDone)) }
    }

    fun deleteMeeting(meeting: Meeting) {
        write(
            failure = R.string.error_delete_failed,
            onSuccess = { it.copy(editor = null) },
        ) { repository.deleteMeeting(meeting.id) }
    }

    // ---- Backup ----------------------------------------------------------

    /**
     * Writes the whole dataset as JSON.
     *
     * [openStream] is supplied by the screen, which is the only place that knows
     * about the picked document Uri and the ContentResolver. Keeping the Uri out of
     * here means this ViewModel needs no Context and stays unit-testable, and it is
     * called on the IO dispatcher so the resolver call is off the main thread.
     */
    fun exportTo(openStream: () -> OutputStream?) {
        viewModelScope.launch {
            update { it.copy(busy = true) }
            val snapshot = repository.snapshot()
            val saved = try {
                withContext(Dispatchers.IO) {
                    val stream = openStream() ?: error("Document could not be opened for writing")
                    stream.use { codec.write(snapshot, it) }
                }
                true
            } catch (e: CancellationException) {
                // Must be rethrown. A bare `catch (Exception)` would treat the
                // ViewModel being cleared as a failed export and report it.
                throw e
            } catch (_: Exception) {
                // Anything from a revoked Uri permission to a full disk lands here,
                // and the user gets the same recoverable answer either way.
                false
            }
            update {
                it.copy(
                    busy = false,
                    message = nextMessage(
                        if (saved) R.string.settings_export_success else R.string.settings_export_failure,
                    ),
                )
            }
        }
    }

    /**
     * Reads a Daybook JSON file back in. [replaceExisting] wipes what is already
     * stored first; merge is the default because it cannot lose anything.
     */
    fun importFrom(replaceExisting: Boolean, openStream: () -> InputStream?) {
        viewModelScope.launch {
            update { it.copy(busy = true) }
            val counts = try {
                val snapshot = withContext(Dispatchers.IO) {
                    val stream = openStream() ?: error("Document could not be opened for reading")
                    stream.use { codec.read(it) }
                }
                repository.importSnapshot(snapshot, replaceExisting)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // A malformed or unrelated file is a user mistake, not a crash. The
                // file is fully parsed before anything is written, so a bad file
                // cannot leave the database half-imported.
                null
            }
            update {
                it.copy(
                    busy = false,
                    message = if (counts == null) {
                        nextMessage(R.string.settings_import_failure)
                    } else {
                        nextMessage(
                            R.string.settings_import_success,
                            counts.tasks,
                            counts.logEntries,
                            counts.meetings,
                        )
                    },
                )
            }
        }
    }

    fun deleteEverything() {
        write(failure = R.string.error_delete_failed) { repository.deleteEverything() }
    }

    // ---- Internals -------------------------------------------------------

    /**
     * Runs a database write, reporting failure instead of crashing.
     *
     * An exception thrown inside `viewModelScope.launch` is not caught by anything:
     * it reaches the thread's uncaught handler and takes the process down. Without
     * this, a full disk or a locked database while ticking off a checkbox would kill
     * the app mid-tap. Every write goes through here.
     *
     * [onSuccess] runs only when [block] completed, which is what keeps an editor
     * open when its save failed — closing it would throw away what the user typed.
     */
    private fun write(
        @StringRes failure: Int = R.string.error_save_failed,
        onSuccess: (DaybookUiState) -> DaybookUiState = { it },
        block: suspend () -> Unit,
    ) {
        viewModelScope.launch {
            try {
                block()
                update(onSuccess)
            } catch (e: CancellationException) {
                // Cancellation is the ViewModel being cleared, not a failure, and
                // must be allowed to propagate or the scope cannot shut down.
                throw e
            } catch (_: Exception) {
                update { it.copy(message = nextMessage(failure)) }
            }
        }
    }

    /** Applies [transform], then recomputes everything that depends on it. */
    private fun update(transform: (DaybookUiState) -> DaybookUiState) {
        _state.value = derive(transform(_state.value))
    }

    /**
     * Fills in the raw lists from the repository and the derived views from the
     * current filters. This is the only place either is computed.
     */
    private fun derive(state: DaybookUiState): DaybookUiState {
        val tasks = repository.tasks.value
        val logEntries = repository.logEntries.value
        val meetings = repository.meetings.value
        val projects = repository.projects.value

        // Hoisted so the two editor-derived fields below can smart-cast on it.
        val editor = state.editor

        // Renaming or deleting the last task in a project can leave the Tasks screen
        // filtered by a project that no longer exists, which would show an empty list
        // with no obvious way back. Drop the filter instead.
        val project = state.taskProject?.takeIf { it in projects }

        val visibleTasks = TaskQuery.apply(
            tasks = tasks,
            filter = state.taskFilter,
            sort = state.taskSort,
            today = state.today,
            project = project,
            search = state.taskSearch,
        )

        return state.copy(
            tasks = tasks,
            logEntries = logEntries,
            meetings = meetings,
            projects = projects,
            passes = repository.passes.value,
            taskProject = project,
            board = TodayBuilder.build(tasks, meetings, logEntries, state.today),
            visibleTasks = visibleTasks,
            // Only paid for when the screen is actually grouped.
            taskGroups = if (state.groupByProject) {
                TaskQuery.groupByProject(visibleTasks)
            } else {
                emptyList()
            },
            logDays = LogQuery.groupByDay(logEntries, state.logKind, state.logSearch),
            visibleMeetings = MeetingQuery.apply(
                meetings = meetings,
                filter = state.meetingFilter,
                today = state.today,
                search = state.meetingSearch,
            ),
            // Both of these used to be computed in composition, which meant a scan
            // of every task and every meeting on each recomposition of an open
            // sheet -- including on every keystroke in it. Neither depends on
            // anything but the data and the open editor, so both belong here.
            editorActionItems = if (editor is Editor.MeetingSheet) {
                TaskQuery.forMeeting(tasks, editor.seed.id)
            } else {
                emptyList()
            },
            // Resolved rather than stored on the seed, so a meeting renamed while
            // its action item's sheet is open shows the new name.
            editorMeetingTitle = if (editor is Editor.TaskSheet) {
                editor.seed.meetingId?.let { id ->
                    meetings.firstOrNull { meeting -> meeting.id == id }?.title
                }
            } else {
                null
            },
        )
    }

    private fun nextMessage(@StringRes textRes: Int, vararg args: Any): UserMessage =
        UserMessage(id = ++messageCounter, textRes = textRes, args = args.toList())

    companion object {
        /**
         * Built from the container on the Application rather than by a DI framework.
         *
         * [llmEngine] and [modelDownloader] are optional: when null the conversation
         * agent runs in rule-engine-only mode (UNKNOWN intents show the "download model"
         * inline message instead of attempting inference).
         */
        fun factory(
            repository: DaybookRepository,
            llmEngine: LlmEngine? = null,
            modelDownloader: ModelDownloader? = null,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                DaybookViewModel(
                    repository      = repository,
                    llmEngine       = llmEngine,
                    modelDownloader = modelDownloader,
                )
            }
        }
    }
}
