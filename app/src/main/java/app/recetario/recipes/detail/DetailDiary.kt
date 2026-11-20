package app.recetario.recipes.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.recetario.core.ui.components.DrawnStars
import app.recetario.core.ui.components.MutedText
import app.recetario.core.ui.components.PhotoOrPlaceholder
import app.recetario.core.ui.components.SectionTitle
import app.recetario.core.ui.components.TagPill
import app.recetario.core.ui.theme.LocalRecetarioColors
import app.recetario.diary.CookLogEntry
import app.recetario.diary.CookStats
import app.recetario.diary.RatingStars
import app.recetario.diary.cookStatsDescription
import app.recetario.diary.cookStatsLine
import app.recetario.diary.logDateLabel
import app.recetario.diary.starFills
import app.recetario.recipes.data.PhotoStore
import app.recetario.settings.BottomPanel
import java.time.LocalDate

/**
 * «La hiciste 3 veces · última hace 2 semanas» y la media en estrellas dibujadas (o «Aún no la has hecho»); tocarla
 * abre el historial. TalkBack lee las estrellas como «valoración media: 4 de 5 estrellas». Al lado, «✓ Lo hice».
 */
@Composable
internal fun CookStatsRow(stats: CookStats?, today: LocalDate, onHistory: () -> Unit, onLog: () -> Unit) {
    val c = LocalRecetarioColors.current
    val line = cookStatsLine(stats, today)
    val description = cookStatsDescription(stats, today)
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(role = Role.Button, onClickLabel = "Ver el historial", onClick = onHistory)
                .testTag("detail-stats"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                line,
                color = c.muted,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f, fill = false)
                    .then(if (description != line) Modifier.semantics { contentDescription = description } else Modifier),
            )
            stats?.averageRating?.let { average ->
                Spacer(Modifier.width(6.dp))
                DrawnStars(starFills(average), c.accent, Modifier.testTag("detail-stars"))
            }
        }
        Spacer(Modifier.width(8.dp))
        TagPill("✓ Lo hice", accent = true, onClick = onLog, modifier = Modifier.testTag("detail-log"))
    }
}

/** Historial de la receta en el diario: cada vez, con su fecha, estrellas, nota y foto. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BoxScope.HistoryPanel(
    stats: CookStats?,
    history: List<CookLogEntry>,
    today: LocalDate,
    photoStore: PhotoStore,
    emoji: String,
    onLog: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val c = LocalRecetarioColors.current
    BottomPanel(onDismiss, "diary-history") {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("Historial")
            Row(
                Modifier.clearAndSetSemantics { contentDescription = cookStatsDescription(stats, today) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MutedText(cookStatsLine(stats, today), Modifier.weight(1f, fill = false))
                stats?.averageRating?.let { average ->
                    Spacer(Modifier.width(6.dp))
                    DrawnStars(starFills(average), c.accent)
                }
            }
            history.forEachIndexed { i, entry ->
                if (i > 0) HorizontalDivider(color = c.line)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                    entry.photos.firstOrNull()?.let { photo ->
                        PhotoOrPlaceholder(
                            photo, photoStore, emoji,
                            Modifier.size(54.dp).clip(RoundedCornerShape(12.dp)),
                            contentDescription = "Foto de ese día",
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        // Si no caben juntas, las estrellas bajan (no se recortan).
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                            Text(logDateLabel(entry.date, today), color = c.ink, style = MaterialTheme.typography.bodyLarge)
                            RatingStars(entry.rating)
                        }
                        if (entry.note.isNotBlank()) MutedText(entry.note)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (onLog != null) {
                    TextButton(onClick = onLog, modifier = Modifier.heightIn(min = 48.dp)) { Text("✓ Lo hice", color = c.accent) }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cerrar", color = c.accent) }
            }
        }
    }
}
