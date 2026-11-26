package app.bp.ui.review

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.bp.data.Reading

/** The two ways to look at history. Chart is the default. */
enum class ReviewTab { Chart, List }

@Composable
fun ReviewScreen(
    chartReadings: List<Reading>,
    allReadings: List<Reading>,
    onNewEntry: () -> Unit,
    onDelete: (Reading) -> Unit,
    onRestore: (Reading) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(ReviewTab.Chart) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme

    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Blood pressure", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Last 7 days",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                FilledTonalButton(onClick = onNewEntry) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("New entry")
                }
            }
            Spacer(Modifier.height(12.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = tab == ReviewTab.Chart,
                    onClick = { tab = ReviewTab.Chart },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) {
                    Text("Chart")
                }
                SegmentedButton(
                    selected = tab == ReviewTab.List,
                    onClick = { tab = ReviewTab.List },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) {
                    Text("List")
                }
            }
            Spacer(Modifier.height(16.dp))
            when (tab) {
                ReviewTab.Chart -> {
                    if (chartReadings.isEmpty()) EmptyState(onNewEntry)
                    else ContentCard() {
                        BpChart(readings = chartReadings)
                    }
                }
                ReviewTab.List -> {
                    if (allReadings.isEmpty()) EmptyState(onNewEntry)
                    else ContentCard(Modifier.weight(1f, fill = false)) {
                        ReadingList(
                            readings = allReadings,
                            onDelete = { r ->
                                onDelete(r)
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "Reading deleted",
                                        actionLabel = "Undo",
                                    )
                                    if (result == SnackbarResult.ActionPerformed) onRestore(r)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * The delineated background for chart and list: a raised, bordered card so
 * the content reads as a self-contained surface even when there are only a
 * few entries.
 *
 * The card wraps its content — no `fillMaxSize` inside — so with few entries
 * the border hugs the content instead of extending down the page (v0.1.3
 * device report). Callers that must cap the height pass
 * `weight(1f, fill = false)`.
 */
@Composable
private fun ContentCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(modifier = Modifier.padding(12.dp)) { content() }
    }
}

@Composable
private fun EmptyState(onNewEntry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Outlined.MonitorHeart,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "No readings yet",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onNewEntry) {
            Text("Add your first reading")
        }
    }
}
