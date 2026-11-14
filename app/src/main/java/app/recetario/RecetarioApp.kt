package app.recetario

import android.app.Application
import android.content.Context
import android.os.SystemClock
import app.recetario.cooking.TimerManager
import app.recetario.cooking.launchTimerTicker
import app.recetario.core.data.AppDatabase
import app.recetario.recipes.data.PhotoStore
import app.recetario.recipes.data.RecipeRepository
import app.recetario.recipes.data.RoomRecipeRepository
import app.recetario.shopping.RoomShoppingRepository
import app.recetario.shopping.ShoppingRepository
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import app.recetario.settings.SettingsRepository
import app.recetario.settings.createSettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.time.Clock

class RecetarioApp : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/**
 * Dependencias de la app, creadas a mano. Cada tarea registra aquí lo que crea.
 * Las pruebas pasan su propio reloj y una base de datos en memoria.
 */
class AppContainer(
    context: Context,
    val clock: Clock = Clock.systemDefaultZone(),
    val database: AppDatabase = AppDatabase.create(context),
    settingsDataStore: DataStore<Preferences> = createSettingsDataStore(context),
    /** Reloj de los temporizadores: monótono y sigue contando con la pantalla apagada. */
    elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
) {
    val photoStore = PhotoStore(File(context.filesDir, "photos"))
    val draftsDir = File(context.filesDir, "drafts")
    val recipeRepository: RecipeRepository = RoomRecipeRepository(database, photoStore, clock)

    val settingsRepository = SettingsRepository(settingsDataStore)

    /** El tamaño de la taza sale de los ajustes. */
    val shoppingRepository: ShoppingRepository = RoomShoppingRepository(database, clock, settingsRepository.cupMl)

    /**
     * Temporizadores del modo cocina: uno para toda la app, así sobreviven al giro y a salir de la pantalla.
     * Un único bucle de la app los hace avanzar cada 250 ms, solo mientras alguno corre.
     */
    val timerManager = TimerManager(elapsedRealtime)

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launchTimerTicker(timerManager)
    }
}

/** DataStore de los ajustes: archivo `ajustes` en `filesDir/datastore/`. */
private fun createSettingsDataStore(context: Context): DataStore<Preferences> = createSettingsDataStore(
    scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    produceFile = { context.preferencesDataStoreFile("ajustes") },
)
