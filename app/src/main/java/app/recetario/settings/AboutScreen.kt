package app.recetario.settings

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.recetario.core.ui.components.MutedText
import app.recetario.core.ui.components.SectionTitle
import app.recetario.core.ui.theme.LocalRecetarioColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Fuentes incluidas y el archivo de su licencia en `assets/licenses/`. */
private val fontLicenses = listOf(
    "Fraunces" to "OFL-Fraunces.txt",
    "Inter" to "OFL-Inter.txt",
    "Nunito" to "OFL-Nunito.txt",
    "Playfair Display" to "OFL-PlayfairDisplay.txt",
)

@Composable
fun AboutRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    AboutContent(versionName = remember(context) { versionName(context) }, onBack = onBack)
}

/** Acerca de: nombre, versión, créditos y las licencias de las fuentes (cada una se abre en una pantalla con desplazamiento). */
@Composable
fun AboutContent(versionName: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalRecetarioColors.current
    var openLicense by remember { mutableStateOf<String?>(null) }
    val selected = openLicense
    if (selected != null) {
        LicenseScreen(selected, onBack = { openLicense = null }, modifier = modifier)
        return
    }
    SettingsPage("Acerca de", onBack, tag = "about-screen", modifier = modifier) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 4.dp)) {
            Text("Recetario", color = c.ink, style = MaterialTheme.typography.headlineSmall)
            MutedText("Versión $versionName", Modifier.testTag("about-version"), fontSizeSp = 14)
            MutedText("Hecho con cariño para cocinar en casa", Modifier.padding(top = 8.dp), fontSizeSp = 14)
            SectionTitle("Licencias de las fuentes", Modifier.padding(top = 24.dp, bottom = 4.dp))
            for ((name, _) in fontLicenses) {
                SettingsRow(
                    name,
                    "SIL Open Font License 1.1",
                    Modifier.testTag("license-$name"),
                    onClick = { openLicense = name },
                )
            }
            MutedText(
                "Fuentes: Fraunces, Inter, Nunito, Playfair Display (SIL Open Font License 1.1)",
                Modifier.padding(top = 16.dp).testTag("about-credits"),
            )
        }
    }
}

@Composable
private fun LicenseScreen(name: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalRecetarioColors.current
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    var text by remember(name) { mutableStateOf<String?>(null) }
    LaunchedEffect(name) {
        text = withContext(Dispatchers.IO) {
            val file = fontLicenses.firstOrNull { it.first == name }?.second
            runCatching { context.assets.open("licenses/$file").bufferedReader().use { it.readText() } }
                .getOrDefault("No se pudo abrir la licencia.")
        }
    }
    SettingsPage("Licencia de $name", onBack, tag = "license-screen", modifier = modifier) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp)) {
            Text(text ?: "", color = c.ink, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("license-text"))
        }
    }
}

@Suppress("DEPRECATION")
private fun versionName(context: Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
