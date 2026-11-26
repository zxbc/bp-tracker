package app.bp.ui.review

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.bp.data.Reading
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val dayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM")

private val numberStyle: TextStyle @Composable get() =
    MaterialTheme.typography.bodyLarge.copy(
        fontFamily = FontFamily.Monospace,
        fontFeatureSettings = "tnum",
    )

private val labelStyle: TextStyle @Composable get() =
    MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)

/**
 * Spreadsheet-like view of all readings: a column header, one band per day
 * (newest first) and one row per reading (newest first within the day).
 * Each row has a delete action; deletion is undoable via the snackbar.
 * An empty list is not rendered here — ReviewScreen shows its shared
 * EmptyState instead.
 */
@Composable
fun ReadingList(
    readings: List<Reading>,
    onDelete: (Reading) -> Unit,
) {
    // A scrollable Column, not a LazyColumn(fillMaxSize): the column wraps
    // its content so the card hugs a short list, and only scrolls once the
    // list grows past the card's height cap (ReviewScreen passes
    // weight(1f, fill = false)). A plain column is fine at this scale — a
    // personal log rarely holds more than a few hundred rows.
    val colors = MaterialTheme.colorScheme
    val groups = remember(readings) {
        readings
            .groupBy { Instant.ofEpochMilli(it.ts).atZone(ZoneId.systemDefault()).toLocalDate() }
            .entries
            .sortedByDescending { it.key }
            .map { it.value.sortedByDescending { r -> r.ts } }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceVariant)
                .padding(horizontal = 12.dp)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).testTag("list-header")) {
                Text("Date / time", style = labelStyle, color = colors.onSurfaceVariant)
            }
            Column(Modifier.width(48.dp)) {
                Text("SYS", style = labelStyle, color = colors.onSurfaceVariant)
            }
            Column(Modifier.width(48.dp)) {
                Text("DIA", style = labelStyle, color = colors.onSurfaceVariant)
            }
            Column(Modifier.width(48.dp)) {
                Text("HR", style = labelStyle, color = colors.onSurfaceVariant)
            }
            Spacer(Modifier.width(36.dp))
        }
        groups.forEach { dayReadings ->
            val day = Instant.ofEpochMilli(dayReadings.first().ts)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
            Text(
                day.format(dayFormatter),
                style = MaterialTheme.typography.labelMedium,
                color = colors.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceContainer)
                    .padding(horizontal = 12.dp)
                    .padding(top = 10.dp, bottom = 2.dp)
                    .testTag("day-header"),
            )
            dayReadings.forEach { r ->
                ReadingRow(r = r, onDelete = { onDelete(r) })
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ReadingRow(r: Reading, onDelete: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            Instant.ofEpochMilli(r.ts)
                .atZone(ZoneId.systemDefault())
                .toLocalTime()
                .format(timeFormatter),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .weight(1f)
                .testTag("list-row-time"),
        )
        Text(r.sys.toString(), style = numberStyle, textAlign = TextAlign.End, modifier = Modifier.width(48.dp))
        Text(r.dia.toString(), style = numberStyle, textAlign = TextAlign.End, modifier = Modifier.width(48.dp))
        Text(r.hr.toString(), style = numberStyle, textAlign = TextAlign.End, modifier = Modifier.width(48.dp))
        IconButton(onClick = onDelete, modifier = Modifier.width(36.dp)) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = "Delete reading",
                tint = colors.onSurfaceVariant,
            )
        }
    }
}
