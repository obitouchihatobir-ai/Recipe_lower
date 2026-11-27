package app.recetario.widget

import android.content.Context
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.glance.appwidget.testing.unit.hasRunCallbackClickAction
import androidx.glance.appwidget.testing.unit.hasStartActivityClickAction
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasTestTag
import androidx.glance.testing.unit.hasText
import androidx.glance.testing.unit.hasTextEqualTo
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.AppContainer
import app.recetario.RecetarioApp
import app.recetario.backup.BackupMessages
import app.recetario.core.data.DefaultCategories
import app.recetario.core.data.GuardResult
import app.recetario.core.ui.theme.contrastRatio
import app.recetario.home.TodaySources
import app.recetario.home.TodayViewModel
import app.recetario.recipes.model.BaseType
import app.recetario.recipes.model.Category
import app.recetario.recipes.model.Difficulty
import app.recetario.recipes.model.FlavorProfile
import app.recetario.recipes.model.RecipeSummary
import app.recetario.recipes.model.ServeTemp
import app.recetario.testing.CloseDatabaseRule
import app.recetario.testing.MainDispatcherRule
import app.recetario.testing.forecastJson
import app.recetario.testing.installTestContainer
import app.recetario.today.MealTimes
import app.recetario.today.ThermalThresholds
import app.recetario.weather.Place
import app.recetario.weather.parseForecast
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * El widget «Hoy» (diseño 2, §7.8): el mismo plato que la tarjeta de Inicio con los mismos datos, «Otra idea» con la
 * vuelta guardada en el estado de Glance, sin recetas y con la guarda de la base de datos fallida.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class TodayWidgetTest {
    @get:Rule(order = 0)
    val closeDatabase = CloseDatabaseRule { container.database.close() }

    private val dispatcher = StandardTestDispatcher()

    @get:Rule(order = 1)
    val main = MainDispatcherRule(dispatcher)

    private lateinit var container: AppContainer
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val categories = listOf(
        Category(DefaultCategories.FOOD_ID, "Comidas", "🍲", "#C8553D", BaseType.FOOD, 0, true),
        Category(DefaultCategories.DRINK_ID, "Bebidas", "🍹", "#2A7F8E", BaseType.DRINK, 1, true),
        Category(DefaultCategories.DESSERT_ID, "Postres", "🍮", "#B5733A", BaseType.DESSERT, 2, true),
    )

    private fun summary(id: String, title: String, category: String = DefaultCategories.FOOD_ID, temp: ServeTemp = ServeTemp.HOT) =
        RecipeSummary(
            id = id, title = title, categoryId = category, coverPhoto = null, totalMinutes = 30,
            flavor = FlavorProfile(), serveTemp = temp, moments = emptySet(), alcoholic = null,
            difficulty = Difficulty.EASY, favorite = false, tags = emptyList(), ingredientNames = emptyList(),
            updatedAt = Instant.EPOCH, servings = 4,
        )

    private val recipes = listOf(
        summary("sopa", "Crema de calabaza"),
        summary("tacos", "Tacos de canasta"),
        summary("arroz", "Arroz a la mexicana"),
        summary("jamaica", "Agua de jamaica", DefaultCategories.DRINK_ID, ServeTemp.COLD),
        summary("flan", "Flan napolitano", DefaultCategories.DESSERT_ID, ServeTemp.COLD),
    )

    /** Una ciudad inventada (nunca la ubicación del usuario) con 9 °C y lluvia. */
    private val place = Place("Ciudad de prueba", null, null, 4.6, -74.08)
    private val weather = parseForecast(
        forecastJson(LocalDate.of(2026, 11, 13), apparent = 9.0, precipitation = 1.2, weatherCode = 61),
        Instant.parse("2026-11-13T20:00:00Z"),
    )

    /** Viernes 13 de noviembre de 2026, 21:40 en Madrid: cena. */
    private val clock = Clock.fixed(Instant.parse("2026-11-13T20:40:00Z"), ZoneId.of("Europe/Madrid"))
    private val now = LocalDateTime.of(2026, 11, 13, 21, 40)

    private fun sources(list: List<RecipeSummary> = recipes) = TodaySources(
        summaries = list,
        categories = categories,
        stats = emptyMap(),
        pantry = emptyList(),
        basics = emptyList(),
        mealTimes = MealTimes(),
        thresholds = ThermalThresholds(),
        weather = weather,
        place = place,
        ratings = emptyMap(),
    )

    /** La tarjeta Hoy de Inicio con los mismos datos que el widget. */
    private fun card(list: List<RecipeSummary> = recipes) = main.track(
        TodayViewModel(
            summaries = flowOf(list),
            categories = flowOf(categories),
            stats = flowOf(emptyMap()),
            pantry = flowOf(emptyList()),
            basics = flowOf(emptyList()),
            mealTimes = flowOf(MealTimes()),
            thresholds = flowOf(ThermalThresholds()),
            weather = flowOf(weather),
            place = flowOf(place),
            ratings = flowOf(emptyMap()),
            refresh = {},
            clock = clock,
            handle = SavedStateHandle(),
            ticks = emptyFlow(),
            dispatcher = dispatcher,
        ),
    )

    @Test
    fun todayWidget_data_matchesCard() = runTest(dispatcher) {
        container = installTestContainer()
        val vm = card()
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()
        val shown = vm.state.value
        val lead = shown.lead!!

        val widget = todayWidgetContent(sources(), now, stored = null) as TodayWidgetContent.Dish

        assertThat(widget.recipeId).isEqualTo(lead.id)
        assertThat(widget.title).isEqualTo(lead.title)
        assertThat(widget.emoji).isEqualTo(lead.emoji)
        assertThat(widget.reason).isEqualTo(shown.reason)
        assertThat(widget.eyebrow).isEqualTo(shown.eyebrow)
        assertThat(widget.pick).isEqualTo(WidgetPick("2026-11-13|DINNER", emptyList(), 0))

        // El widget lo pinta: el momento, el plato (que abre su detalle en MainActivity) y su porqué.
        runGlanceAppWidgetUnitTest {
            setContext(context)
            provideComposable { TodayWidgetBody(widget) }
            onNode(hasText(widget.eyebrow)).assertExists()
            onNode(hasText(widget.title)).assertExists()
            onNode(hasText(widget.reason)).assertExists()
            onNode(hasTestTag("widget-today-dish")).assert(hasStartActivityClickAction(openRecipeIntent(context, widget.recipeId)))
        }
        val intent = openRecipeIntent(context, widget.recipeId)
        assertThat(intent.component?.className).isEqualTo("app.recetario.MainActivity")
        assertThat(AppOpen.from(intent)).isEqualTo(AppOpen.Recipe(widget.recipeId))
    }

    @Test
    fun todayWidget_anotherIdea() = runTest(dispatcher) {
        container = installTestContainer()
        val vm = card()
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()

        val first = todayWidgetContent(sources(), now, stored = null) as TodayWidgetContent.Dish
        val second = todayWidgetAnotherIdea(sources(), now, first.pick) as TodayWidgetContent.Dish

        assertThat(second.recipeId).isNotEqualTo(first.recipeId)
        assertThat(second.pick).isEqualTo(WidgetPick(first.pick.key, listOf(first.recipeId), 1))
        // La misma propuesta que la tarjeta tras «Otra idea».
        vm.anotherIdea()
        runCurrent()
        assertThat(vm.state.value.lead!!.id).isEqualTo(second.recipeId)

        // La vuelta va en el estado de Glance: al volver a pintarse (o con el trabajo de cada hora) no salta.
        val prefs = mutablePreferencesOf()
        second.writeTo(prefs)
        assertThat(todayWidgetContentFrom(prefs)).isEqualTo(second)
        assertThat(prefs.widgetPick()).isEqualTo(second.pick)
        assertThat(todayWidgetContent(sources(), now, prefs.widgetPick())).isEqualTo(second)

        // Otro momento (el desayuno del sábado) empieza de cero.
        val breakfast = todayWidgetContent(sources(), LocalDateTime.of(2026, 11, 14, 8, 0), prefs.widgetPick())
        assertThat((breakfast as TodayWidgetContent.Dish).pick).isEqualTo(WidgetPick("2026-11-14|BREAKFAST", emptyList(), 0))

        // El botón ejecuta la acción que cambia la propuesta en el widget.
        runGlanceAppWidgetUnitTest {
            setContext(context)
            provideComposable { TodayWidgetBody(second) }
            onNode(hasText(WIDGET_ANOTHER_IDEA)).assert(hasRunCallbackClickAction<AnotherIdeaAction>())
        }
    }

    @Test
    fun todayWidget_noRecipes() {
        container = installTestContainer()
        val content = todayWidgetContent(sources(emptyList()), now, stored = null)
        assertThat(content).isEqualTo(TodayWidgetContent.NoRecipes)

        runGlanceAppWidgetUnitTest {
            setContext(context)
            provideComposable { TodayWidgetBody(content) }
            onNode(hasTextEqualTo(WIDGET_NO_RECIPES_TEXT)).assertExists()
            onNode(hasTestTag("widget-today-open")).assert(hasStartActivityClickAction(openAppIntent(context)))
        }
        // Con la base de datos de verdad (vacía), lo mismo.
        assertThat(runBlocking { loadTodayWidgetContent(context, stored = null) }).isEqualTo(TodayWidgetContent.NoRecipes)
    }

    @Test
    fun widget_guardFailed_showsOpenApp() {
        container = installTestContainer()
        // Sin consultas: la base de datos (en memoria) aún no se ha abierto.
        assertThat(container.database.isOpen).isFalse()
        val app = context as RecetarioApp
        app.upgrade = GuardResult.Failed(BackupMessages.UPDATE_COPY_FAILED)
        try {
            runBlocking {
                assertThat(widgetContainer(context)).isNull()
                assertThat(loadTodayWidgetContent(context, stored = null)).isEqualTo(TodayWidgetContent.OpenApp)
                assertThat(shoppingWidgetContent(context).first()).isEqualTo(ShoppingWidgetContent.OpenApp)
                // Marcar desde el widget tampoco la abre.
                CheckShoppingItemAction().onAction(context, TestGlanceId, checkItemParameters("pan"))
            }
            // Abrirla la migraría sin la copia previa: nadie la ha tocado.
            assertThat(container.database.isOpen).isFalse()

            runGlanceAppWidgetUnitTest {
                setContext(context)
                provideComposable { TodayWidgetBody(TodayWidgetContent.OpenApp) }
                onNode(hasTextEqualTo(WIDGET_OPEN_APP_TEXT)).assertExists()
                onNode(hasTestTag("widget-today-open")).assert(hasStartActivityClickAction(openAppIntent(context)))
            }
            runGlanceAppWidgetUnitTest {
                setContext(context)
                provideComposable { ShoppingWidgetBody(ShoppingWidgetContent.OpenApp) }
                onNode(hasTextEqualTo(WIDGET_OPEN_APP_TEXT)).assertExists()
            }
        } finally {
            app.upgrade = GuardResult.Ready
        }
    }

    @Test
    fun widgetColors_contrastAtLeast4_5() {
        container = installTestContainer()
        // Claro (Cálido) y oscuro (Nocturno), según el sistema: cada texto sobre su fondo, opacos.
        for (colors in listOf(WidgetColors.day, WidgetColors.night)) {
            assertThat(contrastRatio(colors.ink, colors.bg)).isAtLeast(4.5)
            assertThat(contrastRatio(colors.muted, colors.bg)).isAtLeast(4.5)
            assertThat(contrastRatio(colors.onAccent, colors.accent)).isAtLeast(4.5)
            assertThat(contrastRatio(colors.accent, colors.bg)).isAtLeast(3.0)
            assertThat(colors.bg.alpha).isEqualTo(1f)
            assertThat(colors.ink.alpha).isEqualTo(1f)
            assertThat(colors.muted.alpha).isEqualTo(1f)
        }
    }
}
