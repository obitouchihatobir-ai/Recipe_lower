package app.recetario.weather

import app.recetario.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlin.math.abs

/** De dónde sale el tiempo: de ningún sitio, de una ciudad elegida a mano o de la ubicación aproximada del teléfono. */
enum class LocationMode { NONE, CITY, APPROXIMATE }

/**
 * La última respuesta de la previsión tal cual llegó ([json]), cuándo ([fetchedAt]) y para qué sitio. Se guarda en
 * `estado-local` (no va en ninguna copia).
 */
data class WeatherCache(val json: String, val fetchedAt: Instant, val latitude: Double, val longitude: Double)

sealed interface WeatherResult {
    data class Ok(val snapshot: WeatherSnapshot) : WeatherResult

    /** No hay ciudad ni ubicación aproximada: Hoy usa la hora y la estación. */
    data object NoLocation : WeatherResult

    /** Sin conexión: [cached] es el último dato si tiene menos de 6 h. */
    data class Offline(val cached: WeatherSnapshot?) : WeatherResult

    /** Open-Meteo respondió algo que no se puede usar. */
    data class Error(val message: String) : WeatherResult
}

sealed interface CitySearchResult {
    data class Ok(val places: List<Place>) : CitySearchResult
    data object Offline : CitySearchResult
    data class Error(val message: String) : CitySearchResult
}

/** «Bogotá, Bogotá D.C., Colombia» (ciudad, estado y país, los que haya). */
fun Place.label(): String = listOfNotNull(name, admin, country).joinToString(", ")

/** Nombre de la ubicación aproximada: Open-Meteo no dice el nombre de un punto. */
const val APPROXIMATE_PLACE_NAME = "Ubicación aproximada"

/**
 * El clima de Open-Meteo con caché (diseño 2, §7.3). Nada lanza excepciones: cada fallo es un [WeatherResult].
 */
interface WeatherRepository {
    /** La caché si es del sitio de ahora y tiene menos de 6 h; si no, null. */
    val snapshot: Flow<WeatherSnapshot?>

    /**
     * La última previsión guardada del sitio de ahora (sea cual sea su edad); null sin ubicación o si es de otro sitio.
     * El tema según el sol mira sus días.
     */
    val forecast: Flow<WeatherSnapshot?>

    /** Pide a Open-Meteo solo si la caché tiene más de 1 h (o con [force]). */
    suspend fun refresh(force: Boolean = false): WeatherResult

    /** La ciudad elegida o la ubicación aproximada (con [APPROXIMATE_PLACE_NAME]); null sin ubicación. */
    fun location(): Flow<Place?>

    /** Hasta 5 ciudades con ese nombre. */
    suspend fun searchCity(name: String): CitySearchResult
}

class DefaultWeatherRepository(
    private val settings: SettingsRepository,
    private val client: OpenMeteoClient,
    private val locationProvider: LocationProvider,
    private val clock: Clock,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : WeatherRepository {
    /** Una petición a la vez: dos pantallas que piden a la vez comparten la respuesta (la segunda ve la caché nueva). */
    private val lock = Mutex()

    private val place: Flow<Place?> = combine(
        settings.locationMode,
        settings.city,
        settings.approximateLocation,
    ) { mode, city, approx ->
        when (mode) {
            LocationMode.NONE -> null
            LocationMode.CITY -> city
            LocationMode.APPROXIMATE -> approx?.let { Place(APPROXIMATE_PLACE_NAME, null, null, it.latitude, it.longitude) }
        }
    }.distinctUntilChanged()

    override fun location(): Flow<Place?> = place

    override val snapshot: Flow<WeatherSnapshot?> = combine(place, settings.weatherCache) { p, cache ->
        if (p == null || cache == null || !cache.isFor(p.latitude, p.longitude) || age(cache) >= OFFLINE_MAX_AGE) null else cache.parse()
    }.flowOn(Dispatchers.Default)

    override val forecast: Flow<WeatherSnapshot?> = combine(place, settings.weatherCache) { p, cache ->
        // Solo la del sitio de ahora: la salida y la puesta del sol de otra ciudad no valen.
        if (p == null || cache == null || !cache.isFor(p.latitude, p.longitude)) null else cache.parse()
    }.flowOn(Dispatchers.Default)

    override suspend fun refresh(force: Boolean): WeatherResult = try {
        lock.withLock { refreshLocked(force) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Nada debe llegar a la pantalla como excepción (p. ej., ajustes que no se pueden leer).
        WeatherResult.Error("No se pudo obtener el tiempo.")
    }

    private suspend fun refreshLocked(force: Boolean): WeatherResult {
        val coordinates = currentCoordinates() ?: return WeatherResult.NoLocation
        val cache = settings.weatherCache.first()?.takeIf { it.isFor(coordinates.latitude, coordinates.longitude) }
        if (!force && cache != null && age(cache) < FRESH_FOR) withContext(io) { cache.parse() }?.let { return WeatherResult.Ok(it) }
        val now = clock.instant()
        val result = try {
            val json = withContext(io) { client.forecast(coordinates.latitude, coordinates.longitude) }
            val parsed = withContext(io) { parseForecast(json, now) }
            quietly { settings.setWeatherCache(WeatherCache(json, now, coordinates.latitude, coordinates.longitude)) }
            WeatherResult.Ok(parsed)
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            WeatherResult.Offline(cache?.takeIf { age(it) < OFFLINE_MAX_AGE }?.let { withContext(io) { it.parse() } })
        } catch (_: Exception) {
            // Un código de error de Open-Meteo o una respuesta que no se entiende.
            WeatherResult.Error("No se pudo leer el tiempo de Open-Meteo.")
        }
        quietly { settings.setWeatherFailedAt(if (result is WeatherResult.Ok) null else now) }
        return result
    }

    /** Ciudad, o la ubicación aproximada: la del teléfono si la da ahora (y se guarda) o la última guardada. */
    private suspend fun currentCoordinates(): Coordinates? = when (settings.locationMode.first()) {
        LocationMode.NONE -> null
        LocationMode.CITY -> settings.city.first()?.let { Coordinates(it.latitude, it.longitude) }
        LocationMode.APPROXIMATE -> {
            val fresh = withContext(io) { runCatching { locationProvider.lastKnown() }.getOrNull() }
            if (fresh != null) quietly { settings.setApproximateLocation(fresh) }
            fresh ?: settings.approximateLocation.first()
        }
    }

    override suspend fun searchCity(name: String): CitySearchResult {
        if (name.isBlank()) return CitySearchResult.Ok(emptyList())
        return try {
            CitySearchResult.Ok(parseGeocoding(withContext(io) { client.geocode(name) }))
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            CitySearchResult.Offline
        } catch (_: Exception) {
            CitySearchResult.Error("No se pudo buscar la ciudad.")
        }
    }

    private fun age(cache: WeatherCache): Duration = Duration.between(cache.fetchedAt, clock.instant())

    private fun WeatherCache.parse(): WeatherSnapshot? = runCatching { parseForecast(json, fetchedAt) }.getOrNull()

    private suspend fun quietly(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Si no se puede guardar, el dato de ahora sirve igual.
        }
    }

    companion object {
        val FRESH_FOR: Duration = Duration.ofHours(1)
        val OFFLINE_MAX_AGE: Duration = Duration.ofHours(6)
    }
}

/** La caché sirve para un sitio a menos de unos 5 km (la ubicación aproximada baila un poco de una vez a otra). */
internal fun WeatherCache.isFor(lat: Double, lon: Double): Boolean =
    abs(latitude - lat) <= 0.05 && abs(longitude - lon) <= 0.05
