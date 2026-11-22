package app.recetario

import android.app.Application
import android.content.Context
import android.content.Intent
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
import app.recetario.core.data.DatabaseUpgradeGuard
import app.recetario.core.data.GuardResult
import app.recetario.diary.DiaryRepository
import app.recetario.diary.LogEntryEditor
import app.recetario.diary.RoomDiaryRepository
import app.recetario.recipes.data.PhotoStore
import app.recetario.recipes.data.RecipeRepository
import app.recetario.recipes.data.RoomRecipeRepository
import app.recetario.pairing.PairingRepository
import app.recetario.pairing.RoomPairingRepository
import app.recetario.pantry.PantryRepository
import app.recetario.plan.MealPlanRepository
import app.recetario.plan.RoomMealPlanRepository
import app.recetario.pantry.RoomPantryRepository
import app.recetario.share.clearCardFiles
import app.recetario.shopping.RoomShoppingRepository
import app.recetario.shopping.ShoppingRepository
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import app.recetario.settings.SettingsRepository
import app.recetario.settings.createSettingsDataStore
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import app.recetario.weather.AndroidLocationProvider
import app.recetario.weather.DefaultWeatherRepository
import app.recetario.weather.LocationProvider
import app.recetario.weather.OpenMeteoClient
import app.recetario.weather.WeatherRepository
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.Clock

class RecetarioApp : Application() {
    lateinit var container: AppContainer

    /**
     * Cómo quedó la base de datos antes de abrirla ([DatabaseUpgradeGuard]). Si es [GuardResult.Failed], nada abre
     * la base de datos (ni la pantalla ni la copia semanal): no se migra sin copia previa. `MainActivity` repite la
     * guarda al abrirse si falló (p. ej., sin espacio y con el proceso aún vivo).
     *
     * `@Volatile`: lo escriben `onCreate` y `MainActivity` (en IO) y lo leen WorkManager e IO, así nadie ve un valor
     * viejo; antes de `onCreate` nadie puede leerlo con sentido, porque [container] aún no existe.
     */
    @Volatile
    var upgrade: GuardResult = GuardResult.Ready

    override fun onCreate() {
        super.onCreate()
        // Antes de que nada pueda abrir Room (la actividad, WorkManager, el servicio de los temporizadores).
        upgrade = DatabaseUpgradeGuard(
            dbFile = getDatabasePath(AppDatabase.FILE_NAME),
            backupsDir = File(filesDir, BackupManager.BACKUPS_DIR),
            clock = Clock.systemDefaultZone(),
        ).prepare()
        container = AppContainer(this)
        // Las fichas compartidas la vez anterior ya no hacen falta (las apps que las recibieron ya las copiaron).
        clearCardFiles(cacheDir)
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
    /** Estado de este teléfono (`estado-local`): no va en la copia de Android. */
    localStateDataStore: DataStore<Preferences> = createLocalStateDataStore(context),
    /** Reloj de los temporizadores: monótono y sigue contando con la pantalla apagada. */
    elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
    /** Documentos de fuera (Drive, Descargas…) de las copias; las pruebas pasan uno falso. */
    val backupDocuments: BackupDocuments = ContentResolverDocuments(context.contentResolver),
    /** Archivo de la base de datos (para restaurar una copia de antes de actualizar); null con la de memoria de las pruebas. */
    val databaseFile: File? = context.getDatabasePath(AppDatabase.FILE_NAME),
    /** Reinicia el proceso tras restaurar la base de datos; las pruebas pasan uno falso (no pueden cerrar la JVM). */
    restartApp: () -> Unit = { restartProcess(context.applicationContext) },
    /** Abre las conexiones a Open-Meteo; las pruebas pasan una falsa (nunca salen a la red). */
    openConnection: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
    /** Ubicación aproximada del teléfono (sin Play Services); las pruebas pasan una falsa. */
    locationProvider: LocationProvider = AndroidLocationProvider(context),
) {
    val photoStore = PhotoStore(File(context.filesDir, "photos"))
    val draftsDir = File(context.filesDir, "drafts")
    val recipeRepository: RecipeRepository = RoomRecipeRepository(database, photoStore, clock)

    /**
     * Trabajo que no debe cortarse al cerrar una pantalla (guardar en el diario desde «¡Listo!» y salir enseguida,
     * borrar las fotos de una entrada cuando caduca «Deshacer»).
     */
    val applicationScope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default +
            // Un fallo al escribir (p. ej. disco lleno) se anota y no cierra la app.
            CoroutineExceptionHandler { _, e -> android.util.Log.e("Sazon", "Fallo en segundo plano", e) },
    )

    val diaryRepository: DiaryRepository = RoomDiaryRepository(database, photoStore, clock)

    /** Editor de una entrada del diario para una pantalla ([scope]: el de su ViewModel). */
    fun newLogEditor(scope: CoroutineScope): LogEntryEditor =
        LogEntryEditor(diaryRepository, photoStore, clock, scope, applicationScope)

    val settingsRepository = SettingsRepository(settingsDataStore, localStateDataStore)

    /** El clima de Open-Meteo (Hoy, Ajustes › Ubicación y clima y el tema según el sol), con su caché en `estado-local`. */
    val weatherRepository: WeatherRepository =
        DefaultWeatherRepository(settingsRepository, OpenMeteoClient(openConnection), locationProvider, clock)

    /** El tamaño de la taza sale de los ajustes. */
    val shoppingRepository: ShoppingRepository = RoomShoppingRepository(database, clock, settingsRepository.cupMl)

    /** Valoraciones de maridaje (👍/👎) y lo que necesitan sus pantallas (recetas propias y catálogo). */
    val pairingRepository: PairingRepository = RoomPairingRepository(database, recipeRepository, clock)

    /** La despensa (pestaña Despensa de Compra); sus básicos están en los ajustes. */
    val pantryRepository: PantryRepository = RoomPantryRepository(database, clock)

    /** El menú de la semana (pestaña Semana de Compra), en `meal_plan`. */
    val mealPlanRepository: MealPlanRepository = RoomMealPlanRepository(database)

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
        databaseFile = databaseFile,
        restartApp = restartApp,
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

/**
 * Reinicia la app: abre de nuevo [MainActivity] en una tarea limpia y termina el proceso. Al arrancar, la guarda
 * vuelve a mirar la base de datos (y, si es de una versión anterior, la copia y Room la migra).
 */
fun restartProcess(context: Context) {
    val intent = Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    context.startActivity(intent)
    Runtime.getRuntime().exit(0)
}

private fun appVersionName(context: Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()

/** DataStore de los ajustes: archivo `ajustes` en `filesDir/datastore/` (va en la copia de Android). */
private fun createSettingsDataStore(context: Context): DataStore<Preferences> = createSettingsDataStore(
    scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    produceFile = { context.preferencesDataStoreFile("ajustes") },
)

/** DataStore del estado del teléfono: archivo `estado-local` en `filesDir/datastore/` (no va en la copia de Android). */
private fun createLocalStateDataStore(context: Context): DataStore<Preferences> = createSettingsDataStore(
    scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    produceFile = { context.preferencesDataStoreFile("estado-local") },
)
