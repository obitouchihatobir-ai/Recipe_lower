package app.recetario.pairing

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.recetario.AppContainer
import app.recetario.core.ui.components.SectionTitle
import app.recetario.core.ui.components.flavorPolygon
import app.recetario.core.ui.components.labelOrigin
import app.recetario.core.ui.motion.LocalReducedMotion
import app.recetario.core.ui.theme.LocalRecetarioColors
import app.recetario.recipes.model.FlavorProfile
import app.recetario.settings.SettingsPage
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Lo que tarda en dibujarse cada rueda (una después de la otra). */
const val WHEEL_DRAW_MS = 400

/** Por dónde va el dibujo de cada rueda (0 a 1): solo para las pruebas; TalkBack no lo anuncia. */
val CompareWheelProgress = SemanticsPropertyKey<List<Float>>("CompareWheelProgress")
var SemanticsPropertyReceiver.compareWheelProgress by CompareWheelProgress

private val axisNames = listOf("Dulce", "Salado", "Ácido", "Amargo", "Picante")

private fun profileWords(p: FlavorProfile) =
    "dulce ${p.sweet} de 3, salado ${p.salty} de 3, ácido ${p.sour} de 3, amargo ${p.bitter} de 3, picante ${p.spicy} de 3"

/** Para TalkBack: los cinco sabores de cada lado, con su nombre. */
fun compareWheelDescription(a: PairingItem, b: PairingItem): String =
    "Comparación de sabores. ${a.name}: ${profileWords(a.flavor)}. ${b.name}: ${profileWords(b.flavor)}."

private fun axisPoint(i: Int, center: Offset, r: Float): Offset {
    val angle = -PI / 2 + i * 2 * PI / 5
    return Offset(center.x + r * cos(angle).toFloat(), center.y + r * sin(angle).toFloat())
}

/**
 * Las dos ruedas de sabor superpuestas: la primera en el color de acento con línea continua y la segunda en tinta con
 * línea discontinua. El relleno es semitransparente; las líneas y el texto, opacos. Cada rueda crece desde el centro
 * en [WHEEL_DRAW_MS], primero una y luego la otra; con las animaciones quitadas sale entera al instante. No recibe
 * toques (no tapa nada) y TalkBack lee [description].
 */
@Composable
fun CompareWheel(first: FlavorProfile, second: FlavorProfile, description: String, modifier: Modifier = Modifier, size: Dp = 230.dp) {
    val colors = LocalRecetarioColors.current
    val reduced = LocalReducedMotion.current
    val a = remember { Animatable(if (reduced) 1f else 0f) }
    val b = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(reduced) {
        if (reduced) {
            a.snapTo(1f)
            b.snapTo(1f)
        } else {
            val easing = CubicBezierEasing(0.2f, 0.9f, 0.2f, 1f)
            a.animateTo(1f, tween(WHEEL_DRAW_MS, easing = easing))
            b.animateTo(1f, tween(WHEEL_DRAW_MS, easing = easing))
        }
    }
    val progressA = a.value
    val progressB = b.value
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = colors.muted, fontSize = 13.sp)
    val firstValues = listOf(first.sweet, first.salty, first.sour, first.bitter, first.spicy)
    val secondValues = listOf(second.sweet, second.salty, second.sour, second.bitter, second.spicy)

    Canvas(
        modifier
            .size(size)
            .testTag("compare-wheel")
            .clearAndSetSemantics {
                contentDescription = description
                compareWheelProgress = listOf(progressA, progressB)
            },
    ) {
        val unit = this.size.minDimension / 230f
        val c = Offset(this.size.width / 2, this.size.height / 2)
        val r = this.size.minDimension * 0.34f
        for (k in 1..3) {
            val grid = Path()
            for (i in 0 until 5) {
                val p = axisPoint(i, c, r * k / 3)
                if (i == 0) grid.moveTo(p.x, p.y) else grid.lineTo(p.x, p.y)
            }
            grid.close()
            drawPath(grid, colors.line, style = Stroke(width = 1.5f * unit))
        }
        for (i in 0 until 5) {
            drawLine(colors.line, c, axisPoint(i, c, r), strokeWidth = 1.5f * unit)
            val layout = measurer.measure(axisNames[i], labelStyle)
            drawText(
                layout,
                topLeft = labelOrigin(
                    c, -PI / 2 + i * 2 * PI / 5, r + 22f * unit,
                    Size(layout.size.width.toFloat(), layout.size.height.toFloat()), this.size,
                ),
            )
        }
        fun polygon(values: List<Int>): Path = Path().apply {
            flavorPolygon(values, c, r).forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
            close()
        }
        val pa = polygon(firstValues)
        val pb = polygon(secondValues)
        scale(progressA, pivot = c) {
            drawPath(pa, colors.accent.copy(alpha = 0.26f))
            drawPath(pa, colors.accent, style = Stroke(width = 3f * unit, join = StrokeJoin.Round))
        }
        if (progressB > 0f) {
            scale(progressB, pivot = c) {
                drawPath(pb, colors.ink.copy(alpha = 0.14f))
                drawPath(
                    pb, colors.ink,
                    style = Stroke(
                        width = 2.5f * unit,
                        join = StrokeJoin.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(9f * unit, 6f * unit)),
                    ),
                )
            }
        }
    }
}

/** Muestra de línea de la leyenda: continua (primer elemento) o discontinua (segundo). */
@Composable
private fun LegendLine(color: Color, dashed: Boolean) {
    Canvas(Modifier.width(28.dp).height(12.dp)) {
        val y = size.height / 2
        drawLine(
            color, Offset(0f, y), Offset(size.width, y), strokeWidth = 3.dp.toPx(),
            pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 3.dp.toPx())) else null,
        )
    }
}

@Composable
fun CompareRoute(
    container: AppContainer,
    first: Pair<String, String>,
    second: Pair<String, String>,
    onBack: () -> Unit,
    repository: PairingRepository = container.pairingRepository,
) {
    val data by repository.observeData().collectAsStateWithLifecycle(initialValue = null)
    val d = data
    val a = d?.find(first.first, first.second)
    val b = d?.find(second.first, second.second)
    val score = remember(a, b, d?.weights) { if (a != null && b != null && d != null) scorePair(a, b, d.weights) else null }
    val key = if (a != null && b != null) ratableKey(a, b) else null
    CompareContent(
        loaded = d != null,
        a = a,
        b = b,
        score = score,
        liked = key?.let { d?.ratings?.get(it) },
        ratable = key != null,
        onRate = { liked ->
            if (a != null && b != null && key != null) {
                container.applicationScope.launch { repository.rate(a, b, liked) }
            }
        },
        onBack = onBack,
    )
}

/** La rueda comparativa con la leyenda, las frases y 👍/👎 (solo si la pareja se puede valorar). */
@Composable
fun CompareContent(
    loaded: Boolean,
    a: PairingItem?,
    b: PairingItem?,
    score: PairScore?,
    liked: Boolean?,
    ratable: Boolean,
    onRate: (Boolean?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalRecetarioColors.current
    SettingsPage("Comparar sabores", onBack, tag = "pairing-compare", modifier = modifier) {
        if (!loaded) {
            Unit
        } else if (a == null || b == null) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("Esta pareja ya no existe.", color = c.muted, style = MaterialTheme.typography.bodyLarge)
            }
        } else Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()
                .padding(horizontal = 18.dp).padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SectionTitle("${a.name} + ${b.name}", Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            CompareWheel(a.flavor, b.flavor, compareWheelDescription(a, b))
            // La rueda ya lo dice todo a TalkBack: la leyenda solo nombra las líneas.
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for ((item, dashed) in listOf(a to false, b to true)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.clearAndSetSemantics {}) { LegendLine(if (dashed) c.ink else c.accent, dashed) }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(item.name, color = c.ink, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                (if (dashed) "Línea discontinua · " else "Línea continua · ") + itemSubtitle(item),
                                color = c.muted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            if (score != null) {
                Column(Modifier.fillMaxWidth().testTag("compare-reasons"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionTitle("Por qué")
                    for (reason in score.reasons) {
                        Text(reason, color = c.ink, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (ratable) {
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("¿Te gusta esta pareja?", color = c.ink, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    RateButtons(liked, onRate)
                }
            }
        }
    }
}
