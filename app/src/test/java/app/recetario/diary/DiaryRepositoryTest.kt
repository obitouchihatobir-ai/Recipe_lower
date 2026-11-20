package app.recetario.diary

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.core.data.AppDatabase
import app.recetario.core.data.DefaultCategories
import app.recetario.pairing.data.PairingRatingEntity
import app.recetario.recipes.data.PhotoStore
import app.recetario.recipes.data.RecipeRepository
import app.recetario.recipes.data.RoomRecipeRepository
import app.recetario.testing.sampleRecipe
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class DiaryRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val now = Instant.parse("2026-11-13T10:00:00Z")
    private lateinit var db: AppDatabase
    private lateinit var photos: PhotoStore
    private lateinit var recipes: RecipeRepository
    private lateinit var diary: DiaryRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(DefaultCategories.callback)
            .allowMainThreadQueries()
            .build()
        photos = PhotoStore(tmp.newFolder("photos"))
        val clock = Clock.fixed(now, ZoneOffset.UTC)
        recipes = RoomRecipeRepository(db, photos, clock)
        diary = RoomDiaryRepository(db, photos, clock)
    }

    @After
    fun tearDown() = db.close()

    private fun entry(
        id: String = "e1",
        recipeId: String? = "r1",
        date: LocalDate = LocalDate.of(2026, 11, 10),
        rating: Int? = 4,
        photos: List<String> = emptyList(),
    ) = CookLogEntry(
        id = id, recipeId = recipeId, recipeTitle = "Crema de calabaza", date = date, rating = rating,
        note = "Le faltó sal", photos = photos,
        createdAt = Instant.parse("2026-11-10T20:00:00Z"), updatedAt = Instant.EPOCH,
    )

    /** Crea un archivo de foto de verdad en el PhotoStore. */
    private fun photoFile(name: String): String {
        photos.dir.mkdirs()
        photos.file(name).writeBytes(byteArrayOf(1, 2, 3))
        return name
    }

    @Test
    fun save_thenObserve() = runBlocking<Unit> {
        recipes.save(sampleRecipe("r1", "Crema de calabaza"))
        val original = entry(photos = listOf("b.jpg", "a.jpg"))
        diary.save(original)

        val loaded = diary.observeEntries().first().single()

        // save() fija updatedAt con el reloj; lo demás vuelve igual, con las fotos en su orden.
        assertThat(loaded).isEqualTo(original.copy(updatedAt = now))
        assertThat(diary.observeStats().first().getValue("r1"))
            .isEqualTo(CookStats(count = 1, lastDate = LocalDate.of(2026, 11, 10), averageRating = 4.0))
    }

    @Test
    fun save_again_replacesPhotos_andDeletesTheOnesNoLongerUsed() = runBlocking<Unit> {
        recipes.save(sampleRecipe("r1", "Crema de calabaza"))
        diary.save(entry(photos = listOf(photoFile("old.jpg"), photoFile("keep.jpg"))))

        diary.save(entry(photos = listOf("keep.jpg"), rating = 5))

        val loaded = diary.observeEntries().first().single()
        assertThat(loaded.photos).containsExactly("keep.jpg")
        assertThat(loaded.rating).isEqualTo(5)
        assertThat(photos.file("old.jpg").exists()).isFalse()
        assertThat(photos.file("keep.jpg").exists()).isTrue()
    }

    @Test
    fun save_forARecipeThatNoLongerExists_isKeptWithoutRecipe() = runBlocking<Unit> {
        diary.save(entry(recipeId = "borrada"))
        val loaded = diary.observeEntries().first().single()
        assertThat(loaded.recipeId).isNull()
        assertThat(loaded.recipeTitle).isEqualTo("Crema de calabaza")
    }

    @Test
    fun deleteRecipe_keepsLogEntriesAndPhotos() = runBlocking<Unit> {
        recipes.save(sampleRecipe("r1", "Crema de calabaza"))
        diary.save(entry(photos = listOf(photoFile("log.jpg"))))

        recipes.delete("r1")

        val loaded = diary.observeEntries().first().single()
        assertThat(loaded.recipeId).isNull()
        assertThat(loaded.recipeTitle).isEqualTo("Crema de calabaza")
        assertThat(loaded.photos).containsExactly("log.jpg")
        assertThat(photos.file("log.jpg").exists()).isTrue()
        // Sin receta, la entrada ya no cuenta en las estadísticas.
        assertThat(diary.observeStats().first()).isEmpty()
    }

    @Test
    fun photoReferenced_byLog() = runBlocking<Unit> {
        recipes.save(sampleRecipe("r1", "Crema de calabaza").copy(coverPhoto = photoFile("shared.jpg")))
        diary.save(entry(photos = listOf("shared.jpg", photoFile("only-log.jpg"))))
        assertThat(recipes.isPhotoReferenced("only-log.jpg")).isTrue()

        // La receta deja de usar la foto, pero el diario la sigue usando: no se borra.
        recipes.save(sampleRecipe("r1", "Crema de calabaza"))
        assertThat(photos.file("shared.jpg").exists()).isTrue()
        assertThat(recipes.isPhotoReferenced("shared.jpg")).isTrue()

        // Borrar la entrada no toca los archivos (puede haber «Deshacer»)…
        val removed = diary.delete("e1")!!
        assertThat(removed.photos).containsExactly("shared.jpg", "only-log.jpg").inOrder()
        assertThat(photos.file("only-log.jpg").exists()).isTrue()
        assertThat(recipes.isPhotoReferenced("only-log.jpg")).isFalse()

        // …hasta que caduca: entonces se borran los que ya no usa nadie.
        diary.deletePhotosIfUnused(removed.photos)
        assertThat(photos.file("only-log.jpg").exists()).isFalse()
        assertThat(photos.file("shared.jpg").exists()).isFalse()
    }

    @Test
    fun delete_thenRestore_bringsTheEntryBackAsItWas() = runBlocking<Unit> {
        recipes.save(sampleRecipe("r1", "Crema de calabaza"))
        diary.save(entry(photos = listOf(photoFile("log.jpg"))))
        val saved = diary.observeEntries().first().single()

        val removed = diary.delete("e1")!!
        assertThat(diary.observeEntries().first()).isEmpty()
        assertThat(diary.delete("e1")).isNull()

        diary.restore(removed)
        assertThat(diary.observeEntries().first().single()).isEqualTo(saved)
    }

    @Test
    fun deleteRecipe_removesItsPairingRatings() = runBlocking<Unit> {
        recipes.save(sampleRecipe("r1", "Crema de calabaza"))
        recipes.save(sampleRecipe("r2", "Tarta de queso"))
        fun rating(aKind: String, aId: String, bKind: String, bId: String) =
            PairingRatingEntity("$aKind:$aId|$bKind:$bId", aKind, aId, bKind, bId, liked = true, ratedAt = now)
        db.pairingRatingDao().upsert(
            listOf(
                rating("CATALOG", "vino-blanco", "RECIPE", "r1"),
                rating("RECIPE", "r1", "RECIPE", "r2"),
                rating("CATALOG", "cafe", "RECIPE", "r2"),
                // Un elemento del catálogo con el mismo id que la receta no es la receta.
                rating("CATALOG", "r1", "RECIPE", "r2"),
            ),
        )

        recipes.delete("r1")

        assertThat(db.pairingRatingDao().getAll().map { it.pairKey })
            .containsExactly("CATALOG:cafe|RECIPE:r2", "CATALOG:r1|RECIPE:r2")
    }

    @Test
    fun editor_importPhoto_revokedUri_showsMessageWithoutCrashing() {
        val failures = java.util.concurrent.CopyOnWriteArrayList<Throwable>()
        val scope = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default +
                kotlinx.coroutines.CoroutineExceptionHandler { _, e -> failures += e },
        )
        val editor = LogEntryEditor(diary, photos, Clock.fixed(now, ZoneOffset.UTC), scope, scope)
        editor.startNew("r1", "Crema de calabaza")
        // Android quita el permiso de la URI a mitad de lectura: el proveedor lanza SecurityException.
        val revoked = object : java.io.InputStream() {
            override fun read(): Int = throw SecurityException("Permission Denial")
        }

        editor.importPhoto(revoked)

        val deadline = System.currentTimeMillis() + 5_000
        while (editor.draft.value?.error == null && failures.isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
        assertThat(failures).isEmpty()
        assertThat(editor.draft.value?.error).isEqualTo(LogEntryEditor.PHOTO_FAILED)
        assertThat(editor.draft.value?.photos).isEmpty()
    }
}
