package app.recetario.widget

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.recetario.home.TodaySources
import app.recetario.home.planTodayFor
import app.recetario.recipes.model.Moment
import app.recetario.recipes.quantity.formatQuantity
import app.recetario.shopping.ShopSection
import app.recetario.shopping.ShoppingItem
import app.recetario.today.MealTimes
import app.recetario.today.momentAt
import app.recetario.today.nextExclude
import app.recetario.today.todayEyebrow
import java.time.LocalDateTime
import java.time.LocalTime

// Lo que pintan los widgets «Hoy» y «Compra» (diseño 2, §7.8), sin Glance: se prueba sin pintar nada.

/** Con la guarda de la base de datos fallida (o la base de datos sin poder abrirse): invita a abrir la app. */
const val WIDGET_OPEN_APP_TEXT = "Abre Sazón"

/** «Hoy» sin recetas. */
const val WIDGET_NO_RECIPES_TEXT = "Abre Sazón para añadir recetas"

/** «Compra» sin nada por comprar. */
const val WIDGET_SHOPPING_EMPTY_TEXT = "No falta nada en la lista"

const val WIDGET_ANOTHER_IDEA = "Otra idea"
const val WIDGET_SHOPPING_TITLE = "Compra"

/**
 * Lo ya propuesto por «Otra idea» en un día y momento ([key], «2026-11-13|DINNER»): los ids en orden ([exclude]) y
 * cuántas veces se pidió ([round], parte de la semilla), como en la tarjeta de Inicio. Va en el estado de Glance.
 */
data class WidgetPick(val key: String, val exclude: List<String>, val round: Int)

/** Lo que muestra el widget «Hoy». */
sealed interface TodayWidgetContent {
    data object OpenApp : TodayWidgetContent

    data object NoRecipes : TodayWidgetContent

    /** El plato que manda: el momento del día ([eyebrow]), la receta y su porqué corto. */
    data class Dish(
        val eyebrow: String,
        val recipeId: String,
        val title: String,
        val emoji: String,
        val reason: String,
        val pick: WidgetPick,
    ) : TodayWidgetContent
}

private fun slotKey(now: LocalDateTime, moment: Moment) = "${now.toLocalDate()}|$moment"

/**
 * El plato de Hoy para [now], con el mismo motor y los mismos datos que la tarjeta de Inicio ([planTodayFor]). Si
 * [stored] es de este día y momento, sigue con su «Otra idea»; si no, empieza de cero.
 */
fun todayWidgetContent(src: TodaySources, now: LocalDateTime, stored: WidgetPick?): TodayWidgetContent {
    if (src.summaries.isEmpty()) return TodayWidgetContent.NoRecipes
    val moment = src.mealTimes.momentAt(now.toLocalTime())
    val key = slotKey(now, moment)
    val pick = stored?.takeIf { it.key == key } ?: WidgetPick(key, emptyList(), 0)
    val plan = planTodayFor(src, now.toLocalDate(), moment, pick.exclude, pick.round)
    val lead = plan.lead ?: return TodayWidgetContent.NoRecipes
    val summary = src.summaries.firstOrNull { it.id == lead.id }
    val category = src.categories.firstOrNull { it.id == summary?.categoryId }
    return TodayWidgetContent.Dish(
        eyebrow = todayEyebrow(moment, plan.context.season),
        recipeId = lead.id,
        title = lead.title,
        emoji = category?.emoji ?: "🍽",
        reason = plan.menu.reason,
        pick = pick,
    )
}

/** «Otra idea»: otro plato sin repetir lo ya propuesto en este día y momento (como la tarjeta). */
fun todayWidgetAnotherIdea(src: TodaySources, now: LocalDateTime, stored: WidgetPick?): TodayWidgetContent {
    val current = todayWidgetContent(src, now, stored) as? TodayWidgetContent.Dish ?: return todayWidgetContent(src, now, stored)
    val moment = src.mealTimes.momentAt(now.toLocalTime())
    val menu = planTodayFor(src, now.toLocalDate(), moment, current.pick.exclude, current.pick.round).menu
    val next = WidgetPick(current.pick.key, menu.nextExclude(current.pick.exclude), current.pick.round + 1)
    return todayWidgetContent(src, now, next)
}

private val KIND = stringPreferencesKey("hoy-tipo")
private val EYEBROW = stringPreferencesKey("hoy-momento")
private val RECIPE_ID = stringPreferencesKey("hoy-receta")
private val TITLE = stringPreferencesKey("hoy-titulo")
private val EMOJI = stringPreferencesKey("hoy-emoji")
private val REASON = stringPreferencesKey("hoy-porque")
private val PICK_KEY = stringPreferencesKey("hoy-clave")
private val PICK_EXCLUDE = stringPreferencesKey("hoy-vistas")
private val PICK_ROUND = intPreferencesKey("hoy-vuelta")

private const val KIND_OPEN_APP = "abrir"
private const val KIND_NO_RECIPES = "sin-recetas"
private const val KIND_DISH = "plato"

/** Guarda [this] en el estado de Glance del widget (con su vuelta de «Otra idea», si es un plato). */
fun TodayWidgetContent.writeTo(prefs: MutablePreferences) {
    listOf(EYEBROW, RECIPE_ID, TITLE, EMOJI, REASON).forEach { prefs.remove(it) }
    when (this) {
        TodayWidgetContent.OpenApp -> prefs[KIND] = KIND_OPEN_APP
        TodayWidgetContent.NoRecipes -> prefs[KIND] = KIND_NO_RECIPES
        is TodayWidgetContent.Dish -> {
            prefs[KIND] = KIND_DISH
            prefs[EYEBROW] = eyebrow
            prefs[RECIPE_ID] = recipeId
            prefs[TITLE] = title
            prefs[EMOJI] = emoji
            prefs[REASON] = reason
            prefs[PICK_KEY] = pick.key
            // Los ids son UUID (o ids de prueba): nunca llevan saltos de línea.
            prefs[PICK_EXCLUDE] = pick.exclude.joinToString("\n")
            prefs[PICK_ROUND] = pick.round
        }
    }
}

/** Lo guardado en el estado de Glance; null si aún no se guardó nada. */
fun todayWidgetContentFrom(prefs: Preferences): TodayWidgetContent? = when (prefs[KIND]) {
    KIND_OPEN_APP -> TodayWidgetContent.OpenApp
    KIND_NO_RECIPES -> TodayWidgetContent.NoRecipes
    KIND_DISH -> {
        val pick = prefs.widgetPick()
        val id = prefs[RECIPE_ID]
        if (pick == null || id == null) {
            null
        } else {
            TodayWidgetContent.Dish(
                eyebrow = prefs[EYEBROW].orEmpty(),
                recipeId = id,
                title = prefs[TITLE].orEmpty(),
                emoji = prefs[EMOJI] ?: "🍽",
                reason = prefs[REASON].orEmpty(),
                pick = pick,
            )
        }
    }
    else -> null
}

/** La vuelta de «Otra idea» guardada en el estado de Glance; null si no hay. */
fun Preferences.widgetPick(): WidgetPick? {
    val key = this[PICK_KEY] ?: return null
    val exclude = this[PICK_EXCLUDE].orEmpty().split("\n").filter { it.isNotEmpty() }
    return WidgetPick(key, exclude, this[PICK_ROUND] ?: 0)
}

/**
 * Cuándo puede cambiar el plato de Hoy después de [now]: el siguiente principio o fin de una franja de comidas, o la
 * medianoche (otro día). El widget se repinta ahí, además de cada hora.
 */
fun nextTodayChange(now: LocalDateTime, times: MealTimes): LocalDateTime {
    val time = now.toLocalTime()
    val next = listOf(times.breakfast, times.lunch, times.snack, times.dinner)
        .flatMap { listOf(it.start, it.end) }
        .filter { it > time && it != LocalTime.MIDNIGHT }
        .minOrNull()
    return if (next != null) now.toLocalDate().atTime(next) else now.toLocalDate().plusDays(1).atStartOfDay()
}

/** Un artículo sin marcar del widget «Compra»: su nombre y la cantidad ya escrita («2 l»; vacía si no tiene). */
data class ShoppingWidgetItem(val id: String, val name: String, val quantity: String)

/** Lo que muestra el widget «Compra». */
sealed interface ShoppingWidgetContent {
    data object OpenApp : ShoppingWidgetContent

    data object Loading : ShoppingWidgetContent

    data class Items(val items: List<ShoppingWidgetItem>) : ShoppingWidgetContent
}

/** Los artículos sin marcar, en el orden del súper (el de la lista de la app). */
fun shoppingWidgetItems(groups: Map<ShopSection, List<ShoppingItem>>): List<ShoppingWidgetItem> =
    ShopSection.entries.flatMap { groups[it].orEmpty() }
        .filter { !it.checked }
        .map { ShoppingWidgetItem(it.id, it.name, formatQuantity(it.quantity, it.unit, it.customUnit)) }
