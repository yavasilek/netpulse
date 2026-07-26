package ru.yavasilek.netpulse.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore by preferencesDataStore(name = "netpulse_settings")

class SettingsRepository(
    private val context: Context,
) {
    private object Keys {
        val monitoringEnabled = booleanPreferencesKey("monitoring_enabled")
        val startOnBoot = booleanPreferencesKey("start_on_boot")
        val speedUnit = stringPreferencesKey("speed_unit")
        val statusIconMode = stringPreferencesKey("status_icon_mode")
        val warnWhenVpnDisconnects = booleanPreferencesKey("warn_when_vpn_disconnects")
        val warnWhenIpChanges = booleanPreferencesKey("warn_when_ip_changes")
        val automaticUpdateChecks = booleanPreferencesKey("automatic_update_checks")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
    }

    val settings: Flow<AppSettings> = context.dataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                throw error
            }
        }
        .map { preferences ->
            AppSettings(
                monitoringEnabled = preferences[Keys.monitoringEnabled] ?: true,
                startOnBoot = preferences[Keys.startOnBoot] ?: true,
                speedUnit = preferences[Keys.speedUnit]
                    ?.let { value -> enumValueOrDefault(value, SpeedUnit.BITS_PER_SECOND) }
                    ?: SpeedUnit.BITS_PER_SECOND,
                statusIconMode = preferences[Keys.statusIconMode]
                    ?.let { value -> enumValueOrDefault(value, StatusIconMode.DOWNLOAD) }
                    ?: StatusIconMode.DOWNLOAD,
                warnWhenVpnDisconnects = preferences[Keys.warnWhenVpnDisconnects] ?: true,
                warnWhenIpChanges = preferences[Keys.warnWhenIpChanges] ?: false,
                automaticUpdateChecks = preferences[Keys.automaticUpdateChecks] ?: true,
                dynamicColor = preferences[Keys.dynamicColor] ?: true,
            )
        }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setMonitoringEnabled(value: Boolean) = edit(Keys.monitoringEnabled, value)

    suspend fun setStartOnBoot(value: Boolean) = edit(Keys.startOnBoot, value)

    suspend fun setSpeedUnit(value: SpeedUnit) = edit(Keys.speedUnit, value.name)

    suspend fun setStatusIconMode(value: StatusIconMode) = edit(Keys.statusIconMode, value.name)

    suspend fun setWarnWhenVpnDisconnects(value: Boolean) =
        edit(Keys.warnWhenVpnDisconnects, value)

    suspend fun setWarnWhenIpChanges(value: Boolean) =
        edit(Keys.warnWhenIpChanges, value)

    suspend fun setAutomaticUpdateChecks(value: Boolean) =
        edit(Keys.automaticUpdateChecks, value)

    suspend fun setDynamicColor(value: Boolean) = edit(Keys.dynamicColor, value)

    private suspend fun <T> edit(key: androidx.datastore.preferences.core.Preferences.Key<T>, value: T) {
        context.dataStore.edit { preferences ->
            preferences[key] = value
        }
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, default: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: default
}
