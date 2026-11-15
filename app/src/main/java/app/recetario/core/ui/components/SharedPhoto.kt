package app.recetario.core.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

/** Ámbito de la transición compartida que envuelve el NavHost (null fuera de él, por ejemplo en pruebas). */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/** Ámbito de animación del destino actual del NavHost. */
val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * True solo si la navegación en curso entra o sale del detalle. Así la foto no salta entre Inicio y
 * Recetas al cambiar de pestaña (las dos tarjetas tienen la misma clave).
 */
val LocalSharePhotos = compositionLocalOf { false }

fun sharedPhotoKey(recipeId: String) = "photo-$recipeId"

/** La foto de la receta viaja de la tarjeta a la cabecera del detalle (clave `photo-$recipeId`). */
@Composable
fun Modifier.sharedRecipePhoto(recipeId: String, enabled: Boolean = true): Modifier {
    val shared = LocalSharedTransitionScope.current
    val visibility = LocalNavAnimatedVisibilityScope.current
    if (!enabled || !LocalSharePhotos.current || shared == null || visibility == null) return this
    return with(shared) {
        this@sharedRecipePhoto.sharedElement(
            sharedContentState = rememberSharedContentState(sharedPhotoKey(recipeId)),
            animatedVisibilityScope = visibility,
        )
    }
}
