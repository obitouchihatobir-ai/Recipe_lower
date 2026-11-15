package app.recetario.cooking

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Un temporizador del modo cocina. Los instantes son del reloj monótono del sistema
 * (`SystemClock.elapsedRealtime()`), que sigue contando con la pantalla apagada.
 * - En marcha: [endsAtElapsedMs] dice cuándo termina.
 * - En pausa: [remainingMsWhenPaused] guarda lo que le quedaba.
 * - Terminado: [finished]; sigue en la lista (la pantalla muestra «¡Tiempo!») hasta que se cancela.
 */
data class CookingTimer(
    val id: String,
    val label: String,
    val totalSeconds: Int,
    val endsAtElapsedMs: Long?,
    val remainingMsWhenPaused: Long?,
    val finished: Boolean,
) {
    val isRunning: Boolean get() = !finished && endsAtElapsedMs != null
    val isPaused: Boolean get() = !finished && endsAtElapsedMs == null && remainingMsWhenPaused != null

    /** Milisegundos que le quedan en [now] (nunca negativos). */
    fun remainingMs(now: Long): Long = when {
        finished -> 0
        endsAtElapsedMs != null -> (endsAtElapsedMs - now).coerceAtLeast(0)
        else -> (remainingMsWhenPaused ?: totalSeconds * 1000L).coerceAtLeast(0)
    }
}

/**
 * Los temporizadores de toda la app (hay uno solo, en el contenedor de dependencias): así sobreviven al giro
 * y a salir y volver al modo cocina. Es seguro llamarlo desde varios hilos.
 */
class TimerManager(private val elapsedRealtime: () -> Long) {
    private val lock = Any()
    private val _timers = MutableStateFlow<List<CookingTimer>>(emptyList())
    private val _finished = MutableSharedFlow<CookingTimer>(extraBufferCapacity = 64)

    /** Todos los temporizadores, en el orden en que se crearon. */
    val timers: StateFlow<List<CookingTimer>> = _timers.asStateFlow()

    /** Cada temporizador que termina, una sola vez (lo emite [tick]). */
    val finishedEvents: SharedFlow<CookingTimer> = _finished.asSharedFlow()

    /** La hora del reloj inyectado. */
    fun now(): Long = elapsedRealtime()

    /**
     * Arranca un temporizador de [seconds] segundos y devuelve su id. Con el [id] de uno que ya existe
     * (el modo cocina usa el id del paso) lo vuelve a empezar en el mismo sitio.
     */
    fun start(label: String, seconds: Int, id: String = UUID.randomUUID().toString()): String {
        require(seconds > 0) { "Un temporizador necesita al menos un segundo" }
        val timer = CookingTimer(id, label, seconds, now() + seconds * 1000L, remainingMsWhenPaused = null, finished = false)
        synchronized(lock) {
            val list = _timers.value
            _timers.value = if (list.any { it.id == id }) list.map { if (it.id == id) timer else it } else list + timer
        }
        return id
    }

    fun pause(id: String) {
        val now = now()
        val finished = synchronized(lock) {
            // Si ya había llegado a cero, termina en vez de quedarse en pausa a 0:00.
            val done = finishDueLocked(now)
            update(id) { t -> if (t.isRunning) t.copy(endsAtElapsedMs = null, remainingMsWhenPaused = t.remainingMs(now)) else t }
            done
        }
        finished.forEach { _finished.tryEmit(it) }
    }

    fun resume(id: String) {
        val now = now()
        synchronized(lock) {
            update(id) { t -> if (t.isPaused) t.copy(endsAtElapsedMs = now + t.remainingMs(now), remainingMsWhenPaused = null) else t }
        }
    }

    /** Suma un minuto. A uno terminado le da un minuto más en marcha. */
    fun addMinute(id: String) {
        val now = now()
        synchronized(lock) {
            update(id) { t ->
                when {
                    t.finished -> t.copy(totalSeconds = t.totalSeconds + 60, endsAtElapsedMs = now + MINUTE_MS, finished = false)
                    t.isRunning -> t.copy(totalSeconds = t.totalSeconds + 60, endsAtElapsedMs = t.endsAtElapsedMs!! + MINUTE_MS)
                    else -> t.copy(totalSeconds = t.totalSeconds + 60, remainingMsWhenPaused = t.remainingMs(now) + MINUTE_MS)
                }
            }
        }
    }

    /** Lo quita de la lista (también sirve para apagar el aviso de uno terminado). */
    fun cancel(id: String) {
        synchronized(lock) { _timers.value = _timers.value.filterNot { it.id == id } }
    }

    fun remainingMs(id: String, now: Long): Long = _timers.value.firstOrNull { it.id == id }?.remainingMs(now) ?: 0

    /** Marca como terminados los que llegaron a cero en [now] y emite cada uno en [finishedEvents]. */
    fun tick(now: Long) {
        val finished = synchronized(lock) { finishDueLocked(now) }
        finished.forEach { _finished.tryEmit(it) }
    }

    private fun finishDueLocked(now: Long): List<CookingTimer> {
        val list = _timers.value
        val due = list.filter { it.isRunning && it.endsAtElapsedMs!! <= now }.map { it.id }.toSet()
        if (due.isEmpty()) return emptyList()
        val updated = list.map {
            if (it.id in due) it.copy(endsAtElapsedMs = null, remainingMsWhenPaused = null, finished = true) else it
        }
        _timers.value = updated
        return updated.filter { it.id in due }
    }

    private inline fun update(id: String, change: (CookingTimer) -> CookingTimer) {
        _timers.value = _timers.value.map { if (it.id == id) change(it) else it }
    }

    private companion object {
        const val MINUTE_MS = 60_000L
    }
}

/**
 * Llama a [TimerManager.tick] cada [periodMs] mientras haya algún temporizador en marcha; si no hay
 * ninguno (o todos están en pausa o terminados) no hace nada.
 */
fun CoroutineScope.launchTimerTicker(manager: TimerManager, periodMs: Long = 250): Job = launch {
    manager.timers
        .map { list -> list.any { it.isRunning } }
        .distinctUntilChanged()
        .collectLatest { running ->
            if (!running) return@collectLatest
            while (true) {
                manager.tick(manager.now())
                delay(periodMs)
            }
        }
}
