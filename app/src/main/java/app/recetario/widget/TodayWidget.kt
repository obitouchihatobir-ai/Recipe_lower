package app.recetario.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.Button
import androidx.glance.ButtonDefaults
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.color.ColorProvider as dayNight
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.semantics.semantics
import androidx.glance.semantics.testTag
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.core.ui.theme.paletteFor
import app.recetario.home.todaySources
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime

/**
 * Los colores de los widgets: Cálido con el tema claro del sistema y Nocturno con el oscuro (diseño 2, §7.8). Todos
 * opacos; texto y fondo con 4,5:1 o más (lo comprueba `TodayWidgetTest`).
 */
data class WidgetColors(val bg: Color, val ink: Color, val muted: Color, val accent: Color, val onAccent: Color) {
    companion object {
        val day: WidgetColors = paletteFor(ThemeChoice.CALIDO).let { WidgetColors(it.surface, it.ink, it.muted, it.accent, it.onAccent) }
        val night: WidgetColors = paletteFor(ThemeChoice.NOCTURNO).let { WidgetColors(it.surface, it.ink, it.muted, it.accent, it.onAccent) }

        val bgProvider: ColorProvider = dayNight(day = day.bg, night = night.bg)
        val inkProvider: ColorProvider = dayNight(day = day.ink, night = night.ink)
        val mutedProvider: ColorProvider = dayNight(day = day.muted, night = night.muted)
        val accentProvider: ColorProvider = dayNight(day = day.accent, night = night.accent)
        val onAccentProvider: ColorProvider = dayNight(day = day.onAccent, night = night.onAccent)
    }
}

/**
 * El widget «Hoy» (4 × 2): el momento del día, el plato propuesto y su porqué corto, con «Otra idea». Lo que muestra
 * va en su estado de Glance (con la vuelta de «Otra idea»); se recalcula al pintarse, con el trabajo de cada hora
 * ([TodayWidgetWorker]) y con «Otra idea» ([AnotherIdeaAction]).
 */
class TodayWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        refreshTodayWidgets(context, listOf(id), online = false)
        provideContent {
            val content = todayWidgetContentFrom(currentState<Preferences>()) ?: TodayWidgetContent.OpenApp
            TodayWidgetBody(content)
        }
    }
}

/**
 * Recalcula [ids] y guarda lo que muestran en su estado de Glance (cada uno con su vuelta de «Otra idea»), sin
 * repintarlos. Con [online], antes pide el tiempo si el guardado tiene más de una hora.
 */
suspend fun refreshTodayWidgets(context: Context, ids: List<GlanceId>, online: Boolean) {
    updateTodayWidgets(widgetContainer(context), online, ids) { id, compute ->
        updateAppWidgetState(context, id) { prefs -> compute(prefs.widgetPick()).writeTo(prefs) }
    }
}

/** Lo que mostraría el widget «Hoy» ahora (con la vuelta [stored]); con la guarda fallida, «Abre Sazón». */
suspend fun loadTodayWidgetContent(context: Context, stored: WidgetPick?): TodayWidgetContent {
    var content: TodayWidgetContent = TodayWidgetContent.OpenApp
    updateTodayWidgets(widgetContainer(context), online = false, ids = listOf(Unit)) { _, compute -> content = compute(stored) }
    return content
}

/** «Otra idea» del widget: otro plato para este día y momento; la vuelta queda en el estado de Glance. */
class AnotherIdeaAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val container = widgetContainer(context)
        val sources = container?.let { c ->
            try {
                c.todaySources().first()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }
        updateAppWidgetState(context, glanceId) { prefs ->
            val content = if (container == null || sources == null) {
                TodayWidgetContent.OpenApp
            } else {
                todayWidgetAnotherIdea(sources, LocalDateTime.now(container.clock), prefs.widgetPick())
            }
            content.writeTo(prefs)
        }
        TodayWidget().update(context, glanceId)
    }
}

private val small = TextStyle(color = WidgetColors.mutedProvider, fontSize = 12.sp)
private val body = TextStyle(color = WidgetColors.inkProvider, fontSize = 13.sp)
private val title = TextStyle(color = WidgetColors.inkProvider, fontSize = 17.sp, fontWeight = FontWeight.Bold)

/** El widget «Hoy» pintado (sin estado: lo usan las pruebas). */
@Composable
fun TodayWidgetBody(content: TodayWidgetContent) {
    val context = LocalContext.current
    val frame = GlanceModifier.fillMaxSize().background(WidgetColors.bgProvider).cornerRadius(16.dp).padding(12.dp)
    when (content) {
        TodayWidgetContent.OpenApp, TodayWidgetContent.NoRecipes -> Box(
            frame.clickable(actionStartActivity(openAppIntent(context))).semantics { testTag = "widget-today-open" },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (content == TodayWidgetContent.OpenApp) WIDGET_OPEN_APP_TEXT else WIDGET_NO_RECIPES_TEXT,
                style = body.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium),
            )
        }
        is TodayWidgetContent.Dish -> Column(frame) {
            Column(
                GlanceModifier.fillMaxWidth().defaultWeight()
                    .clickable(actionStartActivity(openRecipeIntent(context, content.recipeId)))
                    .semantics { testTag = "widget-today-dish" },
            ) {
                Text(content.eyebrow, style = small, maxLines = 1)
                Spacer(GlanceModifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(content.emoji, style = title)
                    Spacer(GlanceModifier.width(6.dp))
                    Text(content.title, style = title, maxLines = 2, modifier = GlanceModifier.defaultWeight())
                }
                Spacer(GlanceModifier.height(2.dp))
                Text(content.reason, style = body, maxLines = 2)
            }
            Button(
                text = WIDGET_ANOTHER_IDEA,
                onClick = actionRunCallback<AnotherIdeaAction>(),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = WidgetColors.accentProvider,
                    contentColor = WidgetColors.onAccentProvider,
                ),
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                modifier = GlanceModifier.height(48.dp),
            )
        }
    }
}
