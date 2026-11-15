package app.recetario.recipes.editor

import android.content.ActivityNotFoundException
import android.content.Context
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

private fun open(context: Context, photo: PendingPhoto): InputStream? = try {
    if (photo.tempFile) FileInputStream(photo.source) else context.contentResolver.openInputStream(Uri.parse(photo.source))
} catch (_: Exception) {
    null
}

/**
 * Cámara (`TakePicture` con un archivo de `cacheDir/camera/` compartido por FileProvider) o galería
 * (`PickVisualMedia`). Cada resultado queda en [PendingPhotos] (guardado con `rememberSaveable`, y el archivo
 * de la cámara sigue en disco) hasta que [editorReady] es true y no hay borrador por decidir ([draftPending]):
 * si Android cerró la app con la cámara abierta, el resultado llega al recrearse, antes de que el editor esté
 * listo. Entonces se entrega a [onPicked] (el PhotoStore la comprime) y se borra el archivo temporal.
 */
@Composable
fun rememberPhotoRequester(
    editorReady: Boolean,
    draftPending: Boolean,
    onPicked: (InputStream, PhotoTarget) -> Unit,
    onError: (String) -> Unit,
): PhotoRequester {
    val context = LocalContext.current
    val picked by rememberUpdatedState(onPicked)
    val error by rememberUpdatedState(onError)
    // Cámara o galería abiertas: a qué destino va y, con la cámara, en qué archivo escribe.
    var launchTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var launchFile by rememberSaveable { mutableStateOf<String?>(null) }
    // Resultados aún sin entregar (PendingPhotos.encode()).
    var pending by rememberSaveable { mutableStateOf(ArrayList<String>()) }
    val cameraDir = remember(context) { File(context.cacheDir, "camera") }

    // Restos de capturas que nunca volvieron: se borran, salvo los que esperan entrega o la captura en curso.
    LaunchedEffect(cameraDir) {
        val keep = PendingPhotos.decode(pending).tempFiles + listOfNotNull(launchFile)
        withContext(Dispatchers.IO) {
            cameraDir.listFiles()?.filter { it.absolutePath !in keep }?.forEach { it.delete() }
        }
    }

    // Entrega sin suspender: así un cambio de las claves no puede cortarla a medias.
    LaunchedEffect(editorReady, draftPending, pending) {
        val (deliver, rest) = PendingPhotos.decode(pending).release(editorReady, draftPending)
        if (deliver.isEmpty()) return@LaunchedEffect
        for (photo in deliver) {
            val target = decodeTarget(photo.target)
            // Se abre antes de borrar el temporal: el flujo abierto sigue pudiendo leerse.
            val stream = target?.let { open(context, photo) }
            if (photo.tempFile) File(photo.source).delete()
            when {
                target == null -> Unit
                stream == null -> error("No se pudo abrir la foto.")
                else -> picked(stream, target)
            }
        }
        pending = rest.encode()
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = launchFile?.let(::File)
        val target = launchTarget
        launchFile = null
        launchTarget = null
        if (file == null) return@rememberLauncherForActivityResult
        if (ok && target != null && file.length() > 0) {
            pending = (PendingPhotos.decode(pending) + PendingPhoto(file.absolutePath, tempFile = true, target = target)).encode()
        } else {
            file.delete()
        }
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        val target = launchTarget
        launchTarget = null
        if (uri != null && target != null) {
            pending = (PendingPhotos.decode(pending) + PendingPhoto(uri.toString(), tempFile = false, target = target)).encode()
        }
    }

    return remember(context) {
        PhotoRequester(
            request = { target, source ->
                launchTarget = target.encode()
                when (source) {
                    PhotoSource.CAMERA -> {
                        val file = File(cameraDir.apply { mkdirs() }, "${UUID.randomUUID()}.jpg")
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        launchFile = file.absolutePath
                        try {
                            camera.launch(uri)
                        } catch (_: ActivityNotFoundException) {
                            file.delete()
                            launchFile = null
                            launchTarget = null
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
                        launchTarget = null
                        error("No hay ninguna galería de fotos.")
                    }
                }
            },
            discardPending = {
                PendingPhotos.decode(pending).tempFiles.forEach { File(it).delete() }
                pending = ArrayList()
            },
        )
    }
}
