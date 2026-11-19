package app.recetario.testing

/** TEMPORAL: si la prueba sigue viva a los [seconds] s, imprime la pila de todos los hilos. */
fun startWatchdog(name: String, seconds: Long): Thread = Thread {
    try {
        Thread.sleep(seconds * 1000)
    } catch (_: InterruptedException) {
        return@Thread
    }
    System.err.println("=== WATCHDOG $name: pilas de todos los hilos ===")
    for ((t, stack) in Thread.getAllStackTraces()) {
        System.err.println("--- ${t.name} (${t.state})")
        stack.take(60).forEach { System.err.println("    at $it") }
    }
    System.err.println("=== FIN WATCHDOG ===")
}.apply { isDaemon = true; start() }
