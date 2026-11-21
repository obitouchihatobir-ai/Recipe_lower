package app.recetario.pairing

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.AppContainer
import app.recetario.testing.installTestContainer
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

/** La pestaña Maridaje sin pantalla: escribir en el buscador no recalcula las sugerencias del elegido. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class PairingViewModelTest {
    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        container = installTestContainer()
        seedPairingRecipes(container)
    }

    @After
    fun tearDown() {
        container.database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun typingInTheSearch_doesNotRecomputeSuggestions() {
        val data = pairingData(container)
        // Los datos no cambian durante la prueba: solo cambia lo que se escribe.
        val repository = object : PairingRepository by container.pairingRepository {
            override fun observeData(): Flow<PairingData> = flowOf(data)
        }
        val calls = AtomicInteger()
        val vm = PairingViewModel(
            repository,
            SavedStateHandle(mapOf(PairingViewModel.SUBJECT_ARG to "RECIPE:mole")),
            suggest = { s, all, r, limit, w -> calls.incrementAndGet(); suggestPairings(s, all, r, limit, w) },
        )
        val loaded = runBlocking { withTimeout(10_000) { vm.state.first { it.subject != null } } }
        assertThat(loaded.own.map { it.item.id }).contains("limonada")
        val before = calls.get()
        assertThat(before).isAtLeast(1)

        for (q in listOf("l", "li", "lim")) {
            vm.setQuery(q)
            runBlocking { withTimeout(10_000) { vm.state.first { it.query == q } } }
        }
        assertThat(vm.state.value.matches.map { it.id }).contains("limonada")
        assertThat(vm.state.value.own.map { it.item.id }).contains("limonada")
        assertThat(calls.get()).isEqualTo(before)
    }
}
