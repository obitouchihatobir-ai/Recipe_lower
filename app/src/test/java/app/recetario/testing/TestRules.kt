package app.recetario.testing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.ExternalResource
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Cierra la base de datos (o lo que haga [close]) **después** de las reglas que van dentro de esta. Se pone con
 * `@get:Rule(order = 0)` y la regla de Compose con `order = 1`: así la composición se desecha antes de cerrar Room.
 * Con un `@After`, el cierre llega antes y una consulta tardía de un `Flow` falla con «connection pool has been
 * closed» (CLAUDE.md, problemas conocidos). Si la prueba falló antes de crear el contenedor (`lateinit` sin valor),
 * no hace nada.
 */
class CloseDatabaseRule(private val close: () -> Unit) : ExternalResource() {
    override fun after() {
        try {
            close()
        } catch (_: UninitializedPropertyAccessException) {
            // La prueba no llegó a crear la base de datos.
        }
    }
}

/**
 * Pone [dispatcher] como `Dispatchers.Main` y lo deshace siempre al terminar. Los ViewModel creados con [track] se
 * desechan (`onCleared` y cancelar `viewModelScope`) y se espera a que acaben sus corrutinas **antes** de
 * `resetMain`: una corrutina que vuelve de `Dispatchers.IO` lee `Dispatchers.Main`, y si lo hace mientras se cambia,
 * `TestMainDispatcher` lanza «Dispatchers.Main is used concurrently with setting it» (`IllegalStateException`).
 * Con `CloseDatabaseRule` por fuera (`order` menor), la base de datos se cierra cuando ya no queda ninguna.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) : TestWatcher() {
    private val store = ViewModelStore()
    private val jobs = mutableListOf<Job>()

    /** Anota [vm] para desecharlo al terminar la prueba; lo devuelve tal cual. */
    fun <T : ViewModel> track(vm: T): T {
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <V : ViewModel> create(modelClass: Class<V>): V = vm as V
        }
        ViewModelProvider(store, factory)["vm-${jobs.size}", vm.javaClass]
        jobs += vm.viewModelScope.coroutineContext.job
        return vm
    }

    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)

    override fun finished(description: Description) {
        try {
            store.clear()
            awaitJobs()
        } finally {
            Dispatchers.resetMain()
        }
    }

    /** Espera a que acaben las corrutinas canceladas (las del despachador de prueba avanzan aquí). */
    private fun awaitJobs() {
        val deadline = System.currentTimeMillis() + 10_000
        while (jobs.any { !it.isCompleted }) {
            dispatcher.scheduler.advanceUntilIdle()
            check(System.currentTimeMillis() < deadline) { "Una corrutina de un ViewModel sigue en marcha al terminar la prueba." }
            Thread.sleep(5)
        }
    }
}
