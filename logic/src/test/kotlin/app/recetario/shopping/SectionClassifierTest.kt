package app.recetario.shopping

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SectionClassifierTest {
    @Test fun calabazaIsFruitsAndVeg() = assertThat(SectionClassifier.classify("Calabaza")).isEqualTo(ShopSection.FRUITS_VEG)

    @Test fun cremaNataIsDairy() = assertThat(SectionClassifier.classify("Crema / nata")).isEqualTo(ShopSection.DAIRY)

    @Test fun firstWordWins() = assertThat(SectionClassifier.classify("Caldo de verduras")).isEqualTo(ShopSection.PANTRY)

    @Test fun pechugaDePolloIsMeat() = assertThat(SectionClassifier.classify("Pechuga de pollo")).isEqualTo(ShopSection.MEAT_FISH)

    @Test fun aguaMineralIsDrinks() = assertThat(SectionClassifier.classify("Agua mineral")).isEqualTo(ShopSection.DRINKS)

    @Test fun unknownIsOther() = assertThat(SectionClassifier.classify("Xyz")).isEqualTo(ShopSection.OTHER)

    @Test fun ignoresAccentsCaseAndPlurals() {
        assertThat(SectionClassifier.classify("LIMONES")).isEqualTo(ShopSection.FRUITS_VEG)
        assertThat(SectionClassifier.classify("Plátanos")).isEqualTo(ShopSection.FRUITS_VEG)
        assertThat(SectionClassifier.classify("Huevos")).isEqualTo(ShopSection.DAIRY)
    }

    @Test fun fallsBackToLaterWordsOfTheName() {
        assertThat(SectionClassifier.classify("Dos tomates")).isEqualTo(ShopSection.FRUITS_VEG)
    }

    @Test fun blankIsOther() = assertThat(SectionClassifier.classify("  ")).isEqualTo(ShopSection.OTHER)

    @Test fun multiWordEntriesWinOverTheFirstWord() {
        assertThat(SectionClassifier.classify("Tomate frito")).isEqualTo(ShopSection.PANTRY)
        assertThat(SectionClassifier.classify("Harina de trigo")).isEqualTo(ShopSection.PANTRY)
        assertThat(SectionClassifier.classify("harina de trigo integral")).isEqualTo(ShopSection.PANTRY)
        assertThat(SectionClassifier.classify("Caldo de verduras")).isEqualTo(ShopSection.PANTRY)
        assertThat(SectionClassifier.classify("Calabaza")).isEqualTo(ShopSection.FRUITS_VEG)
    }
}
