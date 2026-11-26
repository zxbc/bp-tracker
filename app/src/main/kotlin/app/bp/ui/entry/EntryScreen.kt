package app.bp.ui.entry

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.KeyboardBackspace
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.bp.domain.BpParser.ParseResult
import app.bp.ui.EntryUi

/**
 * The entry screen: always the first thing you see. A live display of the
 * digit stream (auto-parsed as it grows), a numpad, and a confirm bar that
 * appears once the stream parses into a real reading.
 */
@Composable
fun EntryScreen(
    ui: EntryUi,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onEdit: () -> Unit,
    onCancel: () -> Unit,
    onOk: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        // Top row: title + always-available Cancel.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("New reading", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onCancel, enabled = ui.digits.isNotEmpty()) {
                Text("Cancel")
            }
        }

        // Live display, takes the room between the top row and the numpad.
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            EntryDisplay(ui)
        }

        Numpad(onDigit = onDigit, onBackspace = onBackspace)

        // Confirm bar: slides up exactly when the stream parses.
        AnimatedVisibility(
            visible = ui.result is ParseResult.Parsed && !ui.editing,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Save this reading?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = onCancel) { Text("Cancel") }
                    TextButton(onClick = onEdit) { Text("Edit") }
                    Button(
                        onClick = onOk,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("OK")
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}

/** The live digit-stream display and its parsed form. */
@Composable
private fun EntryDisplay(ui: EntryUi) {
    val digits = ui.digits
    val colors = MaterialTheme.colorScheme
    val mono = FontFamily.Monospace
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        when (val result = ui.result) {
            is ParseResult.Waiting -> {
                if (digits.isNotEmpty()) DigitRow(digits, colors.onSurface, mono)
                Text(
                    if (digits.isEmpty()) "Type your reading" else "keep typing…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }

            is ParseResult.Parsed -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        result.sys.toString(),
                        style = MaterialTheme.typography.displaySmall,
                        fontFamily = mono,
                        color = colors.primary,
                    )
                    Text(
                        " / ",
                        style = MaterialTheme.typography.displaySmall,
                        color = colors.onSurfaceVariant,
                    )
                    Text(
                        result.dia.toString(),
                        style = MaterialTheme.typography.displaySmall,
                        fontFamily = mono,
                        color = colors.primary,
                    )
                }
                Text(
                    "${result.hr} bpm",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = mono,
                    color = colors.onSurfaceVariant,
                )
            }

            is ParseResult.Unresolved -> {
                DigitRow(digits, colors.onSurface, mono)
                Text(
                    "not a valid reading yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.error,
                )
            }

            is ParseResult.TooLong -> {
                DigitRow(digits, colors.error, mono)
                Text(
                    "too many digits — backspace to fix",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.error,
                )
            }

            // Defensively unreachable with the current ranges (PLAN §4.3):
            // never guess, ask the user to keep typing or edit.
            is ParseResult.Ambiguous -> {
                DigitRow(digits, colors.onSurface, mono)
                Text(
                    "ambiguous — keep typing or press Edit",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.error,
                )
            }
        }

        if (ui.editing && digits.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                "editing — backspace to fix the digits",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

/** The digit stream, with the most recent digit highlighted. */
@Composable
private fun DigitRow(digits: String, color: Color, font: FontFamily) {
    val colors = MaterialTheme.colorScheme
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            digits.dropLast(1),
            style = MaterialTheme.typography.headlineMedium,
            fontFamily = font,
            color = color,
        )
        Surface(shape = RoundedCornerShape(10.dp), color = colors.primaryContainer) {
            Box(
                modifier = Modifier
                    .width(44.dp)
                    .height(52.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    digits.last().toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = font,
                    color = colors.onPrimaryContainer,
                )
            }
        }
    }
}

/** The numpad: 1–9, 0 and a wide backspace. Every key haptics + press animation. */
@Composable
private fun Numpad(onDigit: (Char) -> Unit, onBackspace: () -> Unit) {
    val view = LocalView.current
    val onKey: (Char) -> Unit = { d ->
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        onDigit(d)
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Row 1: 1 2 3
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f).fillMaxWidth()) {
            NumpadKey("1", Modifier.weight(1f).fillMaxHeight(), onClick = { onKey('1') })
            NumpadKey("2", Modifier.weight(1f).fillMaxHeight(), onClick = { onKey('2') })
            NumpadKey("3", Modifier.weight(1f).fillMaxHeight(), onClick = { onKey('3') })
        }
        // Row 2: 4 5 6
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f).fillMaxWidth()) {
            NumpadKey("4", Modifier.weight(1f).fillMaxHeight(), onClick = { onKey('4') })
            NumpadKey("5", Modifier.weight(1f).fillMaxHeight(), onClick = { onKey('5') })
            NumpadKey("6", Modifier.weight(1f).fillMaxHeight(), onClick = { onKey('6') })
        }
        // Row 3: 7 8 9
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f).fillMaxWidth()) {
            NumpadKey("7", Modifier.weight(1f).fillMaxHeight(), onClick = { onKey('7') })
            NumpadKey("8", Modifier.weight(1f).fillMaxHeight(), onClick = { onKey('8') })
            NumpadKey("9", Modifier.weight(1f).fillMaxHeight(), onClick = { onKey('9') })
        }
        // Row 4: 0 backspace [gap]
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f).fillMaxWidth()) {
            NumpadKey("0", Modifier.weight(1f).fillMaxHeight(), onClick = { onKey('0') })
            NumpadKey(
                label = "",
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onBackspace()
                },
            )
            Spacer(Modifier.weight(1f).fillMaxHeight())
        }
    }
}

/**
 * A numpad key with a springy press-down animation. The press state comes
 * from the interaction source so the scale feedback tracks real touches.
 */
@Composable
private fun NumpadKey(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 900f),
        label = "key-press",
    )
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.surfaceVariant,
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .scale(scale),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (label.isNotEmpty()) {
                Text(label, style = MaterialTheme.typography.headlineSmall, color = colors.onSurface)
            } else {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardBackspace,
                    contentDescription = "Backspace",
                    tint = colors.onSurface,
                )
            }
        }
    }
}
