package com.sr2ma.daybook.ui

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sr2ma.daybook.R
import com.sr2ma.daybook.data.BackupCodec
import com.sr2ma.daybook.data.DaybookRepository
import com.sr2ma.daybook.domain.Dates
import com.sr2ma.daybook.domain.LogQuery
import com.sr2ma.daybook.domain.MeetingFilter
import com.sr2ma.daybook.domain.MeetingQuery
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
) : ViewModel() {

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

    // ---- Tasks -----------------------------------------------------------

    /** Saves the sheet and closes it. A blank title is ignored rather than stored. */
    fun saveTask(task: Task) {
        if (task.title.isBlank()) return
        write(onSuccess = { it.copy(editor = null) }) { repository.saveTask(task) }
    }

    /** The one-line add field on Today: a task due today, everything else default. */
    fun quickAddTask(title: String) {
        if (title.isBlank()) return
        write { repository.saveTask(Task(title = title, dueDate = _state.value.today)) }
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
         * Built from the container on the Application rather than by a DI framework:
         * one ViewModel with one dependency does not need one.
         */
        fun factory(repository: DaybookRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { DaybookViewModel(repository) }
        }
    }
}
