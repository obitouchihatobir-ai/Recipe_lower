package app.recetario.core.data

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.recetario.AppContainer
import app.recetario.MainActivity
import app.recetario.RecetarioApp
import app.recetario.backup.BackupManager
import app.recetario.backup.BackupMessages
import app.recetario.backup.FakeDocuments
import app.recetario.testing.installTestContainer
import app.recetario.testing.testClock
import app.recetario.testing.waitFor
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.io.File

/** La app no puede abrir la base de datos (§6.2): pantalla de error, sin migrar ni cerrarse en bucle. */
@RunWith(AndroidJUnit4::class)
class UpgradeErrorScreenTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    private val app = ApplicationProvider.getApplicationContext<RecetarioApp>()
    private val documents = FakeDocuments()
    private val dbName = "error-prueba.db"
    private lateinit var container: AppContainer
    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var activity: MainActivity

    @After
    fun tearDown() {
        scenario?.close()
        if (::container.isInitialized) container.database.close()
        app.upgrade = GuardResult.Ready
    }

    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java).also { s -> s.onActivity { activity = it } }
    }

    @Test
    fun upgradeError_showsMessageAndSaveOutside() {
        createV1Database(helper, dbName)
        val backupsDir = File(app.filesDir, BackupManager.BACKUPS_DIR)
        val copy = (DatabaseUpgradeGuard(app.getDatabasePath(dbName), backupsDir, testClock).prepare() as GuardResult.Copied).fileName
        // Una base de datos cuya migración falla (aquí, porque no la tiene): Room lanza una excepción al abrirla.
        container = installTestContainer(
            documents = documents,
            database = Room.databaseBuilder(app, AppDatabase::class.java, dbName).build(),
        )
        launch()

        rule.waitFor(hasTestTag("upgrade-error"))
        rule.waitFor(hasText(BackupMessages.MIGRATION_FAILED))
        rule.onNodeWithTag("upgrade-save-outside").performClick()
        rule.waitForIdle()
        val shadow = shadowOf(activity)
        val started = checkNotNull(shadow.nextStartedActivityForResult) { "No se abrió el selector" }
        assertThat(started.intent.action).isEqualTo(Intent.ACTION_CREATE_DOCUMENT)
        assertThat(started.intent.getStringExtra(Intent.EXTRA_TITLE)).isEqualTo(copy)
        val uri = "content://prueba/$copy"
        shadow.receiveResult(started.intent, Activity.RESULT_OK, Intent().setData(Uri.parse(uri)))

        rule.waitFor(hasText(BackupMessages.EXPORTED))
        assertThat(documents.files.getValue(uri).toList()).isEqualTo(File(backupsDir, copy).readBytes().toList())
        // La base de datos sigue en la v1, intacta.
        assertThat(readDatabaseFile(app.getDatabasePath(dbName), live = true)).isEqualTo(1 to listOf("Sopa de tortilla"))

        // Al girar (o volver a abrirla) sale otra vez la pantalla de error: no se cierra en bucle.
        scenario!!.recreate()
        rule.waitFor(hasText(BackupMessages.MIGRATION_FAILED))
    }

    @Test
    fun upgradeCopyFailed_showsMessage_andNeverOpensTheDatabase() {
        app.upgrade = GuardResult.Failed(BackupMessages.UPDATE_COPY_FAILED)
        container = installTestContainer(documents = documents)
        launch()

        rule.waitFor(hasText(BackupMessages.UPDATE_COPY_FAILED))
        // Sin copia previa no hay nada que guardar, y la app no se ha pintado.
        rule.onNodeWithTag("upgrade-save-outside").assertDoesNotExist()
        rule.onNodeWithTag("tab-recipes").assertDoesNotExist()
        assertThat(container.database.isOpen).isFalse()
    }

    @Test
    fun corruptDatabase_offersSavingTheDatabaseFile_andKeepsIt() {
        val dbFile = app.getDatabasePath(dbName).apply { parentFile!!.mkdirs() }
        dbFile.writeText("una base de datos dañada")
        app.upgrade = GuardResult.Failed(BackupMessages.DATABASE_UNREADABLE, databaseUnreadable = true)
        container = installTestContainer(documents = documents, databaseFile = dbFile)
        launch()

        rule.waitFor(hasText(BackupMessages.DATABASE_UNREADABLE))
        rule.onNodeWithTag("upgrade-save-outside").assertDoesNotExist()
        rule.onNodeWithTag("upgrade-save-database").performClick()
        rule.waitForIdle()
        val shadow = shadowOf(activity)
        val started = checkNotNull(shadow.nextStartedActivityForResult) { "No se abrió el selector" }
        assertThat(started.intent.action).isEqualTo(Intent.ACTION_CREATE_DOCUMENT)
        assertThat(started.intent.getStringExtra(Intent.EXTRA_TITLE)).isEqualTo("Sazon-base-de-datos.db")
        val uri = "content://prueba/Sazon-base-de-datos.db"
        shadow.receiveResult(started.intent, Activity.RESULT_OK, Intent().setData(Uri.parse(uri)))

        rule.waitFor(hasText(BackupMessages.EXPORTED))
        assertThat(String(documents.files.getValue(uri))).isEqualTo("una base de datos dañada")
        // Nunca se borra ni se abre.
        assertThat(dbFile.readText()).isEqualTo("una base de datos dañada")
        assertThat(container.database.isOpen).isFalse()
    }

    @Test
    fun normalStart_opensTheDatabase_andShowsHome() {
        container = installTestContainer(documents = documents)
        launch()
        rule.waitFor(hasText("¿Qué cocinamos hoy?"))
        rule.onNodeWithTag("upgrade-error").assertDoesNotExist()
        assertThat(container.database.isOpen).isTrue()
    }
}
