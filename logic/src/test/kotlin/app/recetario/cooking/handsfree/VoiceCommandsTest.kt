package app.recetario.cooking.handsfree

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VoiceCommandsTest {
    @Test
    fun voice_commands_variants() {
        val cases = mapOf(
            "siguiente" to VoiceCommand.NEXT, "Siguiente." to VoiceCommand.NEXT, "LISTO" to VoiceCommand.NEXT,
            "ya está hecho" to VoiceCommand.NEXT, "anterior" to VoiceCommand.PREVIOUS,
            "atrás" to VoiceCommand.PREVIOUS, "Atras" to VoiceCommand.PREVIOUS,
            "temporizador" to VoiceCommand.TIMER, "pon el temporizador" to VoiceCommand.TIMER,
            "repite" to VoiceCommand.REPEAT, "¿Puedes repetir?" to VoiceCommand.REPEAT,
            "  Repíte  " to VoiceCommand.REPEAT,
        )
        for ((phrase, command) in cases) assertThat(parseVoiceCommand(listOf(phrase))).isEqualTo(command)
    }

    @Test
    fun voice_otherPhrase_null() {
        for (phrase in listOf(
            "hola", "", "   ", "listón", "hechos", "siguientes pasos", "atrasado", "temporizadores", "repiten",
            "quiero más sal",
        )) assertThat(parseVoiceCommand(listOf(phrase))).isNull()
        assertThat(parseVoiceCommand(emptyList())).isNull()
    }

    @Test
    fun voice_usesAlternatives() {
        assertThat(parseVoiceCommand(listOf("si gente", "siguiente"))).isEqualTo(VoiceCommand.NEXT)
        assertThat(parseVoiceCommand(listOf("a través", "atrás"))).isEqualTo(VoiceCommand.PREVIOUS)
        // La primera alternativa que trae una orden manda.
        assertThat(parseVoiceCommand(listOf("repite", "siguiente"))).isEqualTo(VoiceCommand.REPEAT)
        assertThat(parseVoiceCommand(listOf("nada", "tampoco"))).isNull()
    }
}
