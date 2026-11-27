package app.recetario.widget

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.semantics.testTag
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.recetario.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * El widget «Compra» (4 × 2 o mayor): los artículos sin marcar. Mientras se ve, escucha la lista; además, el
 * repositorio lo repinta cada vez que cambia ([WidgetUpdates]).
 */
class ShoppingWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val content = remember { shoppingWidgetContent(context) }
            val shown by content.collectAsState(ShoppingWidgetContent.Loading)
            ShoppingWidgetBody(shown)
        }
    }
}

/** La lista del widget; con la guarda fallida (o si no se puede leer), «Abre Sazón» sin abrir la base de datos. */
fun shoppingWidgetContent(context: Context): Flow<ShoppingWidgetContent> = flow {
    val container = widgetContainer(context)
    if (container == null) {
        emit(ShoppingWidgetContent.OpenApp)
    } else {
        emitAll(container.shoppingRepository.observe().map { ShoppingWidgetContent.Items(shoppingWidgetItems(it)) })
    }
}.catch { e ->
    if (e is CancellationException) throw e
    Log.w("Widgets", "No se pudo leer la lista de la compra", e)
    emit(ShoppingWidgetContent.OpenApp)
}

private val ITEM_ID = ActionParameters.Key<String>("articulo")

/** Los parámetros del círculo de un artículo. */
fun checkItemParameters(id: String): ActionParameters = actionParametersOf(ITEM_ID to id)

/**
 * El círculo de un artículo: lo marca en Room por el repositorio (que avisa a los widgets), aunque la app esté
 * cerrada. Con la guarda fallida no hace nada.
 */
class CheckShoppingItemAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[ITEM_ID] ?: return
        val container = widgetContainer(context) ?: return
        container.shoppingRepository.check(id)
    }
}

private val itemText = TextStyle(color = WidgetColors.inkProvider, fontSize = 14.sp)
private val quantityText = TextStyle(color = WidgetColors.mutedProvider, fontSize = 13.sp)

/** El widget «Compra» pintado (sin estado: lo usan las pruebas). */
@Composable
fun ShoppingWidgetBody(content: ShoppingWidgetContent) {
    val context = LocalContext.current
    Column(GlanceModifier.fillMaxSize().background(WidgetColors.bgProvider).cornerRadius(16.dp).padding(horizontal = 8.dp, vertical = 4.dp)) {
        Text(
            WIDGET_SHOPPING_TITLE,
            style = TextStyle(color = WidgetColors.inkProvider, fontSize = 16.sp, fontWeight = FontWeight.Bold),
            modifier = GlanceModifier.fillMaxWidth()
                .clickable(actionStartActivity(openShoppingIntent(context)))
                .semantics { testTag = "widget-shopping-title" }
                .padding(horizontal = 8.dp, vertical = 12.dp),
        )
        when (content) {
            ShoppingWidgetContent.Loading -> Unit
            ShoppingWidgetContent.OpenApp -> Message(WIDGET_OPEN_APP_TEXT, GlanceModifier.clickable(actionStartActivity(openAppIntent(context))))
            is ShoppingWidgetContent.Items -> if (content.items.isEmpty()) {
                Message(WIDGET_SHOPPING_EMPTY_TEXT, GlanceModifier)
            } else {
                LazyColumn(GlanceModifier.fillMaxSize()) {
                    items(content.items) { item -> ShoppingWidgetRow(item) }
                }
            }
        }
    }
}

@Composable
private fun Message(text: String, modifier: GlanceModifier) {
    Box(modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
        Text(text, style = itemText)
    }
}

@Composable
private fun ShoppingWidgetRow(item: ShoppingWidgetItem) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // El círculo ocupa 48 × 48 dp aunque se dibuje más pequeño.
        Box(
            GlanceModifier.size(48.dp)
                .clickable(actionRunCallback<CheckShoppingItemAction>(checkItemParameters(item.id)))
                .semantics { contentDescription = "Marcar ${item.name}" },
            contentAlignment = Alignment.Center,
        ) {
            Image(ImageProvider(R.drawable.widget_check_circle), contentDescription = null, modifier = GlanceModifier.size(22.dp))
        }
        Text(item.name, style = itemText, maxLines = 1, modifier = GlanceModifier.defaultWeight())
        if (item.quantity.isNotEmpty()) {
            Text(item.quantity, style = quantityText, maxLines = 1, modifier = GlanceModifier.padding(start = 8.dp, end = 8.dp))
        }
    }
}
