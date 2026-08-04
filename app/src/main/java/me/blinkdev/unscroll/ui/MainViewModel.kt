package me.blinkdev.unscroll.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.blinkdev.unscroll.data.Settings
import me.blinkdev.unscroll.data.SettingsStore
import me.blinkdev.unscroll.usage.UsageTracker

data class UiState(
    val settings: Settings = Settings(),
    val blockerEnabled: Boolean = false,
    val usageAccessGranted: Boolean = false,
    /** package name -> minutes used today. */
    val usedToday: Map<String, Int> = emptyMap(),
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val store = SettingsStore(app)
    private val usage = UsageTracker(app)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            store.settings.collect { settings ->
                _state.update { it.copy(settings = settings) }
            }
        }
    }

    fun refresh() {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val blockerEnabled = Permissions.isBlockerEnabled(app)
            val granted = usage.hasPermission()
            val used = if (granted) {
                withContext(Dispatchers.IO) {
                    usage.foregroundMillisToday().mapValues { (_, ms) -> (ms / 60_000L).toInt() }
                }
            } else {
                emptyMap()
            }
            _state.update {
                it.copy(
                    blockerEnabled = blockerEnabled,
                    usageAccessGranted = granted,
                    usedToday = used,
                )
            }
        }
    }

    fun setSurfaceEnabled(surfaceId: String, enabled: Boolean) {
        viewModelScope.launch { store.setSurfaceEnabled(surfaceId, enabled) }
    }

    fun setDailyLimit(packageName: String, minutes: Int) {
        viewModelScope.launch { store.setDailyLimit(packageName, minutes) }
    }

    fun snooze(minutes: Int) {
        viewModelScope.launch { store.snoozeFor(minutes * 60_000L) }
    }

    fun clearSnooze() {
        viewModelScope.launch { store.clearSnooze() }
    }
}
