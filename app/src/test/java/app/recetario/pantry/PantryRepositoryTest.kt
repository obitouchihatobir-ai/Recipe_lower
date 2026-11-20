package app.recetario.pantry

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.core.data.AppDatabase
import app.recetario.core.data.DefaultCategories
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class PantryRepositoryTest {
    private val now = Instant.parse("2026-11-13T10:00:00Z")
    private lateinit var db: AppDatabase
    private lateinit var repo: PantryRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(DefaultCategories.callback)
            .allowMainThreadQueries()
            .build()
        repo = RoomPantryRepository(db, Clock.fixed(now, ZoneOffset.UTC))
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun add_dedupByNormalized() = runBlocking {
        assertThat(repo.add("Limón")).isTrue()
        // Mismo nombre salvo tildes, mayúsculas, espacios o plural: ya estaba (Room @Upsert lo habría ignorado en silencio).
        assertThat(repo.add("  limon ")).isFalse()
        assertThat(repo.add("LIMONES")).isFalse()
        assertThat(repo.add("Tomate")).isTrue()
        assertThat(repo.add("   ")).isFalse()

        val items = repo.observe().first()
        assertThat(items.map { it.name }).containsExactly("Limón", "Tomate").inOrder()
        assertThat(items.first().normalized).isEqualTo("limon")
        assertThat(items.first().addedAt).isEqualTo(now)
    }

    @Test
    fun observe_isAlphabetical() = runBlocking {
        repo.add("Zanahoria")
        repo.add("arroz")
        repo.add("Ébano")
        assertThat(repo.observe().first().map { it.name }).containsExactly("arroz", "Ébano", "Zanahoria").inOrder()
    }

    @Test
    fun delete_thenRestore_bringsBackTheSameItem() = runBlocking {
        repo.add("Arroz")
        val item = repo.observe().first().single()
        val removed = repo.delete(item.id)
        assertThat(removed).isEqualTo(item)
        assertThat(repo.observe().first()).isEmpty()
        assertThat(repo.delete(item.id)).isNull()

        repo.restore(item)
        assertThat(repo.observe().first()).containsExactly(item)
    }

    @Test
    fun restore_whenTheSameNameWasAddedMeanwhile_doesNotDuplicateNorFail() = runBlocking {
        repo.add("Arroz")
        val item = repo.observe().first().single()
        repo.delete(item.id)
        assertThat(repo.add("arroz")).isTrue()

        repo.restore(item)
        repo.restore(item)

        assertThat(repo.observe().first().map { it.normalized }).containsExactly("arroz")
    }
}
