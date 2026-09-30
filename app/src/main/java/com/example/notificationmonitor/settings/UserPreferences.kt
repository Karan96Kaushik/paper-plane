package com.example.notificationmonitor.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_preferences"
)

class UserPreferences(private val context: Context) {

    val retentionPeriod: Flow<RetentionPeriod> = context.dataStore.data.map { prefs ->
        RetentionPeriod.fromStorage(prefs[KEY_RETENTION])
    }

    suspend fun setRetentionPeriod(period: RetentionPeriod) {
        context.dataStore.edit { prefs ->
            prefs[KEY_RETENTION] = period.name
        }
    }

    companion object {
        private val KEY_RETENTION = stringPreferencesKey("retention_period")
    }
}
