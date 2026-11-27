package app.recetario.widget

import android.annotation.SuppressLint
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.impl.WorkManagerImpl
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import app.recetario.AppContainer
import app.recetario.RecetarioApp
import app.recetario.backup.BackupMessages
import app.recetario.core.data.GuardResult
import app.recetario.recipes.model.ServeTemp
import app.recetario.testing.CloseDatabaseRule
import app.recetario.testing.FakeOpenMeteo
import app.recetario.testing.forecastJson
import app.recetario.testing.installTestContainer
import app.recetario.testing.sampleRecipe
import app.recetario.testing.testClock
import app.recetario.today.MealTimes
import app.recetario.weather.Place
import app.recetario.weather.WeatherCache
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * El trabajo del widget «Hoy» (diseño 2, §7.8): solo existe mientras hay algún widget, usa el tiempo guardado y solo
 * lo pide a Open-Meteo si tiene más de una hora y hay red; con la guarda fallida no abre la base de datos.
 */
@RunWith(AndroidJUnit4::class)
class TodayWidgetWorkerTest {
    @get:Rule(order = 0)
    val closeDatabase = CloseDatabaseRule { container.database.close() }

    private lateinit var container: AppContainer
    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** Una ciudad inventada (nunca la ubicación del usuario). */
    private val place = Place("Ciudad de prueba", null, null, 4.6, -74.08)

    @SuppressLint("RestrictedApi")
    @Test
    fun worker_scheduledOnlyWithWidgets() {
        container = installTestContainer()
        val config = Configuration.Builder()
            .setExecutor(SynchronousExecutor())
            .setWorkerFactory(NoOpWorkerFactory())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        try {
            val workManager = WorkManager.getInstance(context)
            fun infos(name: String) = workManager.getWorkInfosForUniqueWork(name).get()

            // Sin widgets «Hoy», abrir la app no programa nada.
            requestTodayWidgetUpdate(context, hasWidgets = false)
            assertThat(infos(TODAY_WIDGET_WORK)).isEmpty()
            assertThat(infos(TODAY_WIDGET_NOW_WORK)).isEmpty()

            // El primer widget: cada hora, único.
            TodayWidgetReceiver().onEnabled(context)
            val periodic = infos(TODAY_WIDGET_WORK).single()
            assertThat(periodic.periodicityInfo?.repeatIntervalMillis).isEqualTo(TimeUnit.HOURS.toMillis(1))
            assertThat(periodic.state).isEqualTo(WorkInfo.State.ENQUEUED)
            // KEEP: otro widget no lo reinicia.
            TodayWidgetReceiver().onEnabled(context)
            assertThat(infos(TODAY_WIDGET_WORK).single().id).isEqualTo(periodic.id)

            // Con widgets, abrir la app los actualiza.
            requestTodayWidgetUpdate(context, hasWidgets = true)
            assertThat(infos(TODAY_WIDGET_NOW_WORK)).isNotEmpty()

            // Sin ningún widget «Hoy», el trabajo deja de existir.
            TodayWidgetReceiver().onDisabled(context)
            assertThat(infos(TODAY_WIDGET_WORK).single().state).isEqualTo(WorkInfo.State.CANCELLED)
            assertThat(infos(TODAY_WIDGET_MOMENT_WORK).all { it.state.isFinished }).isTrue()
        } finally {
            WorkManagerTestInitHelper.closeWorkDatabase()
            // Las demás pruebas arrancan sin WorkManager, como siempre en Robolectric.
            WorkManagerImpl.setDelegate(null)
        }
    }

    @Test
    fun worker_usesCachedWeather_refreshOnlyIfOld() {
        val fake = FakeOpenMeteo(
            forecast = forecastJson(LocalDate.of(2026, 11, 13), apparent = 9.0, precipitation = 1.2, weatherCode = 61),
        )
        container = installTestContainer(openMeteo = fake)
        fun cachedAgo(age: Duration) = runBlocking {
            container.settingsRepository.setWeatherCache(
                WeatherCache(fake.forecast, testClock.instant().minus(age), place.latitude, place.longitude),
            )
        }
        runBlocking {
            container.recipeRepository.save(sampleRecipe("caldo", "Caldo de pollo", serveTemp = ServeTemp.HOT))
            container.settingsRepository.setCity(place)
        }
        val written = mutableMapOf<Int, TodayWidgetContent>()
        fun run(online: Boolean) = runBlocking {
            updateTodayWidgets(container, online, ids = listOf(7)) { id, compute -> written[id] = compute(null) }
        }

        // El tiempo de hace 30 minutos vale: no se pide nada y el plato sale con él.
        cachedAgo(Duration.ofMinutes(30))
        run(online = true)
        assertThat(fake.forecastRequests).isEmpty()
        val dish = written.getValue(7) as TodayWidgetContent.Dish
        assertThat(dish.recipeId).isEqualTo("caldo")
        assertThat(dish.reason).contains("9 °C")

        // Sin red no se pide aunque tenga más de una hora.
        cachedAgo(Duration.ofHours(2))
        run(online = false)
        assertThat(fake.forecastRequests).isEmpty()

        // Con más de una hora y red, una sola petición.
        run(online = true)
        assertThat(fake.forecastRequests).hasSize(1)
    }

    @Test
    fun worker_guardFailed_neverOpensTheDatabase_andShowsOpenApp() {
        val fake = FakeOpenMeteo()
        container = installTestContainer(openMeteo = fake)
        assertThat(container.database.isOpen).isFalse()
        val app = context as RecetarioApp
        app.upgrade = GuardResult.Failed(BackupMessages.UPDATE_COPY_FAILED)
        try {
            val written = mutableMapOf<Int, TodayWidgetContent>()
            runBlocking {
                updateTodayWidgets(widgetContainer(context), online = true, ids = listOf(1, 2)) { id, compute ->
                    written[id] = compute(null)
                }
            }
            assertThat(written).containsExactly(1, TodayWidgetContent.OpenApp, 2, TodayWidgetContent.OpenApp)
            assertThat(fake.requests).isEmpty()
            assertThat(container.database.isOpen).isFalse()
        } finally {
            app.upgrade = GuardResult.Ready
        }
    }

    @Test
    fun worker_withoutWidgets_doesNothing() {
        val fake = FakeOpenMeteo()
        container = installTestContainer(openMeteo = fake)
        val result = runBlocking { TestListenableWorkerBuilder<TodayWidgetWorker>(context).build().doWork() }
        assertThat(result).isEqualTo(ListenableWorker.Result.success())
        assertThat(fake.requests).isEmpty()
        assertThat(container.database.isOpen).isFalse()
    }

    @Test
    fun nextTodayChange_isTheNextMomentBoundaryOrMidnight() {
        container = installTestContainer()
        val times = MealTimes()
        // 21:40 (cena, hasta las 23:00).
        assertThat(nextTodayChange(LocalDateTime.of(2026, 11, 13, 21, 40), times)).isEqualTo(LocalDateTime.of(2026, 11, 13, 23, 0))
        // 23:30: la medianoche (otro día).
        assertThat(nextTodayChange(LocalDateTime.of(2026, 11, 13, 23, 30), times)).isEqualTo(LocalDateTime.of(2026, 11, 14, 0, 0))
        // 6:00: el desayuno de las 7:00.
        assertThat(nextTodayChange(LocalDateTime.of(2026, 11, 14, 6, 0), times)).isEqualTo(LocalDateTime.of(2026, 11, 14, 7, 0))
        // Justo en un límite: el siguiente.
        assertThat(nextTodayChange(LocalDateTime.of(2026, 11, 14, 7, 0), times)).isEqualTo(LocalDateTime.of(2026, 11, 14, 11, 0))
    }

    /** En la prueba de la programación no se ejecuta el trabajo de verdad. */
    private class NoOpWorkerFactory : WorkerFactory() {
        override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
            object : Worker(appContext, workerParameters) {
                override fun doWork(): Result = Result.success()
            }
    }
}
