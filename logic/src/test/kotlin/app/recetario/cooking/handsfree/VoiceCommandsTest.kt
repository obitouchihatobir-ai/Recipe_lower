package app.recetario.cooking.handsfree

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

class VoiceCommandsTest {
    @Test
    fun voice_commands_variants() {
        val cases = mapOf(
            "siguiente" to VoiceCommand.NEXT, "Siguiente." to VoiceCommand.NEXT, "siguiente paso" to VoiceCommand.NEXT,
            "LISTO" to VoiceCommand.NEXT, "Sazón, listo" to VoiceCommand.NEXT, "ya, hecho" to VoiceCommand.NEXT,
            "ok siguiente" to VoiceCommand.NEXT, "anterior" to VoiceCommand.PREVIOUS,
            "paso anterior" to VoiceCommand.PREVIOUS, "atrás" to VoiceCommand.PREVIOUS,
            "Atras" to VoiceCommand.PREVIOUS, "temporizador" to VoiceCommand.TIMER,
            "oye, temporizador" to VoiceCommand.TIMER, "repite" to VoiceCommand.REPEAT,
            "repite por favor" to VoiceCommand.REPEAT, "¿Repetir?" to VoiceCommand.REPEAT,
            "  Repíte  " to VoiceCommand.REPEAT,
        )
        for ((phrase, command) in cases) {
            assertWithMessage(phrase).that(parseVoiceCommand(listOf(phrase))).isEqualTo(command)
        }
    }

    @Test
    fun voice_otherPhrase_null() {
        for (phrase in listOf(
            "hola", "", "   ", "listón", "hechos", "siguientes pasos", "atrasado", "temporizadores", "repiten",
            "quiero más sal",
            // Frases que contienen una orden, pero no son una orden: charla, la tele o el propio paso leído en voz alta.
            "ya está listo el arroz", "no está hecho", "¿está listo?", "hasta que esté listo", "pon el temporizador",
            "¿puedes repetir?",
            // Dos órdenes a la vez: no se adivina.
            "repite el paso anterior", "siguiente anterior",
            // Más de tres palabras, aunque sean de relleno.
            "oye sazon por favor siguiente", "paso",
        )) assertWithMessage(phrase).that(parseVoiceCommand(listOf(phrase))).isNull()
        assertThat(parseVoiceCommand(emptyList())).isNull()
    }

    @Test
    fun voice_usesAlternatives() {
        assertThat(parseVoiceCommand(listOf("si gente", "siguiente"))).isEqualTo(VoiceCommand.NEXT)
        assertThat(parseVoiceCommand(listOf("a través", "atrás"))).isEqualTo(VoiceCommand.PREVIOUS)
        // La primera alternativa que pasa la regla manda; las que no la pasan se saltan.
        assertThat(parseVoiceCommand(listOf("repite", "siguiente"))).isEqualTo(VoiceCommand.REPEAT)
        assertThat(parseVoiceCommand(listOf("ya está listo el arroz", "siguiente"))).isEqualTo(VoiceCommand.NEXT)
        assertThat(parseVoiceCommand(listOf("nada", "tampoco"))).isNull()
    }
}
