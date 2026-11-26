package app.bp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.bp.data.AppDatabase
import app.bp.data.ReadingRepository
import app.bp.ui.BpViewModel
import app.bp.ui.Screen
import app.bp.ui.entry.EntryScreen
import app.bp.ui.review.ReviewScreen
import app.bp.ui.theme.BpTrackerTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val activity = this
        val vm: BpViewModel = viewModel {
            viewModelFactory {
                initializer {
                    BpViewModel(ReadingRepository(AppDatabase.get(activity).readingDao()))
                }
            }
        }
        setContent {
            BpTrackerTheme {
                BpApp(vm)
            }
        }
    }
}

/**
 * The whole app in one place: two screens (Entry, Review) switched by state
 * with a crossfade. No navigation library — there is nothing to route.
 */
@Composable
fun BpApp(vm: BpViewModel) {
    val screen by vm.screen.collectAsStateWithLifecycle()
    val entryUi by vm.entryUi.collectAsStateWithLifecycle()
    val chartReadings by vm.chartReadings.collectAsStateWithLifecycle()
    val allReadings by vm.allReadings.collectAsStateWithLifecycle()
    Crossfade(
        targetState = screen,
        animationSpec = tween(durationMillis = 250),
        modifier = Modifier.fillMaxSize(),
    ) { s ->
        when (s) {
            Screen.Entry -> EntryScreen(
                ui = entryUi,
                onDigit = vm::digit,
                onBackspace = vm::backspace,
                onEdit = vm::startEditing,
                onCancel = vm::cancelEntry,
                onOk = vm::confirmEntry,
            )
            Screen.Review -> ReviewScreen(
                chartReadings = chartReadings,
                allReadings = allReadings,
                onNewEntry = vm::newEntry,
                onDelete = vm::deleteReading,
                onRestore = vm::restoreReading,
            )
        }
    }
}
