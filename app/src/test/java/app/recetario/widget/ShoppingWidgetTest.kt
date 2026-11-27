package app.recetario.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.testing.unit.hasRunCallbackClickAction
import androidx.glance.appwidget.testing.unit.hasStartActivityClickAction
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescriptionEqualTo
import androidx.glance.testing.unit.hasTestTag
import androidx.glance.testing.unit.hasText
import androidx.glance.testing.unit.hasTextEqualTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.AppContainer
import app.recetario.recipes.quantity.MeasureUnit
import app.recetario.shopping.ShoppingItem
import app.recetario.testing.CloseDatabaseRule
import app.recetario.testing.RecordingWidgetUpdates
import app.recetario.testing.installTestContainer
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Un id de widget para las acciones que no lo usan (marcar un artículo avisa a todos los widgets de Compra). */
object TestGlanceId : GlanceId

/**
 * El widget «Compra» (diseño 2, §7.8): los artículos sin marcar, el círculo que los marca de verdad (por el
 * repositorio, con la app cerrada) y el aviso a los widgets cada vez que cambia la lista.
 */
@RunWith(AndroidJUnit4::class)
class ShoppingWidgetTest {
    @get:Rule(order = 0)
    val closeDatabase = CloseDatabaseRule { container.database.close() }

    private lateinit var container: AppContainer
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val updates get() = container.widgetUpdates as RecordingWidgetUpdates

    @Before
    fun setUp() {
        container = installTestContainer()
    }

    private fun items(): List<ShoppingItem> = runBlocking { container.shoppingRepository.observe().first().values.flatten() }

    private fun idOf(name: String) = items().single { it.name == name }.id

    private fun widgetItems(): List<ShoppingWidgetItem> = runBlocking {
        (shoppingWidgetContent(context).first { it is ShoppingWidgetContent.Items } as ShoppingWidgetContent.Items).items
    }

    @Test
    fun shoppingWidget_listsUnchecked() {
        runBlocking {
            container.shoppingRepository.addManual("Pan", 1.0, MeasureUnit.PIECE)
            container.shoppingRepository.addManual("Leche", 2.0, MeasureUnit.LITER)
            container.shoppingRepository.addManual("Sal", null, MeasureUnit.TO_TASTE)
            container.shoppingRepository.toggle(idOf("Sal"))
        }

        val shown = widgetItems()
        assertThat(shown.map { it.name }).containsExactly("Pan", "Leche")
        assertThat(shown.single { it.name == "Leche" }.quantity).isEqualTo("2 l")

        val pan = idOf("Pan")
        runGlanceAppWidgetUnitTest {
            setContext(context)
            provideComposable { ShoppingWidgetBody(ShoppingWidgetContent.Items(shown)) }
            onNode(hasTextEqualTo("Pan")).assertExists()
            onNode(hasText("Leche")).assertExists()
            onAllNodes(hasTextEqualTo("Sal")).assertCountEquals(0)
            // El círculo marca el artículo; el título abre Compra.
            onNode(hasContentDescriptionEqualTo("Marcar Pan"))
                .assert(hasRunCallbackClickAction<CheckShoppingItemAction>(checkItemParameters(pan)))
            onNode(hasTestTag("widget-shopping-title")).assert(hasStartActivityClickAction(openShoppingIntent(context)))
        }
        assertThat(AppOpen.from(openShoppingIntent(context))).isEqualTo(AppOpen.Shopping)
    }

    @Test
    fun shoppingWidget_check_persists() {
        runBlocking { container.shoppingRepository.addManual("Pan", 1.0, MeasureUnit.PIECE) }
        val pan = idOf("Pan")
        val before = updates.shoppingChanges

        runBlocking { CheckShoppingItemAction().onAction(context, TestGlanceId, checkItemParameters(pan)) }

        // Marcado en Room (la lista de la app lee lo mismo) y los widgets avisados.
        assertThat(items().single().checked).isTrue()
        assertThat(updates.shoppingChanges).isGreaterThan(before)
        assertThat(widgetItems()).isEmpty()

        // Un segundo toque (el widget aún sin repintar) no lo desmarca.
        runBlocking { CheckShoppingItemAction().onAction(context, TestGlanceId, checkItemParameters(pan)) }
        assertThat(items().single().checked).isTrue()

        runGlanceAppWidgetUnitTest {
            setContext(context)
            provideComposable { ShoppingWidgetBody(ShoppingWidgetContent.Items(emptyList())) }
            onNode(hasTextEqualTo(WIDGET_SHOPPING_EMPTY_TEXT)).assertExists()
        }
    }

    @Test
    fun shoppingRepository_everyChange_notifiesWidgets() {
        val repo = container.shoppingRepository
        fun changes(block: suspend () -> Unit): Int {
            val before = updates.shoppingChanges
            runBlocking { block() }
            return updates.shoppingChanges - before
        }
        assertThat(changes { repo.addManual("Pan", 1.0, MeasureUnit.PIECE) }).isEqualTo(1)
        val pan = idOf("Pan")
        assertThat(changes { repo.toggle(pan) }).isEqualTo(1)
        assertThat(changes { repo.check(pan) }).isEqualTo(1)
        assertThat(changes { repo.setSection(pan, items().single().section) }).isEqualTo(1)
        val cleared = runBlocking { repo.clearChecked() }
        assertThat(changes { repo.restore(cleared) }).isEqualTo(1)
        assertThat(changes { repo.delete(pan) }).isEqualTo(1)
        assertThat(changes { repo.addLines(cleared) }).isEqualTo(1)
    }
}
