package app.recetario.testing

import android.os.SystemClock
import androidx.compose.ui.test.SemanticsMatcher
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.nio.file.Files
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.recetario.AppContainer
import app.recetario.RecetarioApp
import app.recetario.backup.BackupDocuments
import app.recetario.backup.ContentResolverDocuments
import app.recetario.core.data.AppDatabase
import app.recetario.core.data.DefaultCategories
import app.recetario.recipes.model.Difficulty
import app.recetario.recipes.model.FlavorProfile
import app.recetario.recipes.model.Ingredient
import app.recetario.recipes.model.Moment
import app.recetario.recipes.model.Recipe
import app.recetario.recipes.model.ServeTemp
import app.recetario.recipes.model.Step
import app.recetario.recipes.quantity.MeasureUnit
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** 21:40 en Madrid: «Buenas noches». */
val testClock: Clock = Clock.fixed(Instant.parse("2026-11-13T20:40:00Z"), ZoneId.of("Europe/Madrid"))

/**
 * Sustituye el contenedor de la app por uno con la base de datos en memoria (con las categorías de serie).
 * [elapsedRealtime] es el reloj de los temporizadores; [documents], los documentos de fuera de las copias (null: los
 * del sistema, con el `ContentResolver` de Robolectric); [settingsDataStore], los ajustes (por ejemplo, unos que no se
 * pueden guardar); [database] y [databaseFile], una base de datos en archivo (por defecto, en memoria y sin archivo);
 * [restartApp], el reinicio tras restaurar una copia de antes de actualizar (las pruebas no pueden cerrar la JVM).
 */
fun installTestContainer(
    clock: Clock = testClock,
    elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
    documents: BackupDocuments? = null,
    settingsDataStore: DataStore<Preferences> = testSettingsDataStore(),
    database: AppDatabase? = null,
    databaseFile: File? = null,
    restartApp: () -> Unit = {},
): AppContainer {
    val app = ApplicationProvider.getApplicationContext<RecetarioApp>()
    val db = database ?: Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
        .addCallback(DefaultCategories.callback)
        .build()
    return AppContainer(
        app, clock = clock, database = db, settingsDataStore = settingsDataStore, localStateDataStore = testSettingsDataStore(),
        elapsedRealtime = elapsedRealtime,
        backupDocuments = documents ?: ContentResolverDocuments(app.contentResolver),
        databaseFile = databaseFile,
        restartApp = restartApp,
    ).also { app.container = it }
}

/** Ajustes (o estado del teléfono) en un archivo temporal propio de cada contenedor de prueba. */
fun testSettingsDataStore(): DataStore<Preferences> = PreferenceDataStoreFactory.create(
    scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    produceFile = { File(Files.createTempDirectory("ajustes").toFile(), "ajustes.preferences_pb") },
)

fun sampleRecipe(
    id: String,
    title: String,
    categoryId: String = DefaultCategories.FOOD_ID,
    minutes: Int? = 20,
    flavor: FlavorProfile = FlavorProfile(),
    favorite: Boolean = false,
    updatedAt: Long = 0,
    ingredients: List<Ingredient> = emptyList(),
    steps: List<Step> = emptyList(),
    tags: List<String> = emptyList(),
    serveTemp: ServeTemp? = null,
    moments: Set<Moment> = emptySet(),
) = Recipe(
    id = id, title = title, categoryId = categoryId, coverPhoto = null, description = "",
    totalMinutes = minutes, servings = 4, difficulty = Difficulty.EASY, flavor = flavor,
    serveTemp = serveTemp, moments = moments, alcoholic = null, course = null,
    ingredients = ingredients, steps = steps, tags = tags, notes = "", favorite = favorite,
    sourceUrl = null, aiGenerated = false,
    createdAt = Instant.ofEpochSecond(updatedAt), updatedAt = Instant.ofEpochSecond(updatedAt),
)

fun ingredient(id: String, name: String, quantity: Double?, unit: MeasureUnit, section: String? = null) =
    Ingredient(id = id, section = section, name = name, quantity = quantity, unit = unit, customUnit = null, note = "")

/** Espera (hasta [timeoutMillis]) a que algún nodo cumpla [matcher]; los datos de Room llegan en otro hilo. */
fun ComposeTestRule.waitFor(matcher: SemanticsMatcher, timeoutMillis: Long = 5_000) {
    waitUntil(timeoutMillis) { onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
}

/** Espera a que ningún nodo cumpla [matcher]. */
fun ComposeTestRule.waitForGone(matcher: SemanticsMatcher, timeoutMillis: Long = 5_000) {
    waitUntil(timeoutMillis) { onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
}
