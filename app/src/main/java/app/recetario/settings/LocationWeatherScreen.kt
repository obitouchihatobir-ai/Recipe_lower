package app.recetario.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.recetario.AppContainer
import app.recetario.core.ui.components.MutedText
import app.recetario.core.ui.components.SectionTitle
import app.recetario.core.ui.components.appTextFieldColors
import app.recetario.core.ui.theme.LocalRecetarioColors
import app.recetario.today.ThermalThresholds
import app.recetario.weather.APPROXIMATE_PLACE_NAME
import app.recetario.weather.CitySearchResult
import app.recetario.weather.LocationMode
import app.recetario.weather.Place
import app.recetario.weather.WeatherCache
import app.recetario.weather.WeatherResult
import app.recetario.weather.hasCoarseLocationPermission
import app.recetario.weather.isFor
import app.recetario.weather.label
import app.recetario.weather.weatherLabel
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

const val LOCATION_DENIED_NOTICE = "Sin permiso de ubicación. Puedes elegir una ciudad."
private const val NO_FIX_YET = "El teléfono aún no sabe dónde estás. Prueba más tarde o elige una ciudad."

/** Lo que muestra el panel «Elegir ciudad». */
sealed interface CitySearchState {
    data object Idle : CitySearchState
    data object Searching : CitySearchState
    data class Found(val places: List<Place>) : CitySearchState
    data class Failed(val message: String) : CitySearchState
}

/** «Actualizado a las 14:05» (o «… el 12/11 a las 14:05» si no es de hoy). */
internal fun updatedLine(at: Instant, clock: Clock): String {
    val local = at.atZone(clock.zone)
    val time = local.format(DateTimeFormatter.ofPattern("HH:mm"))
    return if (local.toLocalDate() == LocalDate.now(clock)) "Actualizado a las $time"
    else "Actualizado el ${local.format(DateTimeFormatter.ofPattern("dd/MM"))} a las $time"
}

/**
 * Ajustes › Ubicación y clima (diseño 2, §7.11). El permiso de ubicación aproximada se pide solo al tocar «Usar mi
 * ubicación aproximada» y, si se deniega, no se vuelve a pedir: se ofrece elegir una ciudad.
 */
@Composable
fun LocationWeatherRoute(container: AppContainer, onBack: () -> Unit) {
    val repo = container.settingsRepository
    val weather = container.weatherRepository
    val context = LocalContext.current
    val mode by repo.locationMode.collectAsStateWithLifecycle(initialValue = null)
    val place by weather.location().collectAsStateWithLifecycle(initialValue = null)
    val thresholds by repo.thermalThresholds.collectAsStateWithLifecycle(initialValue = ThermalThresholds())
    val cache by repo.weatherCache.collectAsStateWithLifecycle(initialValue = null)
    val failedAt by repo.weatherFailedAt.collectAsStateWithLifecycle(initialValue = null)
    val snapshot by weather.snapshot.collectAsStateWithLifecycle(initialValue = null)
    val denied by repo.locationPermissionDenied.collectAsStateWithLifecycle(initialValue = false)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var panelOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var search by remember { mutableStateOf<CitySearchState>(CitySearchState.Idle) }

    // Al entrar se mira el tiempo (con la caché de 1 h, casi nunca sale a la red).
    LaunchedEffect(Unit) { weather.refresh() }

    fun say(message: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(message)
        }
    }

    fun useApproximate() {
        scope.launch {
            snackbar.reportFailure("No se pudo guardar la ubicación.") { repo.setLocationMode(LocationMode.APPROXIMATE) }
            if (weather.refresh(force = true) == WeatherResult.NoLocation) say(NO_FIX_YET)
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            useApproximate()
        } else {
            scope.launch { snackbar.reportFailure("No se pudo guardar el ajuste.") { repo.setLocationPermissionDenied() } }
        }
    }

    val currentMode = mode ?: return
    val status = when {
        currentMode == LocationMode.NONE -> null
        failedAt != null && (cache == null || failedAt!! >= cache!!.fetchedAt) -> "No se pudo obtener el tiempo"
        else -> cache?.takeIf { c -> place?.let { c.isFor(it.latitude, it.longitude) } == true }?.let { updatedLine(it.fetchedAt, container.clock) }
    }
    LocationWeatherContent(
        mode = currentMode,
        place = place,
        status = status,
        now = snapshot?.current?.let { "Ahora: ${it.apparentC.roundToInt()} °C, ${weatherLabel(it.weatherCode).lowercase()}" },
        showDeniedNotice = denied && currentMode != LocationMode.APPROXIMATE && !hasCoarseLocationPermission(context),
        thresholds = thresholds,
        onUseApproximate = {
            when {
                hasCoarseLocationPermission(context) -> useApproximate()
                // Ya se denegó: no se vuelve a pedir (Android, además, deja de mostrar el diálogo).
                denied -> say(LOCATION_DENIED_NOTICE)
                else -> permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        },
        onChooseCity = {
            search = CitySearchState.Idle
            panelOpen = true
        },
        onThresholds = { t ->
            if (t.isValid) scope.launch { snackbar.reportFailure("No se pudieron guardar los umbrales.") { repo.setThermalThresholds(t) } }
        },
        onRemove = { scope.launch { snackbar.reportFailure("No se pudo quitar la ubicación.") { repo.clearLocation() } } },
        onBack = onBack,
        snackbar = snackbar,
        cityPanel = if (panelOpen) {
            CityPanelState(query, search)
        } else {
            null
        },
        onQuery = { query = it },
        onSearch = {
            val text = query
            if (text.isNotBlank()) {
                search = CitySearchState.Searching
                scope.launch {
                    search = when (val r = weather.searchCity(text)) {
                        is CitySearchResult.Ok ->
                            if (r.places.isEmpty()) CitySearchState.Failed("No se encontró ninguna ciudad con ese nombre.") else CitySearchState.Found(r.places)
                        CitySearchResult.Offline -> CitySearchState.Failed("Sin conexión. Inténtalo más tarde.")
                        is CitySearchResult.Error -> CitySearchState.Failed(r.message)
                    }
                }
            }
        },
        onPickCity = { picked ->
            panelOpen = false
            scope.launch {
                snackbar.reportFailure("No se pudo guardar la ciudad.") { repo.setCity(picked) }
                weather.refresh(force = true)
            }
        },
        onDismissPanel = { panelOpen = false },
    )
}

data class CityPanelState(val query: String, val search: CitySearchState)

@Composable
fun LocationWeatherContent(
    mode: LocationMode,
    place: Place?,
    status: String?,
    showDeniedNotice: Boolean,
    thresholds: ThermalThresholds,
    onUseApproximate: () -> Unit,
    onChooseCity: () -> Unit,
    onThresholds: (ThermalThresholds) -> Unit,
    onRemove: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    now: String? = null,
    snackbar: SnackbarHostState? = null,
    cityPanel: CityPanelState? = null,
    onQuery: (String) -> Unit = {},
    onSearch: () -> Unit = {},
    onPickCity: (Place) -> Unit = {},
    onDismissPanel: () -> Unit = {},
) {
    val c = LocalRecetarioColors.current
    SettingsPage(
        "Ubicación y clima",
        onBack,
        tag = "location-weather-screen",
        modifier = modifier,
        snackbar = snackbar,
        overlay = {
            if (cityPanel != null) {
                BottomPanel(onDismissPanel, "city-panel") {
                    CityPanel(cityPanel, onQuery, onSearch, onPickCity)
                }
            }
        },
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            MutedText(
                "Hoy usa el tiempo de tu zona para proponerte qué cocinar. Sin ubicación, usa la hora y la estación.",
                Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                fontSizeSp = 14,
            )
            Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp).testTag("loc-current")) {
                Text(
                    when {
                        mode == LocationMode.NONE -> "Sin ubicación"
                        place == null && mode == LocationMode.APPROXIMATE -> "$APPROXIMATE_PLACE_NAME (aún sin datos)"
                        place == null -> "Sin ubicación"
                        mode == LocationMode.APPROXIMATE -> APPROXIMATE_PLACE_NAME
                        else -> place.label()
                    },
                    color = c.ink,
                    style = MaterialTheme.typography.titleMedium,
                )
                if (status != null) MutedText(status, Modifier.testTag("weather-status"), fontSizeSp = 14)
                if (now != null && mode != LocationMode.NONE) MutedText(now, Modifier.testTag("weather-now"), fontSizeSp = 14)
            }
            SettingsRow(
                "Usar mi ubicación aproximada",
                "Solo la aproximada, nunca la exacta",
                Modifier.testTag("loc-approximate"),
                onClick = onUseApproximate,
            )
            if (showDeniedNotice) {
                Text(
                    LOCATION_DENIED_NOTICE,
                    Modifier.padding(horizontal = 18.dp, vertical = 4.dp).testTag("loc-denied"),
                    color = c.ink,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            SettingsRow("Elegir ciudad", "Busca tu ciudad por su nombre", Modifier.testTag("loc-city"), onClick = onChooseCity)

            SectionTitle("Frío y calor", Modifier.padding(start = 18.dp, end = 18.dp, top = 18.dp))
            MutedText("Según la sensación térmica, Hoy propone algo caliente o algo fresco.", Modifier.padding(horizontal = 18.dp), fontSizeSp = 13)
            Column(Modifier.padding(horizontal = 18.dp)) {
                val cold = thresholds.coldBelow
                val hot = thresholds.hotAbove
                StepperRow(
                    label = "Frío por debajo de",
                    value = "$cold °C",
                    tag = "cold",
                    minusDescription = "Frío: un grado menos",
                    plusDescription = "Frío: un grado más",
                    onMinus = { onThresholds(thresholds.copy(coldBelow = cold - 1)) },
                    onPlus = { onThresholds(thresholds.copy(coldBelow = cold + 1)) },
                    minusEnabled = thresholds.copy(coldBelow = cold - 1).isValid,
                    plusEnabled = thresholds.copy(coldBelow = cold + 1).isValid,
                )
                StepperRow(
                    label = "Calor por encima de",
                    value = "$hot °C",
                    tag = "hot",
                    minusDescription = "Calor: un grado menos",
                    plusDescription = "Calor: un grado más",
                    onMinus = { onThresholds(thresholds.copy(hotAbove = hot - 1)) },
                    onPlus = { onThresholds(thresholds.copy(hotAbove = hot + 1)) },
                    minusEnabled = thresholds.copy(hotAbove = hot - 1).isValid,
                    plusEnabled = thresholds.copy(hotAbove = hot + 1).isValid,
                )
            }
            if (mode != LocationMode.NONE) {
                SettingsRow(
                    "Quitar ubicación",
                    "Olvida la ciudad, la ubicación y el tiempo guardado",
                    Modifier.padding(top = 12.dp).testTag("loc-remove"),
                    onClick = onRemove,
                )
            }
            MutedText(
                "Datos del tiempo: Open-Meteo.com, CC BY 4.0",
                Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                fontSizeSp = 12,
            )
        }
    }
}

@Composable
private fun CityPanel(state: CityPanelState, onQuery: (String) -> Unit, onSearch: () -> Unit, onPick: (Place) -> Unit) {
    val c = LocalRecetarioColors.current
    val canSearch = state.query.isNotBlank() && state.search != CitySearchState.Searching
    SectionTitle("Elegir ciudad")
    OutlinedTextField(
        value = state.query,
        onValueChange = onQuery,
        modifier = Modifier.fillMaxWidth().testTag("city-field"),
        label = { Text("Nombre de la ciudad") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { if (canSearch) onSearch() }),
        trailingIcon = {
            TextButton(onClick = onSearch, enabled = canSearch, modifier = Modifier.heightIn(min = 48.dp).testTag("city-search")) {
                Text("Buscar", color = if (canSearch) c.accent else c.muted)
            }
        },
        colors = appTextFieldColors(),
    )
    when (val s = state.search) {
        CitySearchState.Idle -> Unit
        CitySearchState.Searching -> MutedText("Buscando…", fontSizeSp = 14)
        is CitySearchState.Failed -> Text(s.message, color = c.ink, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("city-error"))
        is CitySearchState.Found -> Column {
            s.places.forEachIndexed { i, p ->
                Text(
                    p.label(),
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable(role = Role.Button, onClick = { onPick(p) })
                        .padding(vertical = 12.dp)
                        .testTag("city-result-$i"),
                    color = c.ink,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}
