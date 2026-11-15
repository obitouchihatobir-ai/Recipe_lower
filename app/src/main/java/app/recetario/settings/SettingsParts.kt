package app.recetario.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.recetario.core.ui.components.MutedText
import app.recetario.core.ui.components.ScreenTitle
import app.recetario.core.ui.components.SectionTitle
import app.recetario.core.ui.icons.AppIcons
import app.recetario.core.ui.theme.LocalRecetarioColors

/** Página de ajustes: botón Volver, título y contenido; [overlay] pinta paneles encima (con su fondo atenuado). */
@Composable
fun SettingsPage(
    title: String,
    onBack: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
    snackbar: SnackbarHostState? = null,
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    val c = LocalRecetarioColors.current
    Box(modifier.fillMaxSize().background(c.bg).testTag(tag)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.padding(start = 6.dp, end = 12.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("settings-back")) {
                    Icon(AppIcons.Back, contentDescription = "Volver", tint = c.ink)
                }
                ScreenTitle(title, Modifier.weight(1f))
                actions()
            }
            Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        }
        overlay()
        if (snackbar != null) SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp))
    }
}

/** Fila de una lista de ajustes: título, texto de apoyo y, a la derecha, lo que se quiera. */
@Composable
fun SettingsRow(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .let { if (onClick != null) it.clickable(role = Role.Button, onClick = onClick) else it }
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            SectionTitle(title)
            if (subtitle != null) MutedText(subtitle)
        }
        trailing()
    }
}

/** Panel inferior con fondo atenuado. Atrás o tocar fuera lo cierra. Va dentro de [SettingsPage.overlay]. */
@Composable
fun BoxScope.BottomPanel(onDismiss: () -> Unit, tag: String, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalRecetarioColors.current
    BackHandler(onBack = onDismiss)
    Box(
        Modifier
            .matchParentSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    )
    Column(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .background(c.surface, RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 18.dp, vertical = 16.dp)
            // Tocar el panel no debe llegar al fondo atenuado y cerrarlo.
            .pointerInput(Unit) { detectTapGestures { } }
            .testTag(tag),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/** Ejecuta [block]; si falla (salvo cancelación) avisa con [message] en vez de tumbar la app. */
suspend fun SnackbarHostState.reportFailure(message: String, block: suspend () -> Unit) {
    try {
        block()
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        currentSnackbarData?.dismiss()
        showSnackbar(message)
    }
}
