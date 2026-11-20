package app.recetario.core.data

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.recetario.backup.BackupMessages
import app.recetario.testing.testClock
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

/** Copia previa a la migración (diseño de la etapa 2, §6.2), con una base de datos v1 de verdad. */
@RunWith(AndroidJUnit4::class)
class DatabaseUpgradeGuardTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @get:Rule
    val tmp = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "guarda-prueba.db"
    private val dbFile: File get() = context.getDatabasePath(dbName)
    private val backupsDir by lazy { File(tmp.root, "backups") }

    /** 21:40 en Madrid del 13 de noviembre de 2026. */
    private val expectedCopy = "antes-de-actualizar-v1-2026-11-13-2140.db"

    private fun guard(dir: File = backupsDir) = DatabaseUpgradeGuard(dbFile, dir, testClock)

    @Test
    fun guard_noDatabase_ready() {
        assertThat(dbFile.exists()).isFalse()
        assertThat(guard().prepare()).isEqualTo(GuardResult.Ready)
        // Nada que copiar: ni siquiera se crea la carpeta, ni la base de datos.
        assertThat(backupsDir.exists()).isFalse()
        assertThat(dbFile.exists()).isFalse()
    }

    @Test
    fun guard_v1_copiesThenReady() {
        createV1Database(helper, dbName)

        assertThat(guard().prepare()).isEqualTo(GuardResult.Copied(expectedCopy))

        // Solo la copia: sin temporales ni -wal/-shm/-journal.
        assertThat(backupsDir.list()!!.toList()).containsExactly(expectedCopy)
        // La copia se abre y tiene las recetas, en la versión de antes.
        assertThat(readDatabaseFile(File(backupsDir, expectedCopy))).isEqualTo(1 to listOf("Sopa de tortilla"))
        assertThat(backupsDir.list()!!.toList()).containsExactly(expectedCopy)
        // La base de datos no se ha migrado (eso lo hace Room después).
        assertThat(readDatabaseFile(dbFile, live = true)).isEqualTo(1 to listOf("Sopa de tortilla"))
    }

    @Test
    fun guard_copiesOnlyOnce_thenOpensNormally() {
        createV1Database(helper, dbName)
        assertThat(guard().prepare()).isEqualTo(GuardResult.Copied(expectedCopy))

        // La app abre el mismo archivo con la base de datos de producción: Room migra la v1 y la receta sigue.
        val db = AppDatabase.create(context, dbName)
        try {
            runBlocking {
                assertThat(db.recipeDao().observeRecipe("r1").first()?.recipe?.title).isEqualTo("Sopa de tortilla")
                assertThat(db.diaryDao().getAll()).isEmpty()
            }
            assertThat(db.openHelper.readableDatabase.version).isEqualTo(2)
        } finally {
            db.close()
        }
        assertThat(readDatabaseFile(dbFile, live = true)).isEqualTo(AppDatabase.VERSION to listOf("Sopa de tortilla"))

        // Siguiente arranque: ya es v2, no se copia otra vez.
        assertThat(guard().prepare()).isEqualTo(GuardResult.Ready)
        assertThat(backupsDir.list()!!.toList()).containsExactly(expectedCopy)
    }

    @Test
    fun guard_copyFails_doesNotMigrate() {
        createV1Database(helper, dbName)
        // La carpeta de copias no se puede crear (hay un archivo con su nombre): como un disco lleno, la copia falla.
        backupsDir.parentFile!!.mkdirs()
        backupsDir.writeText("no soy una carpeta")

        assertThat(guard().prepare()).isEqualTo(GuardResult.Failed(BackupMessages.UPDATE_COPY_FAILED))

        assertThat(readDatabaseFile(dbFile, live = true)).isEqualTo(1 to listOf("Sopa de tortilla"))
        assertThat(tmp.root.walk().filter { it.name.endsWith(".tmp") }.toList()).isEmpty()
        assertThat(dbFile.parentFile!!.walk().filter { it.name.endsWith(".tmp") }.toList()).isEmpty()
    }

    @Test
    fun guard_corruptDatabase_distinctMessage_andFileKept() {
        dbFile.parentFile!!.mkdirs()
        val garbage = "esto no es una base de datos, pero no se borra".repeat(200)
        dbFile.writeText(garbage)

        // No es falta de espacio: otro mensaje, y la pantalla de error ofrece guardar el archivo tal cual.
        assertThat(guard().prepare()).isEqualTo(GuardResult.Failed(BackupMessages.DATABASE_UNREADABLE, databaseUnreadable = true))

        // El manejador de serie de Android la habría borrado: sigue ahí, intacta.
        assertThat(dbFile.readText()).isEqualTo(garbage)
        assertThat(backupsDir.list().orEmpty().toList()).isEmpty()
    }

    @Test
    fun guard_failsAfterTmp_cleansUp() {
        createV1Database(helper, dbName)
        val leftovers = mutableListOf<File>()
        // La copia ya está escrita y comprobada; falla justo antes de renombrarla (como un disco que se llena), con
        // restos de SQLite junto al temporal.
        val failing = DatabaseUpgradeGuard(dbFile, backupsDir, testClock, beforeRename = { tmp ->
            assertThat(tmp.isFile).isTrue()
            assertThat(tmp.name).endsWith(".db.tmp")
            for (suffix in listOf("-wal", "-shm", "-journal")) File(tmp.path + suffix).apply { writeText("resto") }.also { leftovers += it }
            throw IOException("disco lleno")
        })

        assertThat(failing.prepare()).isEqualTo(GuardResult.Failed(BackupMessages.UPDATE_COPY_FAILED))

        assertThat(leftovers).hasSize(3)
        // Ni el temporal ni sus -wal, -shm o -journal: la carpeta queda vacía.
        assertThat(backupsDir.list()!!.toList()).isEmpty()
        // La base de datos sigue en la v1, con sus datos.
        assertThat(readDatabaseFile(dbFile, live = true)).isEqualTo(1 to listOf("Sopa de tortilla"))

        // El siguiente arranque, con espacio, sí la copia.
        assertThat(guard().prepare()).isEqualTo(GuardResult.Copied(expectedCopy))
    }

    @Test
    fun guard_keepsTwoNewest() {
        createV1Database(helper, dbName)
        backupsDir.mkdirs()
        val older = listOf(
            "antes-de-actualizar-v1-2026-10-01-0900.db",
            "antes-de-actualizar-v1-2026-11-01-0900.db",
        )
        older.forEach { File(backupsDir, it).writeText("copia anterior") }
        val others = listOf("antes-de-importar-2026-09-01-1000.zip", "Sazon-2026-09-06-0300.zip", "a-mano.db")
        others.forEach { File(backupsDir, it).writeText("otra") }
        // Otra de este mismo minuto: la nueva no la pisa, toma el número siguiente.
        File(backupsDir, expectedCopy).writeText("del mismo minuto")

        assertThat(guard().prepare()).isEqualTo(GuardResult.Copied("antes-de-actualizar-v1-2026-11-13-2140-2.db"))

        assertThat(backupsDir.list()!!.toList())
            .containsExactlyElementsIn(others + expectedCopy + "antes-de-actualizar-v1-2026-11-13-2140-2.db")
        assertThat(File(backupsDir, expectedCopy).readText()).isEqualTo("del mismo minuto")
    }
}
