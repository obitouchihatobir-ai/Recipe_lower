package app.recetario.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.recipes.quantity.MeasureUnit
import app.recetario.testing.sampleRecipe
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.time.Instant

/** Copias guardadas en el teléfono (`filesDir/backups/`): listarlas, restaurarlas y sacarlas fuera. */
@RunWith(AndroidJUnit4::class)
class LocalBackupsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var env: BackupTestEnv

    private val t1 = Instant.parse("2026-11-01T10:00:00Z")

    @Before
    fun setUp() {
        env = BackupTestEnv(tmp)
    }

    @After
    fun tearDown() = env.close()

    private suspend fun seed() {
        env.writePhoto("local.jpg", fakePhoto(1))
        env.saveAt(sampleRecipe("local", "Tortilla").copy(coverPhoto = "local.jpg"), t1)
        env.saveAt(sampleRecipe("otra", "Gazpacho"), t1)
        env.shopping.addManual("Huevos", 6.0, MeasureUnit.PIECE)
        env.settings.setAuto(false)
        env.settings.setTheme(ThemeChoice.FRESCO)
    }

    private val paella = backupZip(backupOf(sampleRecipe("copia", "Paella", updatedAt = 900)))

    @Test
    fun localBackups_newestFirst_withRecipeCounts_andDamagedOnes() = runBlocking<Unit> {
        seed()
        assertThat(env.import(paella, ImportMode.REPLACE_ALL)).isEqualTo(ImportResult.Ok(recipes = 1, images = 0))
        assertThat(env.import(paella, ImportMode.REPLACE_ALL)).isEqualTo(ImportResult.Ok(recipes = 1, images = 0))
        env.localFile("antes-de-importar-2026-11-01-0900.zip").writeText("roto")
        env.localFile("antes-de-importar-2026-11-13-2140.zip.tmp").writeText("a medias")
        // Una copia que se lee pero a la que le falta una foto de su manifiesto tampoco se puede restaurar.
        env.localFile("antes-de-importar-2026-11-02-0900.zip").writeBytes(
            zipOf(BACKUP_JSON_ENTRY to encodeBackup(backupOf().copy(images = listOf("falta.jpg"))).toByteArray()),
        )

        assertThat(env.manager.localBackups()).containsExactly(
            LocalBackup("antes-de-importar-2026-11-13-2140-2.zip", recipes = 1),
            LocalBackup("antes-de-importar-2026-11-13-2140.zip", recipes = 2),
            LocalBackup("antes-de-importar-2026-11-02-0900.zip", recipes = null),
            LocalBackup("antes-de-importar-2026-11-01-0900.zip", recipes = null),
        ).inOrder()
    }

    @Test
    fun restoreLocal_bringsBackThePreviousState_afterANewSafetyCopy() = runBlocking<Unit> {
        seed()
        val before = env.snapshot()
        assertThat(env.import(paella, ImportMode.REPLACE_ALL)).isEqualTo(ImportResult.Ok(recipes = 1, images = 0))
        assertThat(env.recipeIds()).containsExactly("copia")

        assertThat(env.manager.previewLocal("antes-de-importar-2026-11-13-2140.zip")).isEqualTo(PreviewResult.Ok(backupRecipes = 2, localRecipes = 1))
        val result = env.manager.restoreLocal("antes-de-importar-2026-11-13-2140.zip")

        assertThat(result).isEqualTo(ImportResult.Ok(recipes = 2, images = 1))
        assertThat(env.snapshot()).isEqualTo(before)
        // Antes de restaurar se guardó lo que había (la paella), por si acaso.
        assertThat(env.backups()).containsExactly("antes-de-importar-2026-11-13-2140.zip", "antes-de-importar-2026-11-13-2140-2.zip")
        assertThat(env.manager.localBackups().first()).isEqualTo(LocalBackup("antes-de-importar-2026-11-13-2140-2.zip", recipes = 1))
    }

    @Test
    fun restoreLocal_onlyAcceptsCopiesOfTheFolder() = runBlocking<Unit> {
        seed()
        val before = env.snapshot()
        File(env.filesDir, "fuera.zip").writeBytes(paella)

        for (name in listOf("../fuera.zip", "no-existe.zip", "")) {
            assertThat(env.manager.restoreLocal(name)).isEqualTo(ImportResult.Error("Este archivo no es una copia de Sazón."))
            assertThat(env.manager.previewLocal(name)).isEqualTo(PreviewResult.Error("Este archivo no es una copia de Sazón."))
        }
        assertThat(env.snapshot()).isEqualTo(before)
    }

    @Test
    fun copyLocalTo_copiesTheBytesExactly() = runBlocking<Unit> {
        seed()
        env.import(paella, ImportMode.REPLACE_ALL)
        val file = env.localFile("antes-de-importar-2026-11-13-2140.zip")
        val out = ByteArrayOutputStream()

        assertThat(env.manager.copyLocalTo(file.name, out)).isEqualTo(ExportResult.Ok)

        assertThat(out.toByteArray().toList()).isEqualTo(file.readBytes().toList())
        val failing = object : OutputStream() {
            override fun write(b: Int) = throw IOException("disco lleno")
        }
        assertThat(env.manager.copyLocalTo(file.name, failing)).isEqualTo(ExportResult.Error("No se pudo guardar la copia."))
        assertThat(env.manager.copyLocalTo("../fuera.zip", ByteArrayOutputStream())).isEqualTo(ExportResult.Error("No se pudo guardar la copia."))
    }
}
