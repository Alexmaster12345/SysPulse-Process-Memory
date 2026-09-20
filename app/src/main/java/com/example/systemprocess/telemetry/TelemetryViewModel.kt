package com.example.systemprocess.telemetry

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Owns the [TelemetryRepository] lifecycle and exposes its [state] to the UI.
 *
 * The repository is started when the ViewModel is created and polling runs in [viewModelScope],
 * so it survives configuration changes and is cleaned up automatically in [onCleared].
 */
class TelemetryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TelemetryRepository(application)
    private var pollJob: Job? = null

    val state: StateFlow<TelemetryUiState> = repository.state

    init {
        repository.start()
        pollJob = viewModelScope.launch {
            repository.poll(intervalMs = 1000L)
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollJob?.cancel()
        repository.stop()
    }
}
