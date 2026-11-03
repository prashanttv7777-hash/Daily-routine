package com.example.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "daily_life_prefs")

enum class AppThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark")
}

data class UserPreferences(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val defaultReminderMinutes: Long = 15L,
    val dailyGoalTasks: Int = 5,
    val notificationsEnabled: Boolean = true
)

class ThemePreferencesRepository(private val context: Context) {
    private val appContext = context.applicationContext

    val preferencesFlow: Flow<UserPreferences> = appContext.dataStore.data.map { prefs ->
        val modeStr = prefs[KEY_THEME_MODE] ?: AppThemeMode.SYSTEM.name
        val mode = AppThemeMode.entries.firstOrNull { it.name == modeStr } ?: AppThemeMode.SYSTEM
        val reminderMins = prefs[KEY_DEFAULT_REMINDER_MINS] ?: 15L
        val notificationsOn = prefs[KEY_NOTIFICATIONS_ENABLED] ?: true
        UserPreferences(
            themeMode = mode,
            defaultReminderMinutes = reminderMins,
            notificationsEnabled = notificationsOn
        )
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        appContext.dataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = mode.name
        }
    }

    suspend fun setDefaultReminderMinutes(minutes: Long) {
        appContext.dataStore.edit { prefs ->
            prefs[KEY_DEFAULT_REMINDER_MINS] = minutes.coerceIn(0L, 1440L)
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        appContext.dataStore.edit { prefs ->
            prefs[KEY_NOTIFICATIONS_ENABLED] = enabled
        }
    }

    private companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_DEFAULT_REMINDER_MINS = longPreferencesKey("default_reminder_mins")
        val KEY_NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
    }
}
