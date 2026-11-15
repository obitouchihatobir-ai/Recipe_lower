package app.recetario

import android.app.Application
import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.work.WorkManager
import app.recetario.backup.BackupDocuments
import app.recetario.backup.BackupManager
import app.recetario.backup.ContentResolverDocuments
import app.recetario.backup.scheduleWeeklyBackup
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
        // WorkManager se inicia solo (androidx.startup) antes de onCreate. En las pruebas de Robolectric no se
        // ejecutan los proveedores del manifiesto: ahí no está iniciado y no se programa nada.
        if (WorkManager.isInitialized()) {
            scheduleWeeklyBackup(WorkManager.getInstance(this))
        } else {
            Log.w("RecetarioApp", "WorkManager no está iniciado: no se programa la copia semanal")
        }
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
    /** Documentos de fuera (Drive, Descargas…) de las copias; las pruebas pasan uno falso. */
    val backupDocuments: BackupDocuments = ContentResolverDocuments(context.contentResolver),
) {
    val photoStore = PhotoStore(File(context.filesDir, "photos"))
    val draftsDir = File(context.filesDir, "drafts")
    val recipeRepository: RecipeRepository = RoomRecipeRepository(database, photoStore, clock)

    val settingsRepository = SettingsRepository(settingsDataStore)

    /** El tamaño de la taza sale de los ajustes. */
    val shoppingRepository: ShoppingRepository = RoomShoppingRepository(database, clock, settingsRepository.cupMl)

    /**
     * Copias de seguridad: temporales de importar y exportar en la caché, copias del teléfono (semanales y previas a
     * importar) en `filesDir/backups/` y la copia en Drive. La semanal la programa [RecetarioApp] (AutoBackupWorker).
     */
    val backupManager = BackupManager(
        db = database,
        photoStore = photoStore,
        settings = settingsRepository,
        clock = clock,
        cacheDir = context.cacheDir,
        filesDir = context.filesDir,
        documents = backupDocuments,
        appVersion = appVersionName(context),
    )

    /**
     * Temporizadores del modo cocina: uno para toda la app, así sobreviven al giro y a salir de la pantalla.
     * Un único bucle de la app los hace avanzar cada 250 ms, solo mientras alguno corre.
     */
    val timerManager = TimerManager(elapsedRealtime)

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launchTimerTicker(timerManager)
    }
}

private fun appVersionName(context: Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()

/** DataStore de los ajustes: archivo `ajustes` en `filesDir/datastore/`. */
private fun createSettingsDataStore(context: Context): DataStore<Preferences> = createSettingsDataStore(
    scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    produceFile = { context.preferencesDataStoreFile("ajustes") },
)
