package app.recetario.core.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Iconos de trazo (1,8 de grosor, extremos redondeados) como los de los bocetos; `Icon` les pone el color. */
object AppIcons {
    val Home = stroke("Inicio", "M3 11l9-7 9 7", "M5 10v10h14V10")
    val Recipes = stroke("Recetas", "M4 5a2 2 0 0 1 2-2h13v16H6a2 2 0 0 0-2 2z", "M4 19V5")
    val Shopping = stroke("Compra", "M3 4h2l2.4 11h11L21 7H6.2", circle(9f, 19.5f, 1.3f), circle(17f, 19.5f, 1.3f))
    val Pairing = stroke("Maridaje", "M12 3c-3 4-6 6-6 10a6 6 0 0 0 12 0c0-4-3-6-6-10z")
    val Plus = stroke("Más", "M12 5v14", "M5 12h14")
    val Search = stroke("Buscar", circle(11f, 11f, 6.5f), "M20 20l-4.2-4.2")
    val Filter = stroke(
        "Filtros",
        "M4 7h9", "M17 7h3", circle(15f, 7f, 2f),
        "M4 12h3", "M11 12h9", circle(9f, 12f, 2f),
        "M4 17h11", circle(17f, 17f, 2f),
    )
    val Grid = stroke("Cuadrícula", "M4 4h6v6H4z", "M14 4h6v6h-6z", "M4 14h6v6H4z", "M14 14h6v6h-6z")
    val List = stroke("Lista", "M9 6h11", "M9 12h11", "M9 18h11", "M4.5 6h.01", "M4.5 12h.01", "M4.5 18h.01")
    val Back = stroke("Volver", "M19 12H5", "M11 6l-6 6 6 6")
    val Close = stroke("Cerrar", "M6 6l12 12", "M18 6L6 18")
    val DragHandle = stroke("Mover", "M5 9h14", "M5 15h14")
    val Camera = stroke("Cámara", "M4 8h3l2-3h6l2 3h3v11H4z", circle(12f, 13f, 3.5f))
    val Gallery = stroke("Galería", "M4 5h16v14H4z", "M4 16l5-5 4 4 2-2 5 5", circle(15.5f, 9f, 1.5f))
    val ChevronDown = stroke("Desplegar", "M6 9l6 6 6-6")
    val Note = stroke("Nota", "M6 4h9l3 3v13H6z", "M9 11h6", "M9 15h4")
    val Share = stroke("Compartir", "M12 15V4", "M8 8l4-4 4 4", "M5 13v6h14v-6")
    val Sort = stroke("Ordenar", "M7 4v16", "M4 17l3 3 3-3", "M17 20V4", "M14 7l3-3 3 3")
}

private fun circle(cx: Float, cy: Float, r: Float): String =
    "M${cx - r},${cy}a$r,$r 0 1,0 ${2 * r},0a$r,$r 0 1,0 ${-2 * r},0"

private fun stroke(name: String, vararg paths: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        for (d in paths) {
            addPath(
                pathData = addPathNodes(d),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()
