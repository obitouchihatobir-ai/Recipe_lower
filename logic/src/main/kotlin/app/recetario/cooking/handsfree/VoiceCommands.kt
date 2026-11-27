package app.recetario.cooking.handsfree

import app.recetario.core.text.normalize

/** Órdenes de voz del modo cocina. */
enum class VoiceCommand { NEXT, PREVIOUS, TIMER, REPEAT }

/** Palabras (ya normalizadas) de cada orden. */
private val COMMAND_WORDS = mapOf(
    "siguiente" to VoiceCommand.NEXT,
    "listo" to VoiceCommand.NEXT,
    "hecho" to VoiceCommand.NEXT,
    "anterior" to VoiceCommand.PREVIOUS,
    "atras" to VoiceCommand.PREVIOUS,
    "temporizador" to VoiceCommand.TIMER,
    "repite" to VoiceCommand.REPEAT,
    "repetir" to VoiceCommand.REPEAT,
)

/** Palabras de relleno que pueden acompañar a una orden («Sazón, siguiente paso por favor»). */
private val FILLER_WORDS = setOf("por", "favor", "sazon", "paso", "el", "ya", "ok", "oye")

const val VOICE_MAX_WORDS = 3

private val nonWord = Regex("[^\\p{L}\\p{N}]+")

/** La orden de una frase, solo si es una orden y nada más: como mucho 3 palabras y, sin el relleno, una de orden. */
private fun strictCommand(phrase: String): VoiceCommand? {
    val words = normalize(phrase).split(nonWord).filter { it.isNotEmpty() }
    if (words.isEmpty() || words.size > VOICE_MAX_WORDS) return null
    val rest = words.filter { it !in FILLER_WORDS }
    return rest.singleOrNull()?.let { COMMAND_WORDS[it] }
}

/**
 * La orden de lo reconocido. Es estricta para no avanzar con la charla de la cocina, la tele o el propio paso leído
 * en voz alta («hasta que esté listo»): sin tildes, mayúsculas ni puntuación, la frase tiene como mucho
 * [VOICE_MAX_WORDS] palabras y, quitando el relleno («por favor», «Sazón», «paso»…), queda exactamente una palabra de
 * orden. Mira las alternativas del reconocedor en orden y devuelve la de la primera que pasa; si ninguna, null.
 */
fun parseVoiceCommand(alternatives: List<String>): VoiceCommand? =
    alternatives.firstNotNullOfOrNull { strictCommand(it) }
