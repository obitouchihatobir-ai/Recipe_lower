package app.recetario

import android.content.ComponentName
import android.content.Context
import android.os.Looper
import android.util.TypedValue
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.core.ui.motion.SPLASH_EXIT_MS
import app.recetario.core.ui.motion.splashExitAnimator
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.core.ui.theme.paletteFor
import androidx.compose.ui.graphics.toArgb
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.time.Duration

/** Pantalla de arranque (Task 18): tema de MainActivity y animación de salida (el vapor sube y se desvanece). */
@RunWith(AndroidJUnit4::class)
class SplashTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun mainActivity_startsWithTheSplashTheme_iconOverTheCalidoBackground() {
        val info = context.packageManager.getActivityInfo(ComponentName(context, MainActivity::class.java), 0)
        assertThat(info.theme).isEqualTo(R.style.Theme_Sazon_Arranque)

        val theme = context.resources.newTheme().apply { applyStyle(R.style.Theme_Sazon_Arranque, true) }
        val value = TypedValue()
        assertThat(theme.resolveAttribute(androidx.core.splashscreen.R.attr.windowSplashScreenBackground, value, true)).isTrue()
        assertThat(value.data).isEqualTo(paletteFor(ThemeChoice.CALIDO).bg.toArgb())
        assertThat(theme.resolveAttribute(androidx.core.splashscreen.R.attr.windowSplashScreenAnimatedIcon, value, false)).isTrue()
        assertThat(context.resources.getResourceEntryName(value.data)).isEqualTo("ic_launcher_foreground")
        assertThat(theme.resolveAttribute(androidx.core.splashscreen.R.attr.postSplashScreenTheme, value, false)).isTrue()
        assertThat(value.data).isEqualTo(android.R.style.Theme_Material_Light_NoActionBar)
    }

    @Test
    fun exitAnimation_iconRisesAndFades_withinTheLimit_thenRemovesTheSplash() {
        val splash = View(context)
        val icon = View(context)
        var removed = 0
        val animator = splashExitAnimator(icon, splash, onEnd = { removed++ })
        assertThat(animator.totalDuration).isAtMost(450L)
        assertThat(animator.totalDuration).isEqualTo(SPLASH_EXIT_MS)

        animator.start()
        // Nada se quita de golpe: hasta que termina la animación, la pantalla de arranque sigue.
        assertThat(removed).isEqualTo(0)

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(SPLASH_EXIT_MS + 100))
        assertThat(icon.alpha).isEqualTo(0f)
        assertThat(icon.translationY).isLessThan(0f)
        assertThat(splash.alpha).isEqualTo(0f)
        assertThat(removed).isEqualTo(1)
    }

    @Test
    fun exitAnimation_withoutIcon_stillRemovesTheSplash() {
        val splash = View(context)
        var removed = 0
        splashExitAnimator(null, splash, onEnd = { removed++ }).start()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(SPLASH_EXIT_MS + 100))
        assertThat(removed).isEqualTo(1)
    }
}
