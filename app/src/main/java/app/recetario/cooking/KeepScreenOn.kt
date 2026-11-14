package app.recetario.cooking

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

/** Mantiene la pantalla encendida (`FLAG_KEEP_SCREEN_ON`) mientras esté compuesto; al salir lo quita. */
@Composable
fun KeepScreenOn() {
    val window = LocalActivity.current?.window
    DisposableEffect(window) {
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}
