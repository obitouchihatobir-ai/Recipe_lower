package app.recetario.share

import android.graphics.BitmapFactory
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.AppContainer
import app.recetario.backup.FakeDocuments
import app.recetario.recipes.quantity.MeasureUnit
import app.recetario.settings.CardSettings
import app.recetario.testing.CloseDatabaseRule
import app.recetario.testing.MainDispatcherRule
import app.recetario.testing.ingredient
import app.recetario.testing.installTestContainer
import app.recetario.testing.sampleRecipe
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CardViewModelTest {
    @get:Rule(order = 0)
    val closeDatabase = CloseDatabaseRule { container.database.close() }

    @get:Rule(order = 1)
    val main = MainDispatcherRule()

    private lateinit var container: AppContainer
    private val documents = FakeDocuments()
    private val app = RuntimeEnvironment.getApplication()
    private val uri = "content://prueba/Sazon-crema-de-calabaza.png"

    @Before
    fun setUp() {
        container = installTestContainer(documents = documents)
        runBlocking {
            container.recipeRepository.save(
                sampleRecipe("r1", "Crema de calabaza", ingredients = listOf(ingredient("i1", "Calabaza", 800.0, MeasureUnit.GRAM))),
            )
        }
    }

    private fun vm(
        handle: SavedStateHandle = SavedStateHandle(),
        recipeId: String = "r1",
        pdf: () -> CardPdfDocument = { FakePdfDocument() },
    ) = main.track(
        CardViewModel(
            recipeId, 4, container.recipeRepository, container.settingsRepository, container.photoStore,
            CardPainter(CardFonts(app), newPdf = pdf), app.cacheDir, documents, container.applicationScope, handle,
        ),
    )

    private fun CardViewModel.loaded(): CardUiState = runBlocking { withTimeout(15_000) { state.first { !it.loading } } }

    private fun CardViewModel.nextMessage(): String =
        runBlocking { withTimeout(30_000) { (events.first { it is CardEvent.Message } as CardEvent.Message).text } }

    private fun CardViewModel.nextEvent(): CardEvent = runBlocking { withTimeout(30_000) { events.first() } }

    private fun written(): ByteArray? = documents.files[uri]?.takeIf { it.isNotEmpty() }

    private fun size(bytes: ByteArray) = BitmapFactory.decodeByteArray(bytes, 0, bytes.size).let { it.width to it.height }

    /** Nadie mira el estado (la pantalla está tapada por el selector): «Guardar» igualmente escribe la ficha. */
    @Test
    fun save_withoutSubscribers_writesTheFile() {
        val vm = vm()
        vm.save(uri)
        assertThat(vm.nextMessage()).isEqualTo(CARD_SAVED)
        assertThat(size(checkNotNull(written()))).isEqualTo(1080 to 1080)
        assertThat(documents.deleted).isEmpty()
    }

    /**
     * Android cierra el proceso mientras el selector de «Guardar» está abierto: al volver, un ViewModel nuevo (con
     * el mismo `SavedStateHandle`) recibe el documento antes de tener vista previa. Escribe lo elegido.
     */
    @Test
    fun save_afterProcessDeath_writesTheChosenCard() {
        val handle = SavedStateHandle()
        val first = vm(handle)
        first.loaded()
        first.chooseFormat(CardFormat.STORY)
        // Lo guardado en ajustes se pierde (por ejemplo, no llegó a escribirse): solo queda el SavedStateHandle.
        runBlocking {
            withTimeout(5_000) { container.settingsRepository.cardSettings.first { it.options.format == CardFormat.STORY } }
            container.settingsRepository.setCardSettings(CardSettings())
        }

        val restored = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })
        val second = vm(restored)
        assertThat(second.state.value.preview).isNull()
        second.save(uri)
        assertThat(second.nextMessage()).isEqualTo(CARD_SAVED)
        assertThat(size(checkNotNull(written()))).isEqualTo(1080 to 1920)
    }

    @Test
    fun save_recipeGone_failsAndDeletesTheEmptyDocument() {
        val vm = vm(recipeId = "no-existe")
        vm.save(uri)
        assertThat(vm.nextMessage()).isEqualTo(CARD_SAVE_FAILED)
        assertThat(documents.deleted).contains(uri)
    }

    @Test
    fun pdfGarbage_save_isAFailure() {
        runBlocking { container.settingsRepository.setCardSettings(CardSettings(CardOptions(format = CardFormat.PDF_A4))) }
        val vm = vm(pdf = { FakePdfDocument(garbage = true) })
        assertThat(vm.loaded().preview).isInstanceOf(CardPreview.Pdf::class.java)
        vm.save(uri)
        assertThat(vm.nextMessage()).isEqualTo(CARD_SAVE_FAILED)
        assertThat(documents.deleted).contains(uri)
        assertThat(documents.openedForWriting).doesNotContain(uri)
    }

    @Test
    fun pdfGarbage_share_isAFailureAndLeavesNoFile() {
        runBlocking { container.settingsRepository.setCardSettings(CardSettings(CardOptions(format = CardFormat.PDF_CARD))) }
        val vm = vm(pdf = { FakePdfDocument(garbage = true) })
        vm.loaded()
        val before = File(app.cacheDir, CARD_FILES_DIR).walkTopDown().filter { it.isFile }.toList()
        vm.share()
        assertThat(vm.nextMessage()).isEqualTo(CARD_SHARE_FAILED)
        assertThat(File(app.cacheDir, CARD_FILES_DIR).walkTopDown().filter { it.isFile }.toList()).isEqualTo(before)
    }

    @Test
    fun pdf_share_writesAPdf() {
        runBlocking { container.settingsRepository.setCardSettings(CardSettings(CardOptions(format = CardFormat.PDF_A4))) }
        val vm = vm()
        vm.loaded()
        vm.share()
        val e = vm.nextEvent() as CardEvent.ShareFile
        assertThat(e.mimeType).isEqualTo("application/pdf")
        assertThat(isPdf(e.file.readBytes())).isTrue()
    }

    /** Cada vez que se comparte, un archivo nuevo: una app que aún lee el anterior no lo ve cambiar. */
    @Test
    fun share_usesAFreshFileEachTime() {
        val vm = vm()
        vm.loaded()
        vm.share()
        val first = (vm.nextEvent() as CardEvent.ShareFile).file
        val firstBytes = first.readBytes()
        vm.share()
        val second = (vm.nextEvent() as CardEvent.ShareFile).file
        assertThat(second.name).isEqualTo("Sazon-crema-de-calabaza.png")
        assertThat(first.name).isEqualTo(second.name)
        assertThat(second.path).isNotEqualTo(first.path)
        assertThat(first.readBytes()).isEqualTo(firstBytes)
    }

    /** Un toque antes de que lleguen las opciones guardadas no las pisa con las de serie. */
    @Test
    fun options_tapsBeforeLoad_neverOverwriteSaved() {
        val saved = CardSettings(CardOptions(format = CardFormat.STORY, design = CardDesign.MINIMAL, steps = false))
        runBlocking { container.settingsRepository.setCardSettings(saved) }
        val vm = vm()
        vm.setFlavor(false)
        val s = vm.loaded()
        assertThat(s.settings.options.format).isEqualTo(CardFormat.STORY)
        assertThat(s.settings.options.design).isEqualTo(CardDesign.MINIMAL)
        assertThat(s.settings.options.steps).isFalse()
        Thread.sleep(200)
        val stored = runBlocking { container.settingsRepository.cardSettings.first() }
        assertThat(stored.options.format).isEqualTo(CardFormat.STORY)
        assertThat(stored.options.design).isEqualTo(CardDesign.MINIMAL)
    }

    @Test
    fun unreadablePhoto_isReported() {
        runBlocking {
            container.recipeRepository.save(sampleRecipe("r2", "Sin foto legible").copy(coverPhoto = "no-existe.jpg"))
        }
        val s = vm(recipeId = "r2").loaded()
        assertThat(s.photoUnreadable).isTrue()
        assertThat(s.recipeHasPhoto).isTrue()
        assertThat(vm().loaded().photoUnreadable).isFalse()
    }
}
