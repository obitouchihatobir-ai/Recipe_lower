package app.recetario.core.ui.motion

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.PathInterpolator

/** Lo que dura la salida de la pantalla de arranque (el vapor sube y se desvanece). */
const val SPLASH_EXIT_MS = 400L

/**
 * Salida de la pantalla de arranque: el icono [icon] sube un poco (como el vapor) y se desvanece, y el fondo
 * [splash] se funde con la app, en [SPLASH_EXIT_MS] ms. Al terminar (o al cancelarse) llama a [onEnd], que debe
 * quitar la pantalla de arranque. Con las animaciones quitadas no se usa: el sistema la quita sin más.
 */
fun splashExitAnimator(icon: View?, splash: View, onEnd: () -> Unit): Animator {
    val rise = -splash.resources.displayMetrics.density * 24f
    val parts = buildList {
        if (icon != null) {
            add(ObjectAnimator.ofFloat(icon, View.TRANSLATION_Y, 0f, rise))
            add(ObjectAnimator.ofFloat(icon, View.ALPHA, 1f, 0f))
        }
        add(ObjectAnimator.ofFloat(splash, View.ALPHA, 1f, 0f))
    }
    return AnimatorSet().apply {
        playTogether(parts)
        duration = SPLASH_EXIT_MS
        interpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f)
        addListener(object : AnimatorListenerAdapter() {
            private var ended = false
            override fun onAnimationEnd(animation: Animator) {
                if (!ended) {
                    ended = true
                    onEnd()
                }
            }
        })
    }
}
