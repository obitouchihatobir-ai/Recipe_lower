package app.recetario.pairing

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.AppContainer
import app.recetario.testing.installTestContainer
import app.recetario.testing.testClock
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PairingRepositoryTest {
    private lateinit var container: AppContainer
    private val repo get() = container.pairingRepository
    private val dao get() = container.database.pairingRatingDao()

    @Before
    fun setUp() {
        container = installTestContainer()
        seedPairingRecipes(container)
    }

    @After
    fun tearDown() = container.database.close()

    private fun mole() = pairingData(container).find("RECIPE", "mole")!!
    private fun jamaica() = pairingData(container).find("CATALOG", "cat-agua-jamaica")!!

    @Test
    fun rate_storesOrderedPairKey() = runBlocking<Unit> {
        val mole = mole()
        val jamaica = jamaica()
        assertThat(mole.role).isEqualTo(PairRole.FOOD)
        assertThat(jamaica.role).isEqualTo(PairRole.DRINK)

        repo.rate(mole, jamaica, true)
        val row = dao.getAll().single()
        assertThat(row.pairKey).isEqualTo("CATALOG:cat-agua-jamaica|RECIPE:mole")
        assertThat(row.aKind).isEqualTo("CATALOG")
        assertThat(row.aId).isEqualTo("cat-agua-jamaica")
        assertThat(row.bKind).isEqualTo("RECIPE")
        assertThat(row.bId).isEqualTo("mole")
        assertThat(row.liked).isTrue()
        assertThat(row.ratedAt).isEqualTo(testClock.instant())
        assertThat(row.pairKey).isEqualTo(pairKey(mole, jamaica))

        // Al revés es la misma pareja: se sustituye, no se duplica.
        repo.rate(jamaica, mole, false)
        assertThat(dao.getAll().single().liked).isFalse()
        assertThat(repo.observeRatings().first()).containsExactly("CATALOG:cat-agua-jamaica|RECIPE:mole", false)

        val history = repo.observeHistory().first().single()
        assertThat(history.a.name).isEqualTo("Agua de jamaica")
        assertThat(history.b.name).isEqualTo("Mole poblano")
        assertThat(history.liked).isFalse()

        // Dos recetas propias también se valoran.
        val limonada = pairingData(container).find("RECIPE", "limonada")!!
        repo.rate(limonada, mole, true)
        assertThat(dao.getAll().map { it.pairKey }).contains("RECIPE:limonada|RECIPE:mole")
        // La más reciente primero.
        assertThat(repo.observeHistory().first().map { it.pairKey })
            .containsExactly("RECIPE:limonada|RECIPE:mole", "CATALOG:cat-agua-jamaica|RECIPE:mole").inOrder()
    }

    @Test
    fun rate_nullRemoves() = runBlocking<Unit> {
        repo.rate(mole(), jamaica(), false)
        assertThat(dao.getAll()).hasSize(1)
        repo.rate(jamaica(), mole(), null)
        assertThat(dao.getAll()).isEmpty()
        assertThat(repo.observeRatings().first()).isEmpty()
        assertThat(repo.observeHistory().first()).isEmpty()
        // Quitar lo que no está no falla.
        repo.rate(mole(), jamaica(), null)
    }

    @Test
    fun rate_catalogCatalog_rejected() {
        val data = pairingData(container)
        val jamaica = data.find("CATALOG", "cat-agua-jamaica")!!
        val guacamole = data.find("CATALOG", "cat-guacamole")!!
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.rate(jamaica, guacamole, true) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.rate(jamaica, guacamole, null) } }
        // Un tipo de lado desconocido tampoco (la copia de seguridad rechazaría el archivo entero).
        val odd = jamaica.copy(kind = "OTRO")
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.rate(odd, mole(), true) } }
        runBlocking { assertThat(dao.getAll()).isEmpty() }
    }

    @Test
    fun deletingARecipe_dropsItsRatingsFromTheHistory() = runBlocking<Unit> {
        repo.rate(mole(), jamaica(), true)
        container.recipeRepository.delete("mole")
        assertThat(repo.observeHistory().first()).isEmpty()
        assertThat(dao.getAll()).isEmpty()
    }
}
