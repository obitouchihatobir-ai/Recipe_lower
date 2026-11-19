package app.recetario.core.ui.motion

import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver

/**
 * Por dónde va una animación de dibujo (0 a 1): el tachado de un paso, el relleno de una casilla o el anillo de
 * un temporizador. Solo lo leen las pruebas; TalkBack no lo conoce y no anuncia nada.
 */
val AnimationProgress = SemanticsPropertyKey<Float>("AnimationProgress")
var SemanticsPropertyReceiver.animationProgress by AnimationProgress
