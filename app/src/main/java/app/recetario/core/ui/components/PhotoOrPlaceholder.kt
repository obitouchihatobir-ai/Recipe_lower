package app.recetario.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import app.recetario.core.ui.theme.LocalRecetarioColors
import app.recetario.recipes.data.PhotoStore
import coil3.compose.SubcomposeAsyncImage

/** Foto de la receta o, si no hay (o no se puede leer), un degradado con el emoji de la categoría. */
@Composable
fun PhotoOrPlaceholder(
    fileName: String?,
    photoStore: PhotoStore,
    emoji: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    emojiSize: TextUnit = 40.sp,
) {
    val colors = LocalRecetarioColors.current
    val file = remember(fileName) { fileName?.let { photoStore.file(it) }?.takeIf { it.exists() } }
    val placeholder: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize().background(colors.heroBrush), contentAlignment = Alignment.Center) {
            Text(emoji, fontSize = emojiSize)
        }
    }
    Box(modifier) {
        if (file == null) {
            placeholder()
        } else {
            SubcomposeAsyncImage(
                model = file,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                error = { placeholder() },
            )
        }
    }
}
