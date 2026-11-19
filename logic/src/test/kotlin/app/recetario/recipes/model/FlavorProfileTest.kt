package app.recetario.recipes.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FlavorProfileTest {
    @Test
    fun hasTastes_falseWhenTheFiveTastesAreZero_whateverTheIntensityOrRichness() {
        assertThat(FlavorProfile().hasTastes()).isFalse()
        assertThat(FlavorProfile(intensity = Intensity.INTENSE, rich = true).hasTastes()).isFalse()
    }

    @Test
    fun hasTastes_trueWithAnyTaste() {
        assertThat(FlavorProfile(sweet = 1).hasTastes()).isTrue()
        assertThat(FlavorProfile(salty = 2).hasTastes()).isTrue()
        assertThat(FlavorProfile(sour = 3).hasTastes()).isTrue()
        assertThat(FlavorProfile(bitter = 1).hasTastes()).isTrue()
        assertThat(FlavorProfile(spicy = 1).hasTastes()).isTrue()
    }
}
