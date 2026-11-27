package app.recetario.cooking.handsfree

/**
 * Gesto de manos libres con el sensor de proximidad: pasar la mano (lejos → cerca → lejos en [maxNearMs] o menos)
 * equivale a «Hecho ✓». Después de un gesto hay [cooldownMs] de pausa. Si se queda cerca más de [heldMs] (el
 * teléfono boca abajo o en un bolsillo) no cuenta, y la pausa empieza al destaparlo. Un pase solo cuenta si antes
 * se leyó «lejos» (encender el sensor ya tapado no es un gesto). Las lecturas con una hora anterior se descartan.
 */
class WaveDetector(
    private val cooldownMs: Long = 1500,
    private val maxNearMs: Long = 1500,
    private val heldMs: Long = 2000,
) {
    private var lastAt: Long? = null
    private var sawFar = false
    private var nearSince: Long? = null
    private var blockedUntil: Long = Long.MIN_VALUE

    /** Una lectura del sensor; true si con ella se completa un gesto. */
    fun onProximity(near: Boolean, atMs: Long): Boolean {
        lastAt?.let { if (atMs < it) return false }
        lastAt = atMs
        if (near) {
            if (sawFar && nearSince == null) nearSince = atMs
            return false
        }
        val since = nearSince
        sawFar = true
        nearSince = null
        if (since == null) return false
        val covered = atMs - since
        if (covered > heldMs) {
            blockedUntil = atMs + cooldownMs
            return false
        }
        if (covered > maxNearMs || atMs < blockedUntil) return false
        blockedUntil = atMs + cooldownMs
        return true
    }
}
