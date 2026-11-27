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

private val nonWord = Regex("[^\\p{L}\\p{N}]+")

/**
 * La orden de lo reconocido: mira las alternativas del reconocedor en orden y devuelve la primera que tenga una
 * palabra de orden, completa y sin distinguir tildes ni mayúsculas. Cualquier otra frase da null.
 */
fun parseVoiceCommand(alternatives: List<String>): VoiceCommand? =
    alternatives.firstNotNullOfOrNull { phrase ->
        normalize(phrase).split(nonWord).firstNotNullOfOrNull { COMMAND_WORDS[it] }
    }
