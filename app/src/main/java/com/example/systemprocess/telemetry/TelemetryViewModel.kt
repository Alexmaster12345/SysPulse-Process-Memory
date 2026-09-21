package com.example.systemprocess.telemetry

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.systemprocess.data.AppDatabase
import com.example.systemprocess.data.SavedSession
import com.example.systemprocess.data.SavedSessionDao
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Owns the [TelemetryRepository] lifecycle and exposes its [state] to the UI.
 *
 * The repository is started when the ViewModel is created and polling runs in [viewModelScope],
 * so it survives configuration changes and is cleaned up automatically in [onCleared].
 * It also persists user-saved session snapshots via Room ([savedSessions], [saveCurrentSession]).
 */
class TelemetryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TelemetryRepository(application)
    private val sessionDao: SavedSessionDao = AppDatabase.get(application).savedSessionDao()
    private var pollJob: Job? = null

    val state: StateFlow<TelemetryUiState> = repository.state

    val savedSessions: StateFlow<List<SavedSession>> =
        sessionDao.observeAll().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    init {
        repository.start()
        pollJob = viewModelScope.launch {
            repository.poll(intervalMs = 1000L)
        }
    }

    /** Snapshots the current telemetry state and persists it to the local database. */
    fun saveCurrentSession() {
        val s = state.value
        val usedGb = (s.totalMemoryGb - s.availableMemoryGb).coerceAtLeast(0f)
        viewModelScope.launch {
            sessionDao.insert(
                SavedSession(
                    savedAt = System.currentTimeMillis(),
                    deviceModel = s.deviceModel,
                    androidVersion = s.androidVersion,
                    durationSeconds = s.sessionElapsedSeconds,
                    cpuLoadPercent = s.cpuLoadPercent,
                    usedMemoryGb = usedGb,
                    totalMemoryGb = s.totalMemoryGb,
                    deviceTempC = s.deviceTempC,
                    thermalState = s.thermalState,
                    batteryLevelPercent = s.batteryLevelPercent,
                    batteryCurrentMicroAmps = s.batteryCurrentMicroAmps,
                    batteryTempC = s.batteryTempC,
                    processCount = s.processes.size
                )
            )
        }
    }

    fun deleteSession(session: SavedSession) {
        viewModelScope.launch { sessionDao.delete(session) }
    }

    fun clearSessions() {
        viewModelScope.launch { sessionDao.clear() }
    }

    override fun onCleared() {
        super.onCleared()
        pollJob?.cancel()
        repository.stop()
    }
}
