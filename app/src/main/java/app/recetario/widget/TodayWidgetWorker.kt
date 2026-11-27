package app.recetario.widget

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.recetario.AppContainer
import app.recetario.home.todaySources
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** El repintado de cada hora del widget «Hoy» (solo mientras hay alguno). */
const val TODAY_WIDGET_WORK = "widget-hoy"

/** Un repintado ya (al abrir la app o al poner el primer widget). */
const val TODAY_WIDGET_NOW_WORK = "widget-hoy-ahora"

/** El repintado al cambiar de momento del día (o de día). */
const val TODAY_WIDGET_MOMENT_WORK = "widget-hoy-momento"

private const val TAG = "TodayWidgetWorker"

/**
 * Repinta los widgets «Hoy» (diseño 2, §7.8): con el tiempo guardado, y antes lo pide a Open-Meteo solo si el guardado
 * tiene más de una hora y hay red. Sin widgets no hace nada. Con la guarda de la base de datos fallida no la abre: los
 * widgets dicen «Abre Sazón». Al terminar programa el repintado del siguiente cambio de momento.
 */
class TodayWidgetWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!hasTodayWidgets(context)) return Result.success()
        return try {
            val ids = GlanceAppWidgetManager(context).getGlanceIds(TodayWidget::class.java)
            refreshTodayWidgets(context, ids, online = isOnline(context))
            val widget = TodayWidget()
            ids.forEach { widget.update(context, it) }
            // Tras abrir la app (o con la guarda ya resuelta), la lista del widget «Compra» también puede haber cambiado.
            ShoppingWidget().updateAll(context)
            scheduleNextMoment(context)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // El de la hora siguiente lo intentará otra vez.
            Log.w(TAG, "No se pudo repintar el widget Hoy", e)
            Result.success()
        }
    }

    /** Un repintado al siguiente cambio de momento (o a medianoche), si la guarda dejó leer los horarios. */
    private suspend fun scheduleNextMoment(context: Context) {
        val container = widgetContainer(context) ?: return
        // El trabajo del momento no se reprograma a sí mismo (REPLACE lo cancelaría): lo hacen el de cada hora y el de ahora.
        if (tags.contains(TODAY_WIDGET_MOMENT_WORK)) return
        val now = LocalDateTime.now(container.clock)
        val next = nextTodayChange(now, container.settingsRepository.mealTimes.first())
        val delay = Duration.between(now, next).plusMinutes(1)
        val request = OneTimeWorkRequestBuilder<TodayWidgetWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .addTag(TODAY_WIDGET_MOMENT_WORK)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(TODAY_WIDGET_MOMENT_WORK, ExistingWorkPolicy.REPLACE, request)
    }
}

/**
 * Lo común del trabajo, de «Hoy» al pintarse y de las pruebas: con [online], pide el tiempo (el repositorio solo lo
 * pide si el guardado tiene más de 1 h); después lee los datos una vez y da a [update] cómo calcular cada widget a
 * partir de su vuelta de «Otra idea». Sin [container] (guarda fallida) o si no se pueden leer, «Abre Sazón».
 */
suspend fun <T> updateTodayWidgets(
    container: AppContainer?,
    online: Boolean,
    ids: List<T>,
    update: suspend (T, (WidgetPick?) -> TodayWidgetContent) -> Unit,
) {
    if (ids.isEmpty()) return
    val sources = container?.let { c ->
        try {
            if (online) c.weatherRepository.refresh()
            c.todaySources().first()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "No se pudieron leer los datos de Hoy", e)
            null
        }
    }
    val now = container?.let { LocalDateTime.now(it.clock) }
    for (id in ids) {
        update(id) { stored ->
            if (sources == null || now == null) TodayWidgetContent.OpenApp else todayWidgetContent(sources, now, stored)
        }
    }
}

/** Hay red con salida a internet (validada). */
private fun isOnline(context: Context): Boolean {
    val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
    val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

/** Con el primer widget «Hoy»: el repintado de cada hora (KEEP: otro widget no lo reinicia) y uno ya. */
fun scheduleTodayWidgetWork(workManager: WorkManager) {
    val hourly = PeriodicWorkRequestBuilder<TodayWidgetWorker>(1, TimeUnit.HOURS).build()
    workManager.enqueueUniquePeriodicWork(TODAY_WIDGET_WORK, ExistingPeriodicWorkPolicy.KEEP, hourly)
    workManager.enqueueUniqueWork(TODAY_WIDGET_NOW_WORK, ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<TodayWidgetWorker>().build())
}

/** Sin ningún widget «Hoy», no queda ningún trabajo suyo. */
fun cancelTodayWidgetWork(workManager: WorkManager) {
    workManager.cancelUniqueWork(TODAY_WIDGET_WORK)
    workManager.cancelUniqueWork(TODAY_WIDGET_NOW_WORK)
    workManager.cancelUniqueWork(TODAY_WIDGET_MOMENT_WORK)
}

/**
 * Al abrir la app: si hay algún widget «Hoy», se repinta (y se asegura el trabajo de cada hora). Sin widgets, nada.
 * En Robolectric WorkManager no se inicia solo y no se programa nada.
 */
fun requestTodayWidgetUpdate(context: Context, hasWidgets: Boolean = hasTodayWidgets(context)) {
    if (!hasWidgets || !WorkManager.isInitialized()) return
    scheduleTodayWidgetWork(WorkManager.getInstance(context))
}
