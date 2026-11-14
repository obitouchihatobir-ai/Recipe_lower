package app.recetario.cooking

/**
 * Paso que se abre al marcar [current] como hecho: el siguiente sin hacer; si no queda ninguno detrás, el
 * primero sin hacer desde el principio; y si están todos hechos, [count] (la receta está lista).
 */
fun nextPendingStep(current: Int, done: Set<Int>, count: Int): Int =
    (current + 1 until count).firstOrNull { it !in done }
        ?: (0 until count).firstOrNull { it !in done }
        ?: count
