package app.recetario.cooking

private const val LABEL_CHARS = 30

/**
 * Nombre de un temporizador: «Paso 2: Sofríe la cebolla y el ajo…». El texto del paso se corta hacia los
 * 30 caracteres, en un espacio si lo hay, y sin la puntuación que quede al final.
 */
fun timerLabel(stepNumber: Int, stepText: String): String {
    val text = stepText.trim().replace(Regex("\\s+"), " ")
    if (text.isEmpty()) return "Paso $stepNumber"
    if (text.length <= LABEL_CHARS) return "Paso $stepNumber: $text"
    val prefix = text.take(LABEL_CHARS)
    val cut = when {
        text[LABEL_CHARS] == ' ' -> prefix
        prefix.lastIndexOf(' ') >= LABEL_CHARS / 2 -> prefix.substring(0, prefix.lastIndexOf(' '))
        else -> prefix
    }
    return "Paso $stepNumber: ${cut.trimEnd(' ', ',', ';', ':', '.', '-', '—')}…"
}

/** «Paso 2» de «Paso 2: Sofríe la cebolla…». */
fun shortTimerLabel(label: String): String = label.substringBefore(':')

/** «4:58», «20:00» o «1:05:05»; redondea hacia arriba (no marca 0:00 hasta que termina). */
fun formatTimerClock(ms: Long): String {
    val total = if (ms <= 0) 0 else (ms + 999) / 1000
    val h = total / 3600
    val m = total % 3600 / 60
    val s = total % 60
    return if (h > 0) "$h:${m.pad()}:${s.pad()}" else "$m:${s.pad()}"
}

private fun Long.pad() = toString().padStart(2, '0')

/**
 * Una línea por temporizador activo para la notificación en curso: primero los que corren (el que antes
 * termina arriba) y después los pausados: «Paso 2 · 4:58», «Paso 4 · ⏸ 6:40».
 */
fun ongoingTimerLines(timers: List<CookingTimer>, now: Long): List<String> {
    val running = timers.filter { it.isRunning }.sortedBy { it.remainingMs(now) }
    val paused = timers.filter { it.isPaused }
    return running.map { "${shortTimerLabel(it.label)} · ${formatTimerClock(it.remainingMs(now))}" } +
        paused.map { "${shortTimerLabel(it.label)} · ⏸ ${formatTimerClock(it.remainingMs(now))}" }
}

/** Título del aviso de fin: «⏱ ¡Tiempo! — Paso 2: Sofríe la cebolla…». */
fun finishedTimerTitle(label: String): String = "⏱ ¡Tiempo! — $label"
