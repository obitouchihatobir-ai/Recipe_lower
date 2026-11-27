package app.recetario.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll
import androidx.work.WorkManager
import app.recetario.AppContainer
import app.recetario.MainActivity
import app.recetario.RecetarioApp
import app.recetario.core.data.GuardResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * El widget «Hoy» (4 × 2). Mientras exista alguno, un trabajo lo repinta cada hora ([TodayWidgetWorker]); se
 * programa con el primero ([onEnabled]) y se quita con el último ([onDisabled]).
 */
class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        // En las pruebas de Robolectric WorkManager no se inicia solo.
        if (WorkManager.isInitialized()) scheduleTodayWidgetWork(WorkManager.getInstance(context))
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        if (WorkManager.isInitialized()) cancelTodayWidgetWork(WorkManager.getInstance(context))
    }
}

/** El widget «Compra» (4 × 2 o mayor): se repinta cuando cambia la lista ([WidgetUpdates]). */
class ShoppingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ShoppingWidget()
}

/**
 * El contenedor de la app si los widgets pueden abrir la base de datos; null si la guarda falló (no se hizo la copia
 * previa a la migración: abrirla la migraría sin copia). Espera antes a que `RecetarioApp.onCreate` haya pasado la
 * guarda: un widget o un trabajo nunca abren Room antes.
 */
suspend fun widgetContainer(context: Context): AppContainer? {
    val app = context.applicationContext as RecetarioApp
    app.awaitStarted()
    return if (app.upgrade is GuardResult.Failed) null else app.container
}

/** ¿Hay algún widget «Hoy» en la pantalla de inicio? */
fun hasTodayWidgets(context: Context): Boolean =
    AppWidgetManager.getInstance(context)?.getAppWidgetIds(ComponentName(context, TodayWidgetReceiver::class.java))
        ?.isNotEmpty() == true

/** Avisos a los widgets de que cambió lo que muestran. */
interface WidgetUpdates {
    /** Cambió la lista de la compra (lo llama el repositorio tras cada escritura). No bloquea. */
    fun shoppingChanged()
}

/**
 * Repinta los widgets «Compra» fuera del hilo que escribe. Los avisos seguidos se juntan en uno (canal `CONFLATED`):
 * marcar varios artículos deprisa no encola un repintado por cada uno.
 */
class GlanceWidgetUpdates(private val context: Context) : WidgetUpdates {
    private val shopping = Channel<Unit>(Channel.CONFLATED)

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            while (true) {
                shopping.receive()
                try {
                    ShoppingWidget().updateAll(context)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Un widget que no se puede repintar no debe tumbar la app ni la escritura.
                    Log.w("Widgets", "No se pudo repintar el widget de Compra", e)
                }
            }
        }
    }

    override fun shoppingChanged() {
        shopping.trySend(Unit)
    }
}

/** Lo que pide abrir un toque en un widget. */
sealed interface AppOpen {
    data class Recipe(val id: String) : AppOpen

    data object Shopping : AppOpen

    companion object {
        private const val EXTRA_OPEN = "app.recetario.abrir"
        private const val OPEN_RECIPE = "receta"
        private const val OPEN_SHOPPING = "compra"
        private const val EXTRA_RECIPE_ID = "app.recetario.receta"

        /** Lo que pide [intent]; null si no viene de un widget (o lo repite Android al volver desde Recientes). */
        fun from(intent: Intent?): AppOpen? {
            if (intent == null || intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return null
            return when (intent.getStringExtra(EXTRA_OPEN)) {
                OPEN_RECIPE -> intent.getStringExtra(EXTRA_RECIPE_ID)?.let { Recipe(it) }
                OPEN_SHOPPING -> Shopping
                else -> null
            }
        }

        internal fun intent(context: Context, open: AppOpen?): Intent = Intent(context, MainActivity::class.java).apply {
            // La app ya abierta recibe el toque en `onNewIntent` (sin apilar otra MainActivity).
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            when (open) {
                is Recipe -> {
                    putExtra(EXTRA_OPEN, OPEN_RECIPE)
                    putExtra(EXTRA_RECIPE_ID, open.id)
                    // Un dato distinto por receta: Android no confunde los `PendingIntent` de dos platos.
                    data = Uri.parse("sazon://receta/${Uri.encode(open.id)}")
                }
                Shopping -> {
                    putExtra(EXTRA_OPEN, OPEN_SHOPPING)
                    data = Uri.parse("sazon://compra")
                }
                null -> Unit
            }
        }
    }
}

/** Abre el detalle de la receta [id] (intent explícito a [MainActivity]). */
fun openRecipeIntent(context: Context, id: String): Intent = AppOpen.intent(context, AppOpen.Recipe(id))

/** Abre la pestaña Compra. */
fun openShoppingIntent(context: Context): Intent = AppOpen.intent(context, AppOpen.Shopping)

/** Abre la app tal cual. */
fun openAppIntent(context: Context): Intent = AppOpen.intent(context, null)
