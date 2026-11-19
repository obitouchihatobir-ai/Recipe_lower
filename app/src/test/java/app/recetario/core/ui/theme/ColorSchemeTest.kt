package app.recetario.core.ui.theme

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * Los avisos de Material (Snackbar: «Copia guardada.», «Deshacer»…) usan los roles «inverse» del esquema. Deben salir
 * con los colores del tema, como el aviso propio del detalle (fondo `ink`, texto `bg`), y no con los de serie de
 * Material (gris oscuro y botón lila).
 */
class ColorSchemeTest {
    @Test
    fun snackbarRolesUseTheThemeColors() {
        for (choice in ThemeChoice.entries) {
            val p = paletteFor(choice)
            val scheme = colorSchemeFor(choice, p)
            assertWithMessage("$choice: fondo del aviso").that(scheme.inverseSurface).isEqualTo(p.ink)
            assertWithMessage("$choice: texto del aviso").that(scheme.inverseOnSurface).isEqualTo(p.bg)
            assertWithMessage("$choice: acción del aviso").that(scheme.inversePrimary).isEqualTo(p.bg)
        }
    }

    @Test
    fun snackbarTextAndActionReachMinimumContrast() {
        for (choice in ThemeChoice.entries) {
            val scheme = colorSchemeFor(choice, paletteFor(choice))
            assertThat(contrastRatio(scheme.inverseOnSurface, scheme.inverseSurface)).isAtLeast(4.5)
            assertThat(contrastRatio(scheme.inversePrimary, scheme.inverseSurface)).isAtLeast(4.5)
        }
    }
}
