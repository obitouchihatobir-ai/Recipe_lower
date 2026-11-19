package app.recetario.screenshots

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.app.ActivityOptionsCompat
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.navigation.NavHostController
import androidx.test.core.app.ApplicationProvider
import app.recetario.backup.BackupMessages
import app.recetario.backup.backupOf
import app.recetario.backup.backupZip
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration
import java.io.ByteArrayInputStream
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.AppContainer
import app.recetario.cooking.STRIKE_MS
import app.recetario.core.data.DefaultCategories
import app.recetario.core.ui.components.SERVINGS_ROLL_MS
import app.recetario.core.ui.motion.SAVED_CHECK_MS
import app.recetario.core.ui.theme.RecetarioTheme
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.navigation.About
import app.recetario.navigation.AppNavHost
import app.recetario.navigation.Appearance
import app.recetario.navigation.Backup
import app.recetario.navigation.Categories
import app.recetario.navigation.Converter
import app.recetario.navigation.Home
import app.recetario.navigation.Cooking
import app.recetario.navigation.Settings
import app.recetario.navigation.RecipeDetail
import app.recetario.navigation.RecipeEditor
import app.recetario.recipes.model.FlavorProfile
import app.recetario.recipes.model.Moment
import app.recetario.recipes.model.ServeTemp
import app.recetario.recipes.model.Step
import app.recetario.recipes.quantity.MeasureUnit
import app.recetario.testing.ingredient
import app.recetario.testing.installTestContainer
import app.recetario.testing.sampleRecipe
import app.recetario.testing.waitFor
import app.recetario.testing.waitForGone
import app.recetario.settings.BackupStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant

/**
 * Capturas de Inicio (también con la tarjeta de las copias), Recetas, el editor, el detalle, Ajustes, Acerca de, Apariencia, Categorías, Copias de seguridad (también con Google Drive), el modo cocina, la compra y algunas animaciones a medias (porciones, tachado, confeti, «Receta guardada») en los tres temas, en `app/build/capturas/` (CI las sube como
 * artefacto `capturas`). No comprueban píxeles: sirven para revisarlas a ojo frente a los bocetos.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp-xhdpi")
class CapturasTest {
    @get:Rule
    val rule = createComposeRule()

    private var container: AppContainer? = null

    @After
    fun tearDown() {
        container?.database?.close()
    }

    @Test
    fun calido() = captureAll(ThemeChoice.CALIDO)

    @Test
    fun fresco() = captureAll(ThemeChoice.FRESCO)

    @Test
    fun nocturno() = captureAll(ThemeChoice.NOCTURNO)

    private fun captureAll(theme: ThemeChoice) {
        val c = installTestContainer().also { container = it }
        runBlocking {
            sampleRecipes().forEach { c.recipeRepository.save(it) }
            // Una copia reciente fuera del teléfono: Inicio sin tarjeta de aviso (la tarjeta va en captureHomeNotice).
            c.settingsRepository.updateBackupStatus { it.copy(lastExternal = Instant.parse("2026-11-10T18:05:00Z")) }
        }
        lateinit var nav: NavHostController
        val picker = FilePicker()
        rule.setContent {
            nav = rememberNavController()
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides picker) {
                RecetarioTheme(theme) { AppNavHost(c, navController = nav) }
            }
        }
        val name = theme.name.lowercase()
        rule.waitFor(hasText("Limonada con hierbabuena"))
        save("inicio-$name")

        rule.onNodeWithTag("tab-recipes").performClick()
        rule.waitFor(hasText("Crema de calabaza"))
        save("recetas-$name")

        rule.runOnIdle { nav.navigate(RecipeEditor(id = "crema")) }
        rule.waitFor(hasTestTag("editor-title"))
        save("editor-$name")
        rule.onNodeWithTag("editor-list").performScrollToNode(hasTestTag("flavor-section"))
        save("editor-sabor-$name")
        rule.onNodeWithTag("editor-list").performScrollToNode(hasTestTag("ingredient-name"))
        save("editor-ingredientes-$name")
        rule.onNodeWithTag("editor-list").performScrollToNode(hasTestTag("step-text"))
        save("editor-pasos-$name")

        rule.runOnIdle { nav.navigate(RecipeDetail("calabaza")) }
        rule.waitFor(hasTestTag("detail-list"))
        rule.waitFor(hasText("Vegetariana"))
        save("detalle-$name")
        captureServingsRoll(name)
        rule.onNodeWithTag("detail-list").performScrollToNode(hasText("Sal y pimienta"))
        save("detalle-ingredientes-$name")
        rule.onNodeWithTag("detail-list").performScrollToNode(hasText("Mis notas"))
        save("detalle-pasos-notas-$name")

        rule.runOnIdle { nav.navigate(Settings) }
        rule.waitFor(hasTestTag("settings-screen"))
        save("ajustes-$name")

        rule.runOnIdle { nav.navigate(About) }
        rule.waitFor(hasTestTag("about-screen"))
        rule.waitFor(hasText("Sazón"))
        save("acerca-de-$name")

        rule.runOnIdle { nav.navigate(Appearance) }
        rule.waitFor(hasTestTag("appearance-screen"))
        rule.waitFor(hasTestTag("auto-note"))
        save("apariencia-$name")

        rule.runOnIdle { nav.navigate(Categories) }
        rule.waitFor(hasText("Postres"))
        save("categorias-$name")
        rule.onNodeWithTag("category-add").performClick()
        rule.waitFor(hasTestTag("category-panel"))
        save("categorias-nueva-$name")

        captureConverter(nav, name)
        captureBackup(c, nav, name)
        captureCooking(c, nav, name)
        captureHomeNotice(c, nav, name)
        captureSaved(c, nav, name)
        captureShopping(c, name)
    }

    /** Porciones: el número rodando a mitad del cambio de 4 a 5 (después vuelve a 4 para las demás capturas). */
    private fun captureServingsRoll(name: String) {
        val plus = hasContentDescription("Añadir una porción")
        rule.onNodeWithTag("detail-list").performScrollToNode(plus)
        rule.mainClock.autoAdvance = false
        rule.onNode(plus).performClick()
        pumpUntil { rule.onAllNodes(hasText("5 porciones")).fetchSemanticsNodes().isNotEmpty() }
        rule.mainClock.advanceTimeBy(SERVINGS_ROLL_MS / 2L)
        save("detalle-porciones-rodando-$name", settleMs = 0)
        rule.mainClock.autoAdvance = true
        rule.onNodeWithContentDescription("Quitar una porción").performClick()
        rule.waitFor(hasText("4 porciones"))
        rule.onNodeWithTag("detail-list").performScrollToIndex(0)
    }

    /** Guardar una receta: el aviso «✓ Receta guardada» y la tarjeta que rebota en Inicio. */
    private fun captureSaved(c: AppContainer, nav: NavHostController, name: String) {
        runBlocking { c.settingsRepository.updateBackupStatus { it.copy(localError = null, lastError = null, lastExternal = Instant.parse("2026-11-10T18:05:00Z")) } }
        rule.waitForGone(hasTestTag("home-backup-card"))
        rule.runOnIdle { nav.navigate(RecipeEditor(id = "limonada")) }
        rule.waitFor(hasTestTag("editor-title"))
        rule.waitFor(hasText("Limonada con hierbabuena"))
        rule.onNodeWithText("Guardar").performClick()
        rule.waitFor(hasTestTag("saved-toast"))
        save("guardado-$name", settleMs = SAVED_CHECK_MS.toLong())
    }

    /** Compra con un artículo marcado: ya está debajo de «Comprado», con la casilla rellena. */
    private fun captureShopping(c: AppContainer, name: String) {
        runBlocking {
            c.shoppingRepository.addManual("Calabaza", 800.0, MeasureUnit.GRAM)
            c.shoppingRepository.addManual("Leche", 1.0, MeasureUnit.LITER)
            c.shoppingRepository.addManual("Pan", null, MeasureUnit.PIECE)
        }
        rule.onNodeWithTag("tab-shopping").performClick()
        rule.waitFor(hasText("Pan"))
        rule.mainClock.autoAdvance = false
        rule.onNodeWithText("Pan").performClick()
        rule.mainClock.advanceTimeBy(1_000)
        rule.mainClock.autoAdvance = true
        rule.waitFor(hasText("Comprado"))
        save("compra-$name")
    }

    /** Inicio con el recordatorio de los 30 días y con los errores de la copia automática (teléfono y Drive). */
    private fun captureHomeNotice(c: AppContainer, nav: NavHostController, name: String) {
        runBlocking { c.settingsRepository.updateBackupStatus { BackupStatus() } }
        rule.runOnIdle { nav.popBackStack(Home, inclusive = false) }
        rule.waitFor(hasTestTag("home-backup-card"))
        rule.waitFor(hasText("Hace más de 30 días que no guardas una copia fuera del teléfono."))
        save("inicio-aviso-$name")
        runBlocking {
            c.settingsRepository.updateBackupStatus { it.copy(localError = BackupMessages.LOCAL_FAILED, lastError = BackupMessages.DRIVE_FAILED) }
        }
        rule.waitFor(hasText(BackupMessages.DRIVE_FAILED))
        rule.waitFor(hasText(BackupMessages.LOCAL_FAILED))
        save("inicio-error-$name")
    }

    /**
     * Copias de seguridad (arriba y la lista «Copias en el teléfono», con una buena y una dañada), el panel de
     * «Importar copia» y la confirmación roja de «Reemplazar todo».
     */
    private fun captureBackup(c: AppContainer, nav: NavHostController, name: String) {
        runBlocking {
            c.settingsRepository.updateBackupStatus { it.copy(lastExternal = Instant.parse("2026-11-10T18:05:00Z")) }
            val dir = c.backupManager.localBackupsDir.apply { mkdirs() }
            File(dir, "antes-de-importar-2026-11-12-1830.zip").outputStream().use { c.backupManager.export(it) }
            File(dir, "Sazon-2026-11-08-0300.zip").outputStream().use { c.backupManager.export(it) }
            File(dir, "antes-de-importar-2026-11-01-0900.zip").writeText("roto")
        }
        // El zip que «elige» el selector falso: se comprueba entero antes de ofrecer nada.
        val chosen = backupZip(
            backupOf(sampleRecipe("p1", "Paella"), sampleRecipe("p2", "Gazpacho"), sampleRecipe("p3", "Flan")),
        )
        shadowOf(ApplicationProvider.getApplicationContext<Context>().contentResolver)
            .registerInputStreamSupplier(Uri.parse(PICKED_URI)) { ByteArrayInputStream(chosen) }
        rule.runOnIdle { nav.navigate(Backup) }
        rule.waitFor(hasTestTag("backup-last-external"))
        rule.waitFor(hasTestTag("drive-setup"))
        save("copias-$name")

        // Elegir para Drive un archivo que ya tiene una copia: se pregunta antes de sobrescribirlo.
        rule.onNodeWithTag("backup-list").performScrollToNode(hasTestTag("drive-setup"))
        rule.onNodeWithTag("drive-setup").performClick()
        rule.waitFor(hasTestTag("drive-replace-panel"))
        save("copias-drive-existente-$name")
        rule.onNodeWithTag("drive-cancel").performClick()
        rule.waitForGone(hasTestTag("drive-replace-panel"))

        // Copia automática con Google Drive ya elegido (y su permiso), y después con el error de Drive.
        val resolver = ApplicationProvider.getApplicationContext<Context>().contentResolver
        resolver.takePersistableUriPermission(
            Uri.parse(DRIVE_URI),
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        runBlocking {
            c.settingsRepository.updateBackupStatus {
                it.copy(
                    driveUri = DRIVE_URI,
                    lastLocal = Instant.parse("2026-11-08T02:00:00Z"),
                    lastDrive = Instant.parse("2026-11-08T02:00:05Z"),
                )
            }
        }
        rule.waitFor(hasTestTag("backup-last-drive"))
        rule.onNodeWithTag("backup-list").performScrollToNode(hasTestTag("drive-choose"))
        save("copias-drive-$name")
        runBlocking { c.settingsRepository.updateBackupStatus { it.copy(lastError = BackupMessages.DRIVE_FAILED) } }
        rule.waitFor(hasTestTag("drive-error"))
        rule.onNodeWithTag("backup-list").performScrollToNode(hasTestTag("drive-choose"))
        save("copias-drive-error-$name")
        runBlocking { c.settingsRepository.updateBackupStatus { it.copy(lastError = null) } }
        rule.waitForGone(hasTestTag("drive-error"))
        rule.onNodeWithTag("backup-list").performScrollToNode(hasTestTag("local-backup-antes-de-importar-2026-11-01-0900.zip"))
        rule.waitFor(hasText("Copia dañada"))
        save("copias-telefono-$name")
        rule.onNodeWithTag("backup-list").performScrollToNode(hasTestTag("backup-import"))
        rule.onNodeWithTag("backup-import").performClick()
        rule.waitFor(hasTestTag("import-mode-panel"))
        save("copias-importar-$name")
        rule.onNodeWithTag("import-replace").performClick()
        rule.waitFor(hasTestTag("replace-confirm-panel"))
        save("copias-reemplazar-$name")
        rule.onNodeWithTag("replace-cancel").performClick()
    }

    /**
     * Selector de archivos del sistema falso: «elige» un zip al momento (no se llega a abrir). Lo demás (el permiso
     * de notificaciones del modo cocina) se queda sin respuesta, como con el registro real en Robolectric.
     */
    private companion object {
        const val PICKED_URI = "content://capturas/Sazon-2026-11-13.zip"
        const val DRIVE_URI = "content://capturas/Sazon-copia-automatica.zip"
    }

    private class FilePicker : ActivityResultRegistryOwner {
        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                if (contract is ActivityResultContracts.OpenDocument || contract is ActivityResultContracts.CreateDocument) {
                    dispatchResult(requestCode, Uri.parse(PICKED_URI))
                }
            }
        }
    }

    /** Conversor (1 taza de harina) y la hoja de equivalencias del detalle al tocar «1½ tazas». */
    private fun captureConverter(nav: NavHostController, name: String) {
        rule.runOnIdle { nav.navigate(Converter) }
        rule.waitFor(hasTestTag("converter-screen"))
        rule.onNodeWithTag("converter-value").performTextReplacement("1")
        rule.onNodeWithTag("converter-ingredient").performTextReplacement("harina")
        rule.waitFor(hasTestTag("converter-result"))
        save("conversor-$name")

        rule.runOnIdle { nav.navigate(RecipeDetail("crema")) }
        rule.waitFor(hasTestTag("detail-list"))
        rule.onNodeWithTag("detail-list").performScrollToNode(hasText("1½ tazas"))
        rule.onNodeWithText("1½ tazas").performClick()
        rule.waitFor(hasTestTag("conversions-panel"))
        save("detalle-equivalencias-$name")
    }

    /** Modo cocina: arriba con los ingredientes, el paso 2 expandido con su temporizador en marcha y «¡Listo!». */
    private fun captureCooking(c: AppContainer, nav: NavHostController, name: String) {
        rule.runOnIdle { nav.navigate(Cooking("calabaza", 4)) }
        rule.waitFor(hasTestTag("cooking-list"))
        rule.waitFor(hasText("Paso 1 de 4"))
        save("cocina-$name")

        rule.onNodeWithText("Ingredientes").performClick()
        markDone(1, midwayCapture = "cocina-tachando-$name", midwayMs = STRIKE_MS / 2L)
        rule.waitFor(hasContentDescription("Marcar el paso 2 como hecho"))
        rule.onNodeWithTag("cooking-list").performScrollToNode(hasText("⏱ Iniciar 10:00"))
        rule.onNodeWithText("⏱ Iniciar 10:00").performClick()
        rule.waitFor(hasContentDescription("Pausar el temporizador del paso 2", substring = true))
        // Pasan 4 de sus 10 minutos: el anillo se ha vaciado un 40 % (lo repinta la cuenta atrás).
        ShadowSystemClock.advanceBy(Duration.ofMinutes(4))
        rule.waitFor(hasText("6:00"))
        save("cocina-temporizador-$name")

        markDone(2)
        markDone(3)
        markDone(4, midwayCapture = "cocina-confeti-$name", midwayMs = 450)
        rule.waitFor(hasText("¡Listo!"))
        save("cocina-listo-$name")
        rule.runOnIdle { c.timerManager.cancel("p2") }
    }

    /** Pulsa «Hecho ✓» del paso [step]; con [midwayCapture], captura la animación a los [midwayMs] ms. */
    private fun markDone(step: Int, midwayCapture: String? = null, midwayMs: Long = 0) {
        val button = hasContentDescription("Marcar el paso $step como hecho")
        rule.waitFor(button)
        rule.onNodeWithTag("cooking-list").performScrollToNode(button)
        if (midwayCapture == null) {
            rule.onNode(button).performClick()
            return
        }
        rule.mainClock.autoAdvance = false
        rule.onNode(button).performClick()
        // El paso nuevo llega del ViewModel (otro despachador): se dan fotogramas hasta que se ve.
        val next = if (step == 4) "¡Listo!" else "Paso ${step + 1} de 4"
        pumpUntil { rule.onAllNodes(hasText(next)).fetchSemanticsNodes().isNotEmpty() }
        rule.mainClock.advanceTimeBy(midwayMs)
        save(midwayCapture, settleMs = 0)
        rule.mainClock.autoAdvance = true
    }

    /** Con el reloj parado, avanza fotograma a fotograma (dejando trabajar a los otros hilos) hasta [done]. */
    private fun pumpUntil(done: () -> Boolean) {
        repeat(500) {
            rule.mainClock.advanceTimeByFrame()
            if (done()) return
            Thread.sleep(10)
        }
        error("condición no cumplida")
    }

    /** Guarda una captura; antes adelanta el reloj [settleMs] ms (0: tal cual, a mitad de una animación). */
    private fun save(name: String, settleMs: Long = 1_500) {
        // La entrada escalonada espera con delay(): se adelanta el reloj para capturar el estado final.
        if (settleMs > 0) rule.mainClock.advanceTimeBy(settleMs)
        if (rule.mainClock.autoAdvance) rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(System.getProperty("user.dir"), "build/capturas").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun sampleRecipes() = listOf(
        sampleRecipe(
            "limonada", "Limonada con hierbabuena", DefaultCategories.DRINK_ID, minutes = 10,
            flavor = FlavorProfile(sweet = 1, sour = 3), updatedAt = 600, favorite = true,
        ),
        sampleRecipe(
            "bloody", "Bloody Mary", DefaultCategories.DRINK_ID, minutes = 5,
            flavor = FlavorProfile(salty = 2, sour = 1, spicy = 1), updatedAt = 500,
        ),
        sampleRecipe(
            "crema", "Crema de calabaza", DefaultCategories.FOOD_ID, minutes = 35,
            flavor = FlavorProfile(sweet = 3, salty = 3, sour = 1, spicy = 1), updatedAt = 400, favorite = true,
            serveTemp = ServeTemp.HOT, moments = setOf(Moment.LUNCH, Moment.DINNER), tags = listOf("Vegetariana", "Otoño"),
            ingredients = listOf(
                ingredient("i1", "Calabaza", 800.0, MeasureUnit.GRAM),
                ingredient("i2", "Cebolla", 1.0, MeasureUnit.PIECE),
                ingredient("i3", "Ajo", 2.0, MeasureUnit.CLOVE),
                ingredient("i4", "Caldo de verduras", 750.0, MeasureUnit.MILLILITER),
                ingredient("i5", "Nata", 100.0, MeasureUnit.MILLILITER, section = "Para servir"),
                ingredient("i6", "Harina", 1.5, MeasureUnit.CUP),
            ),
            steps = listOf(
                Step("s1", "Pela la calabaza, quítale las semillas y córtala en cubos medianos.", emptyList(), null, setOf("i1")),
                Step("s2", "Añade la calabaza y el caldo. Cuece 20 minutos a fuego medio.", emptyList(), null, setOf("i1", "i4")),
            ),
        ),
        sampleRecipe(
            "calabaza", "Crema de calabaza", DefaultCategories.FOOD_ID, minutes = 35,
            flavor = FlavorProfile(sweet = 3, salty = 3, sour = 1, spicy = 1), updatedAt = 450, favorite = true,
            serveTemp = ServeTemp.HOT, tags = listOf("Vegetariana"),
            ingredients = listOf(
                ingredient("c1", "Calabaza", 800.0, MeasureUnit.GRAM),
                ingredient("c2", "Cebolla", 1.0, MeasureUnit.PIECE),
                ingredient("c3", "Ajo", 2.0, MeasureUnit.CLOVE),
                ingredient("c4", "Caldo de verduras", 750.0, MeasureUnit.MILLILITER),
                ingredient("c5", "Crema / nata", 100.0, MeasureUnit.MILLILITER),
                ingredient("c6", "Mantequilla", 20.0, MeasureUnit.GRAM),
                ingredient("c7", "Nuez moscada", null, MeasureUnit.PINCH),
                ingredient("c8", "Sal y pimienta", null, MeasureUnit.TO_TASTE),
            ),
            steps = listOf(
                Step("p1", "Pela la calabaza, quítale las semillas y córtala en cubos medianos.", emptyList(), null, setOf("c1")),
                Step("p2", "Sofríe la cebolla y el ajo picados en la mantequilla hasta que se ablanden.", emptyList(), 600, setOf("c2", "c3", "c6")),
                Step("p3", "Añade la calabaza y el caldo. Cuece 20 minutos a fuego medio.", emptyList(), 1200, setOf("c1", "c4")),
                Step("p4", "Tritura, añade la nata, la nuez moscada, sal y pimienta, y sirve caliente.", emptyList(), null, setOf("c5", "c7", "c8")),
            ),
        ).copy(notes = "La próxima vez: un toque de jengibre y menos caldo."),
        sampleRecipe(
            "flan", "Flan casero de la abuela con caramelo líquido y un toque de vainilla", DefaultCategories.DESSERT_ID,
            minutes = 50, flavor = FlavorProfile(sweet = 3), updatedAt = 300,
        ),
        sampleRecipe("tortilla", "Tortilla de patatas", DefaultCategories.FOOD_ID, minutes = 40, flavor = FlavorProfile(salty = 2), updatedAt = 200),
        sampleRecipe("chocolate", "Chocolate caliente", DefaultCategories.DRINK_ID, minutes = 10, flavor = FlavorProfile(sweet = 3, bitter = 1), updatedAt = 100),
    )
}
