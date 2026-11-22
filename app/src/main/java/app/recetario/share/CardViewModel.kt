package app.recetario.share

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.recetario.AppContainer
import app.recetario.backup.BackupDocuments
import app.recetario.backup.deleteDocumentIfEmpty
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.recipes.data.PhotoStore
import app.recetario.recipes.data.RecipeRepository
import app.recetario.recipes.detail.formatRecipeAsText
import app.recetario.recipes.list.DEFAULT_EMOJI
import app.recetario.recipes.model.Category
import app.recetario.recipes.model.Recipe
import app.recetario.settings.CardSettings
import app.recetario.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/** Lo que enseña la vista previa (y lo que se exporta tal cual). */
sealed interface CardPreview {
    /** Gira la vista previa cuando cambia: el formato y el diseño (no los interruptores). */
    val flipKey: String

    /** Lo que lee TalkBack: «Vista previa de la ficha: Cuadrada, Clásico». */
    val description: String

    data class Text(val text: String) : CardPreview {
        override val flipKey get() = "TEXT"
        override val description get() = "Vista previa de la ficha: Texto"
    }

    data class Image(val layout: ImageLayout, val look: CardLook, val photo: Bitmap?) : CardPreview {
        override val flipKey get() = "${layout.format}|${layout.design}"
        override val description get() = "Vista previa de la ficha: ${layout.format.label}, ${layout.design.label}"
    }

    data class Pdf(val pages: List<PdfPage>, val look: CardLook, val photo: Bitmap?) : CardPreview {
        val format: CardFormat get() = pages.first().format
        override val flipKey get() = "$format|${look.design}"
        override val description get() =
            "Vista previa de la ficha: ${format.label}, ${look.design.label}, " +
                if (pages.size == 1) "1 página" else "${pages.size} páginas"
    }
}

data class CardUiState(
    val loading: Boolean = true,
    /** La receta ya no existe. */
    val missing: Boolean = false,
    val title: String = "",
    val settings: CardSettings = CardSettings(),
    /** La receta tiene foto (si no, el interruptor «Foto» va apagado). */
    val recipeHasPhoto: Boolean = false,
    val preview: CardPreview? = null,
    /** Generando el archivo: «Preparando…». */
    val busy: Boolean = false,
)

sealed interface CardEvent {
    data class ShareFile(val file: File, val mimeType: String) : CardEvent
    data class ShareText(val text: String) : CardEvent
    data class Message(val text: String) : CardEvent
}

const val CARD_SAVE_FAILED = "No se pudo guardar la ficha."
const val CARD_SAVED = "Ficha guardada."
const val CARD_SHARE_FAILED = "No se pudo preparar la ficha."

/**
 * Pantalla Ficha (diseño de la etapa 2, §7.7): la receta [recipeId] con [servings] porciones como texto, imagen o
 * PDF. Las opciones se recuerdan en `ajustes` (se guardan en [writeScope]: salir no corta la escritura). La
 * maquetación se calcula en `Dispatchers.Default` y los archivos se generan en `Dispatchers.IO`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CardViewModel(
    recipeId: String,
    servings: Int,
    repo: RecipeRepository,
    private val settings: SettingsRepository,
    private val photoStore: PhotoStore,
    val painter: CardPainter,
    private val cacheDir: File,
    private val documents: BackupDocuments,
    private val writeScope: CoroutineScope,
) : ViewModel() {
    private val servings = servings.coerceAtLeast(1)
    private val recipe: Flow<Recipe?> = repo.observeRecipe(recipeId)
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    /** La foto, leída una vez por cada foto de portada (null sin foto o si no se puede leer). */
    private val photo: Flow<Bitmap?> = recipe.map { it?.coverPhoto }.distinctUntilChanged()
        .map { name -> name?.let { loadCardPhoto(photoStore.file(it)) } }
        .flowOn(Dispatchers.IO)

    /** Lo elegido en esta pantalla: manda sobre lo guardado mientras se escribe. */
    private val chosen = MutableStateFlow<CardSettings?>(null)
    private val current: Flow<CardSettings> = combine(settings.cardSettings, chosen) { saved, mine -> mine ?: saved }
    private val theme = MutableStateFlow(ThemeChoice.CALIDO)
    private val busy = MutableStateFlow(false)

    private data class Inputs(val recipe: Recipe?, val emoji: String, val photo: Bitmap?, val settings: CardSettings, val theme: ThemeChoice)

    private val built: Flow<CardUiState> = combine(recipe, repo.observeCategories(), photo, current, theme) { r, cats, p, s, t ->
        Inputs(r, emojiOf(r, cats), p, s, t)
    }.mapLatest { i ->
        val r = i.recipe ?: return@mapLatest CardUiState(loading = false, missing = true)
        CardUiState(
            loading = false,
            title = r.title,
            settings = i.settings,
            recipeHasPhoto = r.coverPhoto != null,
            preview = buildPreview(r, i.emoji, i.photo, i.settings, i.theme),
        )
    }.flowOn(Dispatchers.Default)

    val state: StateFlow<CardUiState> = combine(built.onStart { emit(CardUiState()) }, busy) { s, b -> s.copy(busy = b) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CardUiState())

    private val _events = Channel<CardEvent>(Channel.BUFFERED)
    val events: Flow<CardEvent> = _events.receiveAsFlow()

    private fun emojiOf(r: Recipe?, categories: List<Category>) =
        categories.firstOrNull { it.id == r?.categoryId }?.emoji ?: DEFAULT_EMOJI

    private fun buildPreview(r: Recipe, emoji: String, photo: Bitmap?, s: CardSettings, theme: ThemeChoice): CardPreview {
        if (s.asText) return CardPreview.Text(formatRecipeAsText(r, servings, emoji))
        val o = s.options
        val look = CardLook(o.design, theme)
        // Una foto que no se puede leer cuenta como sin foto.
        val content = cardContent(r, servings, emoji, o.copy(photo = o.photo && photo != null))
        val m = painter.measurer(look)
        val shownPhoto = photo.takeIf { content.hasPhoto }
        return if (o.format.isPdf) {
            CardPreview.Pdf(paginatePdf(content, o.format, o.design, m), look, shownPhoto)
        } else {
            CardPreview.Image(layoutImage(content, o.format, o.design, m), look, shownPhoto)
        }
    }

    fun setTheme(choice: ThemeChoice) {
        theme.value = choice
    }

    private fun update(transform: (CardSettings) -> CardSettings) {
        val base = chosen.value ?: state.value.settings
        val next = transform(base)
        chosen.value = next
        writeScope.launch {
            try {
                settings.setCardSettings(next)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // No poder recordar las opciones no impide compartir.
            }
        }
    }

    fun chooseText() = update { it.copy(asText = true) }
    fun chooseFormat(format: CardFormat) = update { CardSettings(it.options.copy(format = format), asText = false) }
    fun chooseDesign(design: CardDesign) = update { it.copy(options = it.options.copy(design = design)) }
    fun setPhoto(on: Boolean) = update { it.copy(options = it.options.copy(photo = on)) }
    fun setSteps(on: Boolean) = update { it.copy(options = it.options.copy(steps = on)) }
    fun setNotes(on: Boolean) = update { it.copy(options = it.options.copy(notes = on)) }
    fun setFlavor(on: Boolean) = update { it.copy(options = it.options.copy(flavor = on)) }

    /** Nombre del archivo al guardar (`Sazon-crema-de-calabaza.png`); null con «Texto». */
    fun fileName(): String? {
        val s = state.value
        val format = when (val p = s.preview) {
            is CardPreview.Image -> p.layout.format
            is CardPreview.Pdf -> p.format
            else -> return null
        }
        return cardFileName(s.title, format)
    }

    /** «Compartir»: el texto tal cual, o la imagen o el PDF en `cacheDir/fichas/`. Un toque mientras se prepara no hace nada. */
    fun share() {
        val s = state.value
        val preview = s.preview ?: return
        if (!busy.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            val event = try {
                when (preview) {
                    is CardPreview.Text -> CardEvent.ShareText(preview.text)
                    else -> withContext(Dispatchers.IO) {
                        val dir = File(cacheDir, CARD_FILES_DIR).apply { mkdirs() }
                        val format = formatOf(preview)
                        val file = File(dir, cardFileName(s.title, format))
                        file.outputStream().use { render(preview, it) }
                        CardEvent.ShareFile(file, format.mimeType)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("Ficha", "No se pudo preparar la ficha", e)
                CardEvent.Message(CARD_SHARE_FAILED)
            } finally {
                busy.value = false
            }
            _events.trySend(event)
        }
    }

    /**
     * «Guardar» en el documento [uri] que acaba de crear el selector. La ficha se genera antes de abrirlo; si algo
     * falla, se avisa y el documento, si quedó vacío, se borra.
     */
    fun save(uri: String) {
        val preview = state.value.preview
        if (preview == null || preview is CardPreview.Text || !busy.compareAndSet(expect = false, update = true)) {
            writeScope.launch { deleteDocumentIfEmpty(documents, uri) }
            return
        }
        viewModelScope.launch {
            val message = try {
                withContext(Dispatchers.IO) {
                    val bytes = ByteArrayOutputStream().also { render(preview, it) }.toByteArray()
                    documents.openOutput(uri).use { it.write(bytes) }
                }
                CARD_SAVED
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                withContext(Dispatchers.IO) { deleteDocumentIfEmpty(documents, uri) }
                CARD_SAVE_FAILED
            } finally {
                busy.value = false
            }
            _events.trySend(CardEvent.Message(message))
        }
    }

    private fun formatOf(p: CardPreview): CardFormat = when (p) {
        is CardPreview.Image -> p.layout.format
        is CardPreview.Pdf -> p.format
        is CardPreview.Text -> error("El texto no es un archivo")
    }

    private fun render(p: CardPreview, out: java.io.OutputStream) {
        when (p) {
            is CardPreview.Image -> {
                val bitmap = renderCardImage(p.layout, p.look, painter, p.photo)
                try {
                    writePng(bitmap, out)
                } finally {
                    bitmap.recycle()
                }
            }
            is CardPreview.Pdf -> writeCardPdf(p.pages, p.look, painter, p.photo, out)
            is CardPreview.Text -> out.write(p.text.toByteArray())
        }
    }

    companion object {
        fun factory(container: AppContainer, painter: CardPainter, cacheDir: File, recipeId: String, servings: Int): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    CardViewModel(
                        recipeId = recipeId,
                        servings = servings,
                        repo = container.recipeRepository,
                        settings = container.settingsRepository,
                        photoStore = container.photoStore,
                        painter = painter,
                        cacheDir = cacheDir,
                        documents = container.backupDocuments,
                        writeScope = container.applicationScope,
                    )
                }
            }
    }
}
