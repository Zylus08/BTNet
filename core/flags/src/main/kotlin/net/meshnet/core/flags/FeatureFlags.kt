package net.meshnet.core.flags

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import net.meshnet.core.events.EventBus
import net.meshnet.core.events.MeshEvent
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "feature_flags")

/**
 * Central configuration for experimental features and debugging toggles.
 * Backed by DataStore for persistence.
 * Emits [MeshEvent.FeatureFlagChanged] on the EventBus when a flag is updated.
 */
@Singleton
class FeatureFlags @Inject constructor(
    @ApplicationContext private val context: Context,
    private val eventBus: EventBus,
) {
    /**
     * Set of all known feature flags and their default values.
     */
    enum class Flag(val key: Preferences.Key<Boolean>, val default: Boolean) {
        ENABLE_PROPHET(booleanPreferencesKey("enable_prophet"), true),
        ENABLE_COMPRESSION(booleanPreferencesKey("enable_compression"), true),
        ENABLE_WIFI_DIRECT(booleanPreferencesKey("enable_wifi_direct"), false),
        ENABLE_BLOOM_FILTER(booleanPreferencesKey("enable_bloom_filter"), true),
        ENABLE_VOICE_MESSAGES(booleanPreferencesKey("enable_voice_messages"), false),
        ENABLE_DTN_BACKGROUND(booleanPreferencesKey("enable_dtn_background"), true)
    }

    private val dataStore = context.dataStore

    /**
     * Observes the value of a specific flag.
     */
    fun observe(flag: Flag): Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading feature flags")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[flag.key] ?: flag.default
        }

    /**
     * Updates the value of a flag and emits a [MeshEvent.FeatureFlagChanged] event.
     */
    suspend fun set(flag: Flag, enabled: Boolean) {
        dataStore.edit { preferences ->
            val current = preferences[flag.key] ?: flag.default
            if (current != enabled) {
                preferences[flag.key] = enabled
                eventBus.emitSuspend(MeshEvent.FeatureFlagChanged(flag.name, enabled))
                Timber.i("Feature flag ${flag.name} changed to $enabled")
            }
        }
    }
}
