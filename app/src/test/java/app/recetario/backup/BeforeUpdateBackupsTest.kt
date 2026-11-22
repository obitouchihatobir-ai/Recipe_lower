package app.recetario.backup

import app.recetario.testing.CloseDatabaseRule
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.core.app.ActivityOptionsCompat
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.recetario.AppContainer
import app.recetario.core.data.AppDatabase
import app.recetario.core.data.DatabaseUpgradeGuard
import app.recetario.core.data.GuardResult
import app.recetario.core.data.createV1Database
import app.recetario.core.data.readDatabaseFile
import app.recetario.core.ui.theme.RecetarioTheme
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.testing.installTestContainer
import app.recetario.testing.sampleRecipe
import app.recetario.testing.testClock
import app.recetario.testing.waitFor
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Copias de la base de datos de antes de actualizar en «Copias en el teléfono»: listarlas, guardarlas fuera y restaurarlas. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class BeforeUpdateBackupsTest {
    /** Cierra la base de datos cuando la regla de Compose ya desechó la composición (CLAUDE.md, problemas conocidos). */
    @get:Rule(order = 0)
    val closeDatabase = CloseDatabaseRule { container.database.close() }

    @get:Rule(order = 1)
    val rule = createComposeRule()

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "copias-prueba.db"
    private val dbFile: File get() = context.getDatabasePath(dbName)
    private val documents = FakeDocuments()
    private val restarts = AtomicInteger()
    private lateinit var container: AppContainer

    private val copyName = "antes-de-actualizar-v1-2026-11-13-2140.db"

    /**
     * Un teléfono que pasó de la 0.1.0 a esta versión: la guarda copió la v1 (con «Sopa de tortilla») y Room la
     * migró; después se añadió otra receta.
     */
    private fun upgradedPhone() {
        createV1Database(helper, dbName)
        val backupsDir = File(context.filesDir, BackupManager.BACKUPS_DIR)
        assertThat(DatabaseUpgradeGuard(dbFile, backupsDir, testClock).prepare()).isEqualTo(GuardResult.Copied(copyName))
        container = installTestContainer(
            documents = documents,
            database = AppDatabase.create(context, dbName),
            databaseFile = dbFile,
            restartApp = { restarts.incrementAndGet() },
        )
        runBlocking { container.recipeRepository.save(sampleRecipe("nueva", "Gazpacho", categoryId = "cat-propia")) }
    }

    private class FakePicker(private val uri: Uri) : ActivityResultRegistryOwner {
        val inputs = mutableListOf<Any?>()
        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                inputs += input
                dispatchResult(requestCode, uri)
            }
        }
    }

    private fun launch(picker: FakePicker = FakePicker(Uri.parse("content://prueba/$copyName"))): FakePicker {
        rule.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides picker) {
                RecetarioTheme(ThemeChoice.CALIDO) { BackupRoute(container, onBack = {}) }
            }
        }
        rule.waitFor(hasTestTag("backup-screen"))
        return picker
    }

    @Test
    fun backupList_showsBeforeUpdateEntry() {
        upgradedPhone()
        val dir = container.backupManager.localBackupsDir
        File(dir, "antes-de-actualizar-v1-2026-11-01-0900.db").writeText("rota")
        File(dir, "a-mano.db").writeText("no es de las nuestras")

        assertThat(runBlocking { container.backupManager.localBackups() }).containsExactly(
            LocalBackup(copyName, recipes = 1),
            LocalBackup("antes-de-actualizar-v1-2026-11-01-0900.db", recipes = null),
        ).inOrder()

        val picker = launch()
        rule.waitFor(hasTestTag("local-save-$copyName"))
        rule.onNodeWithText("Antes de actualizar · 13 de noviembre de 2026, 21:40").assertExists()
        rule.onNodeWithTag("local-backup-$copyName").assertExists()
        rule.onNodeWithTag("local-restore-$copyName").assertExists()
        // La dañada sale, sin acciones; la ajena no sale.
        rule.onNodeWithTag("local-restore-antes-de-actualizar-v1-2026-11-01-0900.db").assertDoesNotExist()
        rule.onNodeWithTag("local-backup-a-mano.db").assertDoesNotExist()

        // «Guardar fuera» copia el archivo tal cual.
        rule.onNodeWithTag("backup-list").performScrollToNode(hasTestTag("local-save-$copyName"))
        rule.onNodeWithTag("local-save-$copyName").performClick()
        rule.waitFor(hasText(BackupMessages.EXPORTED))
        assertThat(picker.inputs.single()).isEqualTo(copyName)
        assertThat(documents.written.toList()).isEqualTo(File(dir, copyName).readBytes().toList())
    }

    @Test
    fun beforeUpdateCopy_newerVersion_isDamaged_andNotRestored() {
        upgradedPhone()
        // Una copia con recetas pero de una versión de la base de datos más nueva que la de la app.
        val newer = "antes-de-actualizar-v1-2026-11-12-0900.db"
        val file = File(container.backupManager.localBackupsDir, newer)
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE recipes (id TEXT PRIMARY KEY, title TEXT)")
            db.execSQL("INSERT INTO recipes VALUES ('x', 'Del futuro')")
            db.version = AppDatabase.VERSION + 1
        }
        val bytes = file.readBytes()

        assertThat(runBlocking { container.backupManager.localBackups() }).containsExactly(
            LocalBackup(copyName, recipes = 1),
            LocalBackup(newer, recipes = null),
        ).inOrder()
        assertThat(runBlocking { container.backupManager.restoreBeforeUpdate(newer) })
            .isEqualTo(BackupResult.Error(BackupMessages.NOT_A_BACKUP))
        assertThat(restarts.get()).isEqualTo(0)
        assertThat(file.readBytes().toList()).isEqualTo(bytes.toList())
        assertThat(readDatabaseFile(dbFile, live = true).first).isEqualTo(AppDatabase.VERSION)

        // En la pantalla, «Copia dañada» sin acciones.
        launch()
        rule.waitFor(hasText(BackupMessages.DAMAGED))
        rule.onNodeWithTag("local-restore-$newer").assertDoesNotExist()
        rule.onNodeWithTag("local-save-$newer").assertDoesNotExist()
    }

    @Test
    fun restoreBeforeUpdate_replacesDatabase() {
        upgradedPhone()
        launch()
        rule.waitFor(hasTestTag("local-restore-$copyName"))

        rule.onNodeWithTag("backup-list").performScrollToNode(hasTestTag("local-restore-$copyName"))
        rule.onNodeWithTag("local-restore-$copyName").performClick()
        rule.waitFor(hasTestTag("import-mode-panel"))
        rule.onNodeWithTag("import-merge").assertDoesNotExist()
        rule.onNodeWithTag("import-replace").performClick()
        rule.waitFor(hasTestTag("replace-confirm-panel"))
        rule.onNodeWithText("La copia tiene 1 receta; ahora tienes 2.", substring = true).assertExists()
        rule.onNodeWithText("Sazón se reiniciará", substring = true).assertExists()
        // Nada ha cambiado aún.
        assertThat(restarts.get()).isEqualTo(0)

        rule.onNodeWithTag("replace-confirm").performClick()
        rule.waitUntil(5_000) { restarts.get() == 1 }
        rule.waitFor(hasText(BackupMessages.DATABASE_RESTORED))

        // Antes, la copia previa a importar habitual, con las 2 recetas de ahora.
        val dir = container.backupManager.localBackupsDir
        assertThat(dir.list()!!.toList()).containsExactly(copyName, "antes-de-importar-2026-11-13-2140.zip")
        assertThat(runBlocking { container.backupManager.localBackups() })
            .contains(LocalBackup("antes-de-importar-2026-11-13-2140.zip", recipes = 2))
        // La base de datos es la copia, sin el WAL de la anterior; al reiniciar, Room vuelve a migrarla.
        assertThat(File(dbFile.path + "-wal").exists()).isFalse()
        assertThat(readDatabaseFile(dbFile, live = true)).isEqualTo(1 to listOf("Sopa de tortilla"))
        val reopened = AppDatabase.create(context, dbName)
        try {
            val titles = runBlocking { reopened.recipeDao().observeRows().first().map { it.recipe.title } }
            assertThat(titles).containsExactly("Sopa de tortilla")
        } finally {
            reopened.close()
        }
    }
}
