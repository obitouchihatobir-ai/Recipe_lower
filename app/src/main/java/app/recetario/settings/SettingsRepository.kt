package app.recetario.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.core.ui.theme.ThemeSettings
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import java.io.File
import java.io.IOException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalTime

/** Estado de las copias de seguridad (lo escriben las Tareas 15 y 16). Los instantes se guardan en milisegundos. */
data class BackupStatus(
    val lastLocal: Instant? = null,
    val lastExternal: Instant? = null,
    val driveUri: String? = null,
    val lastError: String? = null,
)

/** Ajustes de la app en un DataStore de preferencias (archivo `ajustes`). */
class SettingsRepository(private val dataStore: DataStore<Preferences>) {
    private val defaults = ThemeSettings()

    /** Un archivo ilegible se lee como vacío (ajustes de serie) en vez de tumbar la app. */
    private val data: Flow<Preferences> = dataStore.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }

    val themeSettings: Flow<ThemeSettings> = data.map { p ->
        ThemeSettings(
            auto = p[AUTO] ?: defaults.auto,
            choice = p[CHOICE].toTheme() ?: defaults.choice,
            dayTheme = p[DAY_THEME].toTheme()?.takeIf { it != ThemeChoice.NOCTURNO } ?: defaults.dayTheme,
            nightStart = p[NIGHT_START]?.toTime() ?: defaults.nightStart,
            nightEnd = p[NIGHT_END]?.toTime() ?: defaults.nightEnd,
        )
    }.distinctUntilChanged()

    suspend fun setTheme(choice: ThemeChoice) {
        dataStore.edit { it[CHOICE] = choice.name }
    }

    suspend fun setAuto(auto: Boolean) {
        dataStore.edit { it[AUTO] = auto }
    }

    /** El tema claro de día de Automático: Nocturno solo es el tema de noche o el manual. */
    suspend fun setDayTheme(choice: ThemeChoice) {
        require(choice != ThemeChoice.NOCTURNO) { "El tema de día es Cálido o Fresco" }
        dataStore.edit { it[DAY_THEME] = choice.name }
    }

    suspend fun setNightWindow(start: LocalTime, end: LocalTime) {
        dataStore.edit {
            it[NIGHT_START] = start.toSecondOfDay() / 60
            it[NIGHT_END] = end.toSecondOfDay() / 60
        }
    }

    /** Tamaño de la taza en mililitros: 240 por defecto, o 250. */
    val cupMl: Flow<Int> = data.map { p -> p[CUP_ML]?.takeIf { it in CUP_SIZES } ?: DEFAULT_CUP_ML }.distinctUntilChanged()

    suspend fun setCupMl(ml: Int) {
        require(ml in CUP_SIZES) { "La taza es de 240 o 250 ml" }
        dataStore.edit { it[CUP_ML] = ml }
    }

    val backupStatus: Flow<BackupStatus> = data.map { it.toBackupStatus() }.distinctUntilChanged()

    suspend fun updateBackupStatus(transform: (BackupStatus) -> BackupStatus) {
        dataStore.edit { p ->
            val next = transform(p.toBackupStatus())
            p.put(LAST_LOCAL, next.lastLocal?.toEpochMilli())
            p.put(LAST_EXTERNAL, next.lastExternal?.toEpochMilli())
            p.put(DRIVE_URI, next.driveUri)
            p.put(LAST_ERROR, next.lastError)
        }
    }

    private fun Preferences.toBackupStatus() = BackupStatus(
        lastLocal = this[LAST_LOCAL]?.let(Instant::ofEpochMilli),
        lastExternal = this[LAST_EXTERNAL]?.let(Instant::ofEpochMilli),
        driveUri = this[DRIVE_URI],
        lastError = this[LAST_ERROR],
    )

    private fun <T> androidx.datastore.preferences.core.MutablePreferences.put(key: Preferences.Key<T>, value: T?) {
        if (value == null) remove(key) else this[key] = value
    }

    private fun String?.toTheme(): ThemeChoice? = ThemeChoice.entries.firstOrNull { it.name == this }

    private fun Int.toTime(): LocalTime = LocalTime.ofSecondOfDay((coerceIn(0, 24 * 60 - 1) * 60).toLong())

    private companion object {
        const val DEFAULT_CUP_ML = 240
        val CUP_SIZES = setOf(240, 250)
        val AUTO = booleanPreferencesKey("theme_auto")
        val CHOICE = stringPreferencesKey("theme_choice")
        val DAY_THEME = stringPreferencesKey("theme_day")
        val NIGHT_START = intPreferencesKey("night_start_minute")
        val NIGHT_END = intPreferencesKey("night_end_minute")
        val CUP_ML = intPreferencesKey("cup_ml")
        val LAST_LOCAL = longPreferencesKey("backup_last_local")
        val LAST_EXTERNAL = longPreferencesKey("backup_last_external")
        val DRIVE_URI = stringPreferencesKey("backup_drive_uri")
        val LAST_ERROR = stringPreferencesKey("backup_last_error")
    }
}

/** DataStore de los ajustes; si el archivo está corrupto se sustituye por uno vacío. */
fun createSettingsDataStore(scope: CoroutineScope, produceFile: () -> File): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = scope,
        produceFile = produceFile,
    )
