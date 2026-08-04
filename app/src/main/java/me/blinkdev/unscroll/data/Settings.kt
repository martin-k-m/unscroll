package me.blinkdev.unscroll.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.blinkdev.unscroll.block.BlockRules

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "unscroll")

data class Settings(
    /** Ids from [BlockRules.surfaces] that are actively blocked. */
    val enabledSurfaces: Set<String> = defaultSurfaces,
    /** package name -> minutes allowed per day. Absent means unlimited. */
    val dailyLimits: Map<String, Int> = emptyMap(),
    /** Epoch millis until which all blocking is paused. */
    val snoozeUntil: Long = 0L,
) {
    fun isSnoozed(now: Long): Boolean = now < snoozeUntil

    companion object {
        val defaultSurfaces: Set<String> =
            BlockRules.surfaces.filterNot { it.wholeApp }.map { it.id }.toSet()
    }
}

class SettingsStore(context: Context) {

    private val store = context.applicationContext.dataStore

    val settings: Flow<Settings> = store.data.map { prefs ->
        Settings(
            enabledSurfaces = prefs[KEY_SURFACES] ?: Settings.defaultSurfaces,
            dailyLimits = (prefs[KEY_LIMITS] ?: emptySet()).mapNotNull(::decodeLimit).toMap(),
            snoozeUntil = prefs[KEY_SNOOZE] ?: 0L,
        )
    }

    suspend fun setSurfaceEnabled(surfaceId: String, enabled: Boolean) {
        store.edit { prefs ->
            val current = prefs[KEY_SURFACES] ?: Settings.defaultSurfaces
            prefs[KEY_SURFACES] = if (enabled) current + surfaceId else current - surfaceId
        }
    }

    /** [minutes] of zero or less clears the limit. */
    suspend fun setDailyLimit(packageName: String, minutes: Int) {
        store.edit { prefs ->
            val current = (prefs[KEY_LIMITS] ?: emptySet())
                .filterNot { it.startsWith("$packageName=") }
                .toSet()
            prefs[KEY_LIMITS] =
                if (minutes > 0) current + "$packageName=$minutes" else current
        }
    }

    suspend fun snoozeFor(millis: Long) {
        store.edit { prefs ->
            prefs[KEY_SNOOZE] = System.currentTimeMillis() + millis
        }
    }

    suspend fun clearSnooze() {
        store.edit { prefs -> prefs[KEY_SNOOZE] = 0L }
    }

    private companion object {
        val KEY_SURFACES = stringSetPreferencesKey("enabled_surfaces")
        val KEY_LIMITS = stringSetPreferencesKey("daily_limits")
        val KEY_SNOOZE = longPreferencesKey("snooze_until")

        fun decodeLimit(entry: String): Pair<String, Int>? {
            val pkg = entry.substringBefore('=', missingDelimiterValue = "")
            val minutes = entry.substringAfter('=', missingDelimiterValue = "").toIntOrNull()
            return if (pkg.isNotEmpty() && minutes != null) pkg to minutes else null
        }
    }
}
