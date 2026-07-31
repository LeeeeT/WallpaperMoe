package dev.leeeet.wallpapermoe.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "moe")

/**
 * How long a wallpaper is left alone before the next screen lock is allowed to
 * replace it. The screen going off is the app's only trigger, and this decides
 * which of those rotate.
 */
enum class RotationInterval(val minutes: Int) {
    EVERY_LOCK(0),
    M15(15),
    M30(30),
    H1(60),
    H3(180),
    H6(360),
    H12(720),
    H24(1440),
    ;

    companion object {
        fun fromMinutes(m: Int): RotationInterval = entries.firstOrNull { it.minutes == m } ?: EVERY_LOCK
    }
}

data class MoeSettings(
    val applyToLock: Boolean,
    val applyToHome: Boolean,
    val interval: RotationInterval,
    val installId: String,
) {
    val enabled: Boolean get() = applyToLock || applyToHome
}

class Prefs(private val context: Context) {

    val settings: Flow<MoeSettings> = context.dataStore.data.map { p ->
        MoeSettings(
            applyToLock = p[KEY_APPLY_LOCK] ?: true,
            applyToHome = p[KEY_APPLY_HOME] ?: true,
            interval = RotationInterval.fromMinutes(p[KEY_INTERVAL] ?: 0),
            installId = p[KEY_INSTALL_ID] ?: "",
        )
    }

    suspend fun current(): MoeSettings = settings.first()

    /**
     * A random id minted once per install, for providers that require a client
     * id. Random, and minted by the app itself.
     */
    suspend fun installId(): String {
        context.dataStore.data.first()[KEY_INSTALL_ID]?.let { return it }
        val fresh = UUID.randomUUID().toString().replace("-", "")
        context.dataStore.edit { it[KEY_INSTALL_ID] = fresh }
        return fresh
    }

    /** When the wallpaper last actually changed; the interval is measured from here. */
    val lastRotationAt: Flow<Long> = context.dataStore.data.map { it[KEY_LAST_ROTATION] ?: 0L }

    suspend fun markRotated(at: Long) = edit { it[KEY_LAST_ROTATION] = at }

    suspend fun setApplyToLock(v: Boolean) = edit { it[KEY_APPLY_LOCK] = v }
    suspend fun setApplyToHome(v: Boolean) = edit { it[KEY_APPLY_HOME] = v }
    suspend fun setInterval(v: RotationInterval) = edit { it[KEY_INTERVAL] = v.minutes }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    private companion object {
        val KEY_APPLY_LOCK = booleanPreferencesKey("apply_to_lock")
        val KEY_APPLY_HOME = booleanPreferencesKey("apply_to_home")
        val KEY_INTERVAL = intPreferencesKey("interval_minutes")
        val KEY_INSTALL_ID = stringPreferencesKey("install_id")
        val KEY_LAST_ROTATION = longPreferencesKey("last_rotation_at")
    }
}
