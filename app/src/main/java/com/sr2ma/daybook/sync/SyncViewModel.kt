package com.sr2ma.daybook.sync

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sr2ma.daybook.domain.model.AutonomyLevel
import com.sr2ma.daybook.domain.model.ToolCategory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the Sync & Backup section in Settings.
 *
 * Keeps all Google sync state here rather than in DaybookViewModel so that
 * the main ViewModel stays decoupled from network concerns.
 */
class SyncViewModel(
    private val syncManager: SyncManager,
) : ViewModel() {

    private val _state = MutableStateFlow(loadState())
    val state: StateFlow<SyncUiState> = _state.asStateFlow()

    private fun loadState(): SyncUiState {
        val prefs = syncManager.syncPrefs
        return SyncUiState(
            isSignedIn = prefs.isSignedIn,
            accountEmail = prefs.accountEmail,
            calendarSyncMeetings = prefs.calendarSyncMeetings,
            calendarSyncTasks = prefs.calendarSyncTasks,
            driveAutoBackup = prefs.driveAutoBackup,
            lastCalendarSyncAt = prefs.lastCalendarSyncAt,
            lastDriveBackupAt = prefs.lastDriveBackupAt,
            autonomyLevels = ToolCategory.entries.associateWith { prefs.getAutonomy(it) },
        )
    }

    private fun refreshState() {
        _state.value = loadState()
    }

    // ── Autonomy level control (§8) ───────────────────────────────────────────

    fun setAutonomy(category: ToolCategory, level: AutonomyLevel) {
        syncManager.syncPrefs.setAutonomy(category, level)
        _state.value = _state.value.copy(
            autonomyLevels = _state.value.autonomyLevels + (category to level)
        )
    }

    // ── Auth ─────────────────────────────────────────────────────────────────

    fun signIn() {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            try {
                val email = syncManager.signIn()
                if (email != null) {
                    refreshState()
                } else {
                    _state.value = _state.value.copy(
                        busy = false,
                        errorMessage = "Sign-in cancelled or failed",
                    )
                    return@launch
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    busy = false,
                    errorMessage = e.message ?: "Sign-in failed",
                )
                return@launch
            }
            _state.value = _state.value.copy(busy = false)
        }
    }

    fun signOut() {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            try {
                syncManager.signOut()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Sign-out always clears local state even on error.
            }
            refreshState()
            _state.value = _state.value.copy(busy = false)
        }
    }

    // ── Toggles ──────────────────────────────────────────────────────────────

    fun toggleCalendarSyncMeetings() {
        viewModelScope.launch {
            val newValue = !_state.value.calendarSyncMeetings
            syncManager.setCalendarSyncMeetings(newValue)
            _state.value = _state.value.copy(calendarSyncMeetings = newValue)
        }
    }

    fun toggleCalendarSyncTasks() {
        val newValue = !_state.value.calendarSyncTasks
        syncManager.setCalendarSyncTasks(newValue)
        _state.value = _state.value.copy(calendarSyncTasks = newValue)
    }

    fun toggleDriveAutoBackup() {
        val newValue = !_state.value.driveAutoBackup
        syncManager.setDriveAutoBackup(newValue)
        _state.value = _state.value.copy(driveAutoBackup = newValue)
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    fun syncNow() {
        syncManager.syncNow()
        _state.value = _state.value.copy(syncEnqueued = true)
    }

    fun backupNow() {
        syncManager.backupNow()
        _state.value = _state.value.copy(backupEnqueued = true)
    }

    fun openRestorePicker() {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, driveBackups = emptyList())
            val backups = try {
                syncManager.listDriveBackups()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
            _state.value = _state.value.copy(
                busy = false,
                driveBackups = backups,
                showRestorePicker = backups.isNotEmpty(),
                errorMessage = if (backups.isEmpty()) "No backups found on Drive" else null,
            )
        }
    }

    fun dismissRestorePicker() {
        _state.value = _state.value.copy(showRestorePicker = false, driveBackups = emptyList())
    }

    fun consumeError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    fun consumeSyncEnqueued() {
        _state.value = _state.value.copy(syncEnqueued = false)
    }

    fun consumeBackupEnqueued() {
        _state.value = _state.value.copy(backupEnqueued = false)
    }

    companion object {
        fun factory(syncManager: SyncManager): ViewModelProvider.Factory = viewModelFactory {
            initializer { SyncViewModel(syncManager) }
        }
    }
}

/** UI state for the sync settings section. */
data class SyncUiState(
    val isSignedIn: Boolean = false,
    val accountEmail: String? = null,
    val calendarSyncMeetings: Boolean = false,
    val calendarSyncTasks: Boolean = false,
    val driveAutoBackup: Boolean = false,
    val lastCalendarSyncAt: Long = 0L,
    val lastDriveBackupAt: Long = 0L,
    val busy: Boolean = false,
    val syncEnqueued: Boolean = false,
    val backupEnqueued: Boolean = false,
    val showRestorePicker: Boolean = false,
    val driveBackups: List<DriveBackupWorker.DriveFile> = emptyList(),
    val errorMessage: String? = null,
    /** Per-tool autonomy levels. Default ASK_EVERY_TIME for all categories. */
    val autonomyLevels: Map<ToolCategory, AutonomyLevel> = emptyMap(),
)
