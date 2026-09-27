package com.sr2ma.daybook.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.sr2ma.daybook.R
import com.sr2ma.daybook.domain.LogDay
import com.sr2ma.daybook.domain.MeetingFilter
import com.sr2ma.daybook.domain.TaskFilter
import com.sr2ma.daybook.domain.TaskGroup
import com.sr2ma.daybook.domain.TaskSort
import com.sr2ma.daybook.domain.TodayBoard
import com.sr2ma.daybook.domain.model.LogEntry
import com.sr2ma.daybook.domain.model.LogKind
import com.sr2ma.daybook.domain.model.Meeting
import com.sr2ma.daybook.domain.model.Pass
import com.sr2ma.daybook.domain.model.SyncStatus
import com.sr2ma.daybook.domain.model.Task
import com.sr2ma.daybook.domain.model.WhatsAppMessage
import java.time.LocalDate

/** The six destinations in the bottom bar. */
enum class DaybookTab(
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconRes: Int,
) {
    TODAY(R.string.nav_today, R.drawable.ic_today),
    TASKS(R.string.nav_tasks, R.drawable.ic_tasks),
    LOG(R.string.nav_log, R.drawable.ic_log),
    MEETINGS(R.string.nav_meetings, R.drawable.ic_meetings),
    WALLET(R.string.nav_wallet, R.drawable.ic_wallet),
    SETTINGS(R.string.nav_settings, R.drawable.ic_settings),
}

/**
 * Which editor sheet is open, if any.
 *
 * This lives in the ViewModel rather than in a screen so that opening the task
 * editor from the Today screen, the Tasks screen or a meeting's action items is
 * one code path, and so the sheet survives the process being recreated.
 */
sealed interface Editor {
    /** [seed] is a blank task for "new", or the existing row for "edit". */
    data class TaskSheet(val seed: Task) : Editor

    data class LogSheet(val seed: LogEntry) : Editor

    data class MeetingSheet(val seed: Meeting) : Editor

    /** [seed] is a Pass pre-populated from a scan, or the existing row for "edit". */
    data class PassSheet(val seed: Pass) : Editor
}

/**
 * A one-shot message for the snackbar.
 *
 * The text is carried as a resource id plus format arguments rather than as a
 * finished string, because the ViewModel has no Context to resolve one with;
 * the screen calls `stringResource(textRes, *args)` when it shows the snackbar.
 *
 * [id] exists so that showing the same message twice in a row still counts as
 * two messages; without it the second would be swallowed as "no state change".
 */
data class UserMessage(
    val id: Long,
    @param:StringRes val textRes: Int,
    val args: List<Any> = emptyList(),
)

/**
 * Everything on screen, in one value.
 *
 * The raw lists and the derived views are both here: derivation happens once in
 * the ViewModel when the data or a filter changes, so recomposition never
 * re-sorts a list.
 */
data class DaybookUiState(
    val tab: DaybookTab = DaybookTab.TODAY,
    val today: LocalDate = LocalDate.EPOCH,
    val loaded: Boolean = false,

    // Raw data, straight from the repository.
    val tasks: List<Task> = emptyList(),
    val logEntries: List<LogEntry> = emptyList(),
    val meetings: List<Meeting> = emptyList(),
    val projects: List<String> = emptyList(),
    val passes: List<Pass> = emptyList(),

    // Wallet screen: true while the camera scanner is showing.
    val walletScanOpen: Boolean = false,

    // Tasks screen controls.
    val taskFilter: TaskFilter = TaskFilter.OPEN,
    val taskSort: TaskSort = TaskSort.PRIORITY,
    val taskProject: String? = null,
    val taskSearch: String = "",
    val groupByProject: Boolean = false,

    // Log screen controls.
    val logKind: LogKind? = null,
    val logSearch: String = "",

    // Meetings screen controls.
    val meetingFilter: MeetingFilter = MeetingFilter.UPCOMING,
    val meetingSearch: String = "",

    // Derived views, recomputed by the ViewModel.
    val board: TodayBoard = TodayBoard(day = LocalDate.EPOCH),
    val visibleTasks: List<Task> = emptyList(),
    val taskGroups: List<TaskGroup> = emptyList(),
    val logDays: List<LogDay> = emptyList(),
    val visibleMeetings: List<Meeting> = emptyList(),

    // Derived for whichever editor is open, and empty otherwise. These are here
    // rather than resolved by the editor itself so that no scan of the task or
    // meeting list happens during composition.
    val editorActionItems: List<Task> = emptyList(),
    val editorMeetingTitle: String? = null,

    val editor: Editor? = null,
    val message: UserMessage? = null,
    val busy: Boolean = false,

    // ── Voice agent ───────────────────────────────────────────────────────────
    /** True while SpeechRecognizer is actively listening. */
    val isListening: Boolean = false,
    val voiceRetried: Boolean = false,
    val voiceRetryMessage: String? = null,
    /**
     * Non-null when a voice recognition result is waiting for user confirmation.
     * Shown in [VoiceResultSheet]. The user either confirms (saves) or dismisses.
     */
    val voiceResult: VoiceAgentResult? = null,

    // ── WhatsApp reader ───────────────────────────────────────────────────────
    /** Most recent WhatsApp messages, shown in the Today board. Empty when the
     *  notification-reader is disabled or no messages have been received yet. */
    val recentWhatsAppMessages: List<WhatsAppMessage> = emptyList(),

    // ── Gmail integration ───────────────────────────────────────────────────
    /** Recent actionable or primary Gmail messages shown in the Today board. */
    val recentGmailMessages: List<com.sr2ma.daybook.domain.model.GmailMessage> = emptyList(),

    // ── Agent conversation ────────────────────────────────────────────────────
    /** True while the ConversationSheet is open. */
    val conversationOpen: Boolean = false,
    /**
     * In-session messages (RAM only — never persisted to disk).
     * Cleared when [conversationOpen] becomes false.
     */
    val conversationMessages: List<com.sr2ma.daybook.domain.ConversationMessage> = emptyList(),
    /** True while the agent is computing a response (shows thinking dots). */
    val agentThinking: Boolean = false,
    /**
     * The most recent [ParseResult] produced by [NaturalLanguageParser] during
     * a conversation turn. Cleared when the sheet closes.
     *
     * This is what action chips ("Add as task", "Add as meeting", etc.) act on
     * when tapped — Stage 3 wiring that replaces the stub echo from Stage 2.
     * Null = no NLP result yet in this session.
     */
    val lastConversationParseResult: com.sr2ma.daybook.domain.ParseResult? = null,
    val pendingMultiActions: List<com.sr2ma.daybook.domain.ParseResult> = emptyList(),

    /**
     * True when the Gemma 270M model file exists and passes the fast-path size
     * check ([ModelDownloader.isModelPresent]). Updated on [openConversation].
     *
     * When false, an UNKNOWN intent shows an inline "Download AI model in Settings"
     * message rather than attempting inference (which would return [InferResult.ModelNotReady]).
     */
    val llmModelReady: Boolean = false,

    /**
     * Non-null while the Gemma model is being downloaded (0.0–1.0).
     * Null when no download is in progress.
     */
    val modelDownloadProgress: Float? = null,

    /**
     * Set when a model download attempt fails — shown as a snackbar in Settings.
     * Cleared after display.
     */
    val modelDownloadError: String? = null,
) {
    val taskCount: Int get() = tasks.size
    val logCount: Int get() = logEntries.size
    val meetingCount: Int get() = meetings.size

    /** Items waiting to sync (PENDING_SYNC + SYNC_ERROR) across tasks and meetings. */
    val pendingSyncCount: Int get() =
        tasks.count { it.syncStatus == SyncStatus.PENDING_SYNC || it.syncStatus == SyncStatus.SYNC_ERROR } +
        meetings.count { it.syncStatus == SyncStatus.PENDING_SYNC || it.syncStatus == SyncStatus.SYNC_ERROR }
}
