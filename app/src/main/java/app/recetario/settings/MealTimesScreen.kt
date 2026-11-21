package app.recetario.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.recetario.AppContainer
import app.recetario.core.ui.components.MutedText
import app.recetario.core.ui.components.SectionTitle
import app.recetario.recipes.model.Moment
import app.recetario.today.MealTimes
import app.recetario.today.TimeRange
import app.recetario.today.gapLine
import app.recetario.today.gaps
import app.recetario.today.problem
import kotlinx.coroutines.launch
import java.time.LocalTime

private const val MEAL_STEP_MINUTES = 30L

/** Ajustes › Horarios de comidas (diseño 2, §7.11): cada momento con «−/+» de media hora. */
@Composable
fun MealTimesRoute(container: AppContainer, onBack: () -> Unit) {
    val repo = container.settingsRepository
    val times by repo.mealTimes.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    MealTimesContent(
        times = times,
        onChange = { next ->
            scope.launch {
                // Rangos que se solapan, vacíos o que pasan de la medianoche: se avisa y no se guarda.
                val problem = next.problem()
                if (problem != null) {
                    snackbar.currentSnackbarData?.dismiss()
                    snackbar.showSnackbar(problem)
                } else {
                    snackbar.reportFailure("No se pudieron guardar los horarios.") { repo.setMealTimes(next) }
                }
            }
        },
        onBack = onBack,
        snackbar = snackbar,
    )
}

private val editableMoments = listOf(Moment.BREAKFAST, Moment.LUNCH, Moment.SNACK, Moment.DINNER)

private fun MealTimes.rangeOf(moment: Moment): TimeRange = when (moment) {
    Moment.BREAKFAST -> breakfast
    Moment.LUNCH -> lunch
    Moment.SNACK -> snack
    else -> dinner
}

private fun MealTimes.with(moment: Moment, range: TimeRange): MealTimes = when (moment) {
    Moment.BREAKFAST -> copy(breakfast = range)
    Moment.LUNCH -> copy(lunch = range)
    Moment.SNACK -> copy(snack = range)
    else -> copy(dinner = range)
}

@Composable
fun MealTimesContent(
    times: MealTimes?,
    onChange: (MealTimes) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState? = null,
) {
    SettingsPage("Horarios de comidas", onBack, tag = "meal-times-screen", modifier = modifier, snackbar = snackbar) {
        if (times == null) return@SettingsPage
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 32.dp)) {
            MutedText(
                "Hoy los usa para saber qué toca. De la cena al desayuno es noche; si queda un hueco entre comidas, abajo ves qué cuenta.",
                Modifier.padding(top = 4.dp, bottom = 4.dp),
                fontSizeSp = 14,
            )
            for (moment in editableMoments) {
                val range = times.rangeOf(moment)
                SectionTitle(moment.label, Modifier.padding(top = 14.dp))
                TimeStepper("Empieza", moment, range.start, "meal-${moment.name}-start") { onChange(times.with(moment, range.copy(start = it))) }
                TimeStepper("Termina", moment, range.end, "meal-${moment.name}-end") { onChange(times.with(moment, range.copy(end = it))) }
            }
            val gaps = times.gaps()
            if (gaps.isNotEmpty()) {
                SectionTitle("Entre comidas", Modifier.padding(top = 18.dp, bottom = 4.dp))
                for (gap in gaps) MutedText(gapLine(gap), Modifier.padding(vertical = 2.dp).testTag("meal-gap"), fontSizeSp = 14)
            }
        }
    }
}

@Composable
private fun TimeStepper(label: String, moment: Moment, time: LocalTime, tag: String, onTime: (LocalTime) -> Unit) {
    val what = moment.label.lowercase()
    StepperRow(
        label = label,
        value = time.toString(),
        tag = tag,
        minusDescription = "$label $what: media hora antes",
        plusDescription = "$label $what: media hora después",
        onMinus = { onTime(time.minusMinutes(MEAL_STEP_MINUTES)) },
        onPlus = { onTime(time.plusMinutes(MEAL_STEP_MINUTES)) },
    )
}
