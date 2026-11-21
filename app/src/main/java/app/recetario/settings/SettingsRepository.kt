package app.recetario.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.pantry.DEFAULT_PANTRY_BASICS
import app.recetario.core.ui.theme.ThemeSettings
import app.recetario.today.MealTimes
import app.recetario.today.ThermalThresholds
import app.recetario.today.TimeRange
import app.recetario.today.problem
import app.recetario.weather.Coordinates
import app.recetario.weather.LocationMode
import app.recetario.weather.Place
import app.recetario.weather.WeatherCache
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
     * Ajustes que trae una copia de seguridad al «Reemplazar todo» (tema, taza y básicos de la despensa), en una sola
     * escritura. Lo que la app no admite se queda como estaba: Nocturno como tema de día o una taza que no es de 240 o
     * 250 ml. [pantryBasics] null (copia v1): vuelven los básicos de serie.
     */
    suspend fun restoreFromBackup(
        theme: ThemeSettings,
        cupMl: Int,
        pantryBasics: List<String>? = null,
        mealTimes: MealTimes? = null,
        thermalThresholds: ThermalThresholds? = null,
        themeBySun: Boolean? = null,
    ) {
        dataStore.edit {
            // Horarios, umbrales y tema según el sol (0.3.0): null (copia anterior) son los de serie. La ubicación no
            // viene en la copia: se queda la de este teléfono.
            it.put(MEAL_TIMES, mealTimes?.takeIf { t -> t.problem() == null }?.encode())
            val thresholds = thermalThresholds?.takeIf { t -> t.isValid }
            it.put(THERMAL_COLD, thresholds?.coldBelow)
            it.put(THERMAL_HOT, thresholds?.hotAbove)
            it.put(THEME_BY_SUN, themeBySun)
            it[AUTO] = theme.auto
            it[CHOICE] = theme.choice.name
            if (theme.dayTheme != ThemeChoice.NOCTURNO) it[DAY_THEME] = theme.dayTheme.name
            it[NIGHT_START] = theme.nightStart.toSecondOfDay() / 60
            it[NIGHT_END] = theme.nightEnd.toSecondOfDay() / 60
            if (cupMl in CUP_SIZES) it[CUP_ML] = cupMl
            if (pantryBasics == null) {
                it.remove(PANTRY_BASICS)
            } else {
                it[PANTRY_BASICS] = cleanBasics(pantryBasics).joinToString("\n")
            }
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
        val clean = cleanBasics(list)
        dataStore.edit { it[PANTRY_BASICS] = clean.joinToString("\n") }
    }

    private fun cleanBasics(list: List<String>) = list.map { it.replace('\n', ' ').trim() }.filter { it.isNotEmpty() }.distinct()

    /** Horarios de comidas (Hoy los usa para saber qué momento toca); sin guardar, o si no valen, los de serie. */
    val mealTimes: Flow<MealTimes> = data.map { p -> p[MEAL_TIMES]?.let(::decodeMealTimes) ?: MealTimes() }.distinctUntilChanged()

    suspend fun setMealTimes(times: MealTimes) {
        require(times.problem() == null) { "Horarios no válidos" }
        dataStore.edit { it[MEAL_TIMES] = times.encode() }
    }

    /** Umbrales de frío y calor en °C (15 y 24 por defecto). */
    val thermalThresholds: Flow<ThermalThresholds> = data.map { p ->
        val cold = p[THERMAL_COLD]
        val hot = p[THERMAL_HOT]
        if (cold == null || hot == null) ThermalThresholds() else ThermalThresholds(cold, hot).takeIf { it.isValid } ?: ThermalThresholds()
    }.distinctUntilChanged()

    suspend fun setThermalThresholds(thresholds: ThermalThresholds) {
        require(thresholds.isValid) { "Umbrales no válidos" }
        dataStore.edit {
            it[THERMAL_COLD] = thresholds.coldBelow
            it[THERMAL_HOT] = thresholds.hotAbove
        }
    }

    /** Automático con la noche de la puesta a la salida del sol (si hay previsión reciente). No por defecto. */
    val themeBySun: Flow<Boolean> = data.map { it[THEME_BY_SUN] ?: false }.distinctUntilChanged()

    suspend fun setThemeBySun(enabled: Boolean) {
        dataStore.edit { it[THEME_BY_SUN] = enabled }
    }

    /** De dónde sale el tiempo (en `ajustes`). */
    val locationMode: Flow<LocationMode> =
        data.map { p -> LocationMode.entries.firstOrNull { it.name == p[LOCATION_MODE] } ?: LocationMode.NONE }.distinctUntilChanged()

    suspend fun setLocationMode(mode: LocationMode) {
        dataStore.edit { it[LOCATION_MODE] = mode.name }
    }

    /** La ciudad elegida a mano (aunque ahora se use la aproximada). */
    val city: Flow<Place?> = data.map { p ->
        val name = p[CITY_NAME] ?: return@map null
        Place(name, p[CITY_ADMIN], p[CITY_COUNTRY], p[CITY_LAT] ?: return@map null, p[CITY_LON] ?: return@map null)
    }.distinctUntilChanged()

    /** Elige [place] y pasa a usarla. */
    suspend fun setCity(place: Place) {
        dataStore.edit {
            it[CITY_NAME] = place.name
            it.put(CITY_ADMIN, place.admin)
            it.put(CITY_COUNTRY, place.country)
            it[CITY_LAT] = place.latitude
            it[CITY_LON] = place.longitude
            it[LOCATION_MODE] = LocationMode.CITY.name
        }
    }

    /** «Quitar ubicación»: sin ciudad, sin ubicación aproximada y sin el tiempo guardado. */
    suspend fun clearLocation() {
        dataStore.edit {
            it[LOCATION_MODE] = LocationMode.NONE.name
            for (key in listOf(CITY_NAME, CITY_ADMIN, CITY_COUNTRY)) it.remove(key)
            it.remove(CITY_LAT)
            it.remove(CITY_LON)
        }
        localState.edit {
            for (key in listOf(APPROX_LAT, APPROX_LON, WEATHER_LAT, WEATHER_LON)) it.remove(key)
            it.remove(WEATHER_JSON)
            it.remove(WEATHER_FETCHED)
            it.remove(WEATHER_FAILED)
        }
    }

    /** La última ubicación aproximada del teléfono (solo en `estado-local`). */
    val approximateLocation: Flow<Coordinates?> = local.map { p ->
        Coordinates(p[APPROX_LAT] ?: return@map null, p[APPROX_LON] ?: return@map null)
    }.distinctUntilChanged()

    suspend fun setApproximateLocation(coordinates: Coordinates) {
        localState.edit {
            it[APPROX_LAT] = coordinates.latitude
            it[APPROX_LON] = coordinates.longitude
        }
    }

    /** La última previsión de Open-Meteo, con su hora y su sitio (solo en `estado-local`). */
    val weatherCache: Flow<WeatherCache?> = local.map { p ->
        WeatherCache(
            json = p[WEATHER_JSON] ?: return@map null,
            fetchedAt = Instant.ofEpochMilli(p[WEATHER_FETCHED] ?: return@map null),
            latitude = p[WEATHER_LAT] ?: return@map null,
            longitude = p[WEATHER_LON] ?: return@map null,
        )
    }.distinctUntilChanged()

    suspend fun setWeatherCache(cache: WeatherCache) {
        localState.edit {
            it[WEATHER_JSON] = cache.json
            it[WEATHER_FETCHED] = cache.fetchedAt.toEpochMilli()
            it[WEATHER_LAT] = cache.latitude
            it[WEATHER_LON] = cache.longitude
        }
    }

    /** Cuándo falló la última vez que se pidió el tiempo (null si la última salió bien). */
    val weatherFailedAt: Flow<Instant?> = local.map { p -> p[WEATHER_FAILED]?.let(Instant::ofEpochMilli) }.distinctUntilChanged()

    suspend fun setWeatherFailedAt(at: Instant?) {
        localState.edit { it.put(WEATHER_FAILED, at?.toEpochMilli()) }
    }

    /** Se denegó el permiso de ubicación: no se vuelve a pedir (se ofrece elegir una ciudad). */
    val locationPermissionDenied: Flow<Boolean> = local.map { it[LOCATION_DENIED] ?: false }.distinctUntilChanged()

    suspend fun setLocationPermissionDenied() {
        localState.edit { it[LOCATION_DENIED] = true }
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

    private fun MealTimes.encode(): String =
        listOf(breakfast, lunch, snack, dinner).joinToString(",") { "${it.start.toSecondOfDay() / 60}-${it.end.toSecondOfDay() / 60}" }

    /** «420-660,780-960,…» (minutos del día); algo ilegible o que no vale, null. */
    private fun decodeMealTimes(text: String): MealTimes? = runCatching {
        val ranges = text.split(',').map { part ->
            val (start, end) = part.split('-').map { it.trim().toInt() }
            require(start in 0 until 24 * 60 && end in 0 until 24 * 60)
            TimeRange(start.toTime(), end.toTime())
        }
        require(ranges.size == 4)
        MealTimes(ranges[0], ranges[1], ranges[2], ranges[3]).takeIf { it.problem() == null }
    }.getOrNull()

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
        val MEAL_TIMES = stringPreferencesKey("meal_times")
        val THERMAL_COLD = intPreferencesKey("thermal_cold_below")
        val THERMAL_HOT = intPreferencesKey("thermal_hot_above")
        val THEME_BY_SUN = booleanPreferencesKey("theme_by_sun")
        val LOCATION_MODE = stringPreferencesKey("location_mode")
        val CITY_NAME = stringPreferencesKey("city_name")
        val CITY_ADMIN = stringPreferencesKey("city_admin")
        val CITY_COUNTRY = stringPreferencesKey("city_country")
        val CITY_LAT = doublePreferencesKey("city_latitude")
        val CITY_LON = doublePreferencesKey("city_longitude")
        val APPROX_LAT = doublePreferencesKey("approx_latitude")
        val APPROX_LON = doublePreferencesKey("approx_longitude")
        val WEATHER_JSON = stringPreferencesKey("weather_json")
        val WEATHER_FETCHED = longPreferencesKey("weather_fetched_at")
        val WEATHER_LAT = doublePreferencesKey("weather_latitude")
        val WEATHER_LON = doublePreferencesKey("weather_longitude")
        val WEATHER_FAILED = longPreferencesKey("weather_failed_at")
        val LOCATION_DENIED = booleanPreferencesKey("location_permission_denied")
    }
}

/** DataStore de los ajustes (o del estado del teléfono); si el archivo está corrupto se sustituye por uno vacío. */
fun createSettingsDataStore(scope: CoroutineScope, produceFile: () -> File): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = scope,
        produceFile = produceFile,
    )
