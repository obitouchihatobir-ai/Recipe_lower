package app.recetario.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.pantry.DEFAULT_PANTRY_BASICS
import app.recetario.core.ui.theme.ThemeSettings
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalTime

/**
 * Estado de las copias de seguridad (lo escriben las Tareas 15 y 16). Los instantes se guardan en milisegundos.
 * [lastLocal]: última copia semanal en el teléfono; [lastExternal]: última copia que salió del teléfono (Drive o
 * exportación manual; la usa el recordatorio de Inicio); [driveUri]: archivo de Drive de la copia automática;
 * [lastError]: por qué falló la última copia en Drive; [lastDrive]: última copia en ese archivo; [localError]: por
 * qué falló la última copia semanal en el teléfono; [noticeDismissed]: aviso de Inicio que se ocultó (día y tipo).
 * Todo es del teléfono («estado-local») salvo [lastExternal], que va con los ajustes.
 */
data class BackupStatus(
    val lastLocal: Instant? = null,
    val lastExternal: Instant? = null,
    val driveUri: String? = null,
    val lastError: String? = null,
    val lastDrive: Instant? = null,
    val localError: String? = null,
    val noticeDismissed: String? = null,
)

/**
 * Ajustes de la app en un DataStore de preferencias (archivo `ajustes`, que viaja con la copia de Android) y el
 * estado de este teléfono en otro (`estado-local`, que no viaja: el archivo de Drive y su permiso, las fechas y
 * errores de las copias, el aviso oculto de Inicio y si ya se pidió el permiso de notificaciones).
 */
class SettingsRepository(private val dataStore: DataStore<Preferences>, private val localState: DataStore<Preferences>) {
    private val defaults = ThemeSettings()

    /** Un archivo ilegible se lee como vacío (ajustes de serie) en vez de tumbar la app. */
    private val data: Flow<Preferences> = dataStore.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }
    private val local: Flow<Preferences> = localState.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }

    /** El estado de las copias está en dos archivos: sus cambios van de uno en uno. */
    private val statusLock = Mutex()

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

    /**
     * Ajustes que trae una copia de seguridad al «Reemplazar todo» (tema y taza), en una sola escritura.
     * Lo que la app no admite se queda como estaba: Nocturno como tema de día o una taza que no es de 240 o 250 ml.
     */
    suspend fun restoreFromBackup(theme: ThemeSettings, cupMl: Int) {
        dataStore.edit {
            it[AUTO] = theme.auto
            it[CHOICE] = theme.choice.name
            if (theme.dayTheme != ThemeChoice.NOCTURNO) it[DAY_THEME] = theme.dayTheme.name
            it[NIGHT_START] = theme.nightStart.toSecondOfDay() / 60
            it[NIGHT_END] = theme.nightEnd.toSecondOfDay() / 60
            if (cupMl in CUP_SIZES) it[CUP_ML] = cupMl
        }
    }

    /**
     * Básicos de la despensa (no cuentan en «¿Qué puedo hacer?» ni en «Con mi despensa»); sin guardar, los de serie.
     * Se guardan en `ajustes`, un nombre por línea.
     */
    val pantryBasics: Flow<List<String>> = data.map { p ->
        p[PANTRY_BASICS]?.split('\n')?.map { it.trim() }?.filter { it.isNotEmpty() }?.distinct() ?: DEFAULT_PANTRY_BASICS
    }.distinctUntilChanged()

    suspend fun setPantryBasics(list: List<String>) {
        val clean = list.map { it.replace('\n', ' ').trim() }.filter { it.isNotEmpty() }.distinct()
        dataStore.edit { it[PANTRY_BASICS] = clean.joinToString("\n") }
    }

    /** Si ya se pidió el permiso de notificaciones (se pide una sola vez, al iniciar el primer temporizador). */
    val notificationPermissionAsked: Flow<Boolean> =
        local.map { it[NOTIFICATIONS_ASKED] ?: false }.distinctUntilChanged()

    suspend fun setNotificationPermissionAsked() {
        localState.edit { it[NOTIFICATIONS_ASKED] = true }
    }

    val backupStatus: Flow<BackupStatus> =
        combine(data.map { it[LAST_EXTERNAL] }, local) { external, p -> p.toBackupStatus(external?.let(Instant::ofEpochMilli)) }
            .distinctUntilChanged()

    /** Cambia el estado de las copias: lo del teléfono en `estado-local` y la última copia de fuera en `ajustes`. */
    suspend fun updateBackupStatus(transform: (BackupStatus) -> BackupStatus) {
        statusLock.withLock {
            val external = data.first()[LAST_EXTERNAL]?.let(Instant::ofEpochMilli)
            var next: BackupStatus? = null
            localState.edit { p ->
                val n = transform(p.toBackupStatus(external)).also { next = it }
                p.put(LAST_LOCAL, n.lastLocal?.toEpochMilli())
                p.put(DRIVE_URI, n.driveUri)
                p.put(LAST_ERROR, n.lastError)
                p.put(LAST_DRIVE, n.lastDrive?.toEpochMilli())
                p.put(LOCAL_ERROR, n.localError)
                p.put(NOTICE_DISMISSED, n.noticeDismissed)
            }
            val lastExternal = next?.lastExternal
            if (lastExternal != external) dataStore.edit { it.put(LAST_EXTERNAL, lastExternal?.toEpochMilli()) }
        }
    }

    private fun Preferences.toBackupStatus(lastExternal: Instant?) = BackupStatus(
        lastLocal = this[LAST_LOCAL]?.let(Instant::ofEpochMilli),
        lastExternal = lastExternal,
        driveUri = this[DRIVE_URI],
        lastError = this[LAST_ERROR],
        lastDrive = this[LAST_DRIVE]?.let(Instant::ofEpochMilli),
        localError = this[LOCAL_ERROR],
        noticeDismissed = this[NOTICE_DISMISSED],
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
        val PANTRY_BASICS = stringPreferencesKey("pantry_basics")
        val LAST_LOCAL = longPreferencesKey("backup_last_local")
        val LAST_EXTERNAL = longPreferencesKey("backup_last_external")
        val DRIVE_URI = stringPreferencesKey("backup_drive_uri")
        val LAST_ERROR = stringPreferencesKey("backup_last_error")
        val LAST_DRIVE = longPreferencesKey("backup_last_drive")
        val LOCAL_ERROR = stringPreferencesKey("backup_local_error")
        val NOTICE_DISMISSED = stringPreferencesKey("backup_notice_dismissed")
        val NOTIFICATIONS_ASKED = booleanPreferencesKey("notifications_permission_asked")
    }
}

/** DataStore de los ajustes (o del estado del teléfono); si el archivo está corrupto se sustituye por uno vacío. */
fun createSettingsDataStore(scope: CoroutineScope, produceFile: () -> File): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = scope,
        produceFile = produceFile,
    )
