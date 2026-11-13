package app.recetario.recipes.editor

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID

private const val COVER = "cover"
private const val STEP = "step:"

private fun PhotoTarget.encode(): String = when (this) {
    PhotoTarget.Cover -> COVER
    is PhotoTarget.StepPhoto -> STEP + stepId
}

private fun decodeTarget(s: String): PhotoTarget? = when {
    s == COVER -> PhotoTarget.Cover
    s.startsWith(STEP) -> PhotoTarget.StepPhoto(s.removePrefix(STEP))
    else -> null
}

/**
 * Cámara (`TakePicture` con un archivo de `cacheDir/camera/` compartido por FileProvider) o galería
 * (`PickVisualMedia`). La foto se entrega como flujo a [onPicked] (el PhotoStore la comprime) y el archivo
 * temporal de la cámara se borra. El destino se guarda por si Android cierra la app mientras la cámara está abierta.
 */
@Composable
fun rememberPhotoRequester(
    onPicked: (InputStream, PhotoTarget) -> Unit,
    onError: (String) -> Unit,
): (PhotoTarget, PhotoSource) -> Unit {
    val context = LocalContext.current
    val picked by rememberUpdatedState(onPicked)
    val error by rememberUpdatedState(onError)
    var pendingTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingFile by rememberSaveable { mutableStateOf<String?>(null) }
    val cameraDir = remember(context) { File(context.cacheDir, "camera") }

    // Restos de capturas que nunca volvieron (la app se cerró a medias): se borran al abrir el editor.
    LaunchedEffect(cameraDir) {
        withContext(Dispatchers.IO) {
            cameraDir.listFiles()?.filter { it.absolutePath != pendingFile }?.forEach { it.delete() }
        }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = pendingFile?.let(::File)
        val target = pendingTarget?.let(::decodeTarget)
        pendingFile = null
        pendingTarget = null
        if (file == null) return@rememberLauncherForActivityResult
        try {
            if (ok && target != null && file.length() > 0) {
                // Se abre antes de borrarlo: el flujo abierto sigue pudiendo leerse.
                picked(FileInputStream(file), target)
            }
        } catch (_: IOException) {
            error("No se pudo leer la foto.")
        } finally {
            file.delete()
        }
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        val target = pendingTarget?.let(::decodeTarget)
        pendingTarget = null
        if (uri == null || target == null) return@rememberLauncherForActivityResult
        val stream = try {
            context.contentResolver.openInputStream(uri)
        } catch (_: Exception) {
            null
        }
        if (stream == null) error("No se pudo abrir la foto.") else picked(stream, target)
    }

    return remember(context) {
        { target: PhotoTarget, source: PhotoSource ->
            pendingTarget = target.encode()
            when (source) {
                PhotoSource.CAMERA -> {
                    val file = File(cameraDir.apply { mkdirs() }, "${UUID.randomUUID()}.jpg")
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    pendingFile = file.absolutePath
                    try {
                        camera.launch(uri)
                    } catch (_: ActivityNotFoundException) {
                        file.delete()
                        pendingFile = null
                        pendingTarget = null
                        error("No hay ninguna app de cámara.")
                    }
                }
                PhotoSource.GALLERY -> try {
                    gallery.launch(
                        PickVisualMediaRequest.Builder()
                            .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            .build(),
                    )
                } catch (_: ActivityNotFoundException) {
                    pendingTarget = null
                    error("No hay ninguna galería de fotos.")
                }
            }
        }
    }
}
