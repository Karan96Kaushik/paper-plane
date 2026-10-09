package com.example.notificationmonitor.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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

    val autoRepublishEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_AUTO_REPUBLISH] ?: true
    }

    suspend fun setAutoRepublishEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTO_REPUBLISH] = enabled
        }
    }

    suspend fun isAutoRepublishEnabled(): Boolean = autoRepublishEnabled.first()

    val supabaseConfig: Flow<SupabaseConfig> = context.dataStore.data.map { prefs ->
        SupabaseConfig(
            enabled = prefs[KEY_SUPABASE_ENABLED] ?: false,
            projectUrl = prefs[KEY_SUPABASE_URL].orEmpty(),
            apiKey = prefs[KEY_SUPABASE_API_KEY].orEmpty(),
            table = prefs[KEY_SUPABASE_TABLE]?.takeIf { it.isNotBlank() } ?: SupabaseConfig.DEFAULT_TABLE,
            email = prefs[KEY_SUPABASE_EMAIL].orEmpty(),
            accountEmail = prefs[KEY_SUPABASE_ACCOUNT_EMAIL].orEmpty(),
            userId = prefs[KEY_SUPABASE_USER_ID].orEmpty(),
            accessToken = prefs[KEY_SUPABASE_ACCESS_TOKEN].orEmpty(),
            refreshToken = prefs[KEY_SUPABASE_REFRESH_TOKEN].orEmpty(),
            accessTokenExpiresAt = prefs[KEY_SUPABASE_ACCESS_EXPIRES_AT] ?: 0L
        )
    }

    suspend fun setSupabaseConfig(config: SupabaseConfig) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SUPABASE_ENABLED] = config.enabled
            prefs[KEY_SUPABASE_URL] = config.projectUrl.trim()
            prefs[KEY_SUPABASE_API_KEY] = config.apiKey.trim()
            prefs[KEY_SUPABASE_TABLE] = config.table.trim().ifBlank { SupabaseConfig.DEFAULT_TABLE }
            prefs[KEY_SUPABASE_EMAIL] = config.email.trim()
            prefs[KEY_SUPABASE_ACCOUNT_EMAIL] = config.accountEmail.trim()
            prefs[KEY_SUPABASE_USER_ID] = config.userId.trim()
            prefs[KEY_SUPABASE_ACCESS_TOKEN] = config.accessToken.trim()
            prefs[KEY_SUPABASE_REFRESH_TOKEN] = config.refreshToken.trim()
            prefs[KEY_SUPABASE_ACCESS_EXPIRES_AT] = config.accessTokenExpiresAt
        }
    }

    suspend fun currentSupabaseConfig(): SupabaseConfig = supabaseConfig.first()

    companion object {
        private val KEY_RETENTION = stringPreferencesKey("retention_period")
        private val KEY_AUTO_REPUBLISH = booleanPreferencesKey("auto_republish_enabled")
        private val KEY_SUPABASE_ENABLED = booleanPreferencesKey("supabase_enabled")
        private val KEY_SUPABASE_URL = stringPreferencesKey("supabase_url")
        private val KEY_SUPABASE_API_KEY = stringPreferencesKey("supabase_api_key")
        private val KEY_SUPABASE_TABLE = stringPreferencesKey("supabase_table")
        private val KEY_SUPABASE_EMAIL = stringPreferencesKey("supabase_email")
        private val KEY_SUPABASE_ACCOUNT_EMAIL = stringPreferencesKey("supabase_account_email")
        private val KEY_SUPABASE_USER_ID = stringPreferencesKey("supabase_user_id")
        private val KEY_SUPABASE_ACCESS_TOKEN = stringPreferencesKey("supabase_access_token")
        private val KEY_SUPABASE_REFRESH_TOKEN = stringPreferencesKey("supabase_refresh_token")
        private val KEY_SUPABASE_ACCESS_EXPIRES_AT = longPreferencesKey("supabase_access_expires_at")
    }
}
