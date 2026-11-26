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
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.bp.domain.BpParser.ParseResult
import app.bp.ui.EntryUi

/**
 * The entry screen: always the first thing you see. A prominent live display
 * of the digit stream (auto-parsed as it grows), a compact dialer-style
 * numpad anchored at the bottom (thumb-reachable one-handed), and a confirm
 * bar that slides in once the stream parses into a real reading.
 *
 * Layout rule that keeps this screen sane: the numpad has FIXED key heights.
 * Only the display takes the flexible space (weight 1f). A weighted numpad
 * fills the whole screen and squeezes the display to zero — that shipped in
 * v0.1.0 and broke entry on device.
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
    // Surface, not a bare Column: it provides LocalContentColor = onSurface,
    // the color plain Texts fall back to. Without it they render Color.Black
    // and are invisible on a dark background (the v0.1.2 device report).
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 20.dp),
        ) {
            // Top row: title + a Cancel that is always visible and easy to hit.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("New reading", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.weight(1f))
                FilledTonalButton(
                    onClick = onCancel,
                    enabled = ui.digits.isNotEmpty(),
                    modifier = Modifier.testTag("cancel-top"),
                ) {
                    Text("Cancel")
                }
            }

            Spacer(Modifier.height(8.dp))

            // Live display: the star of the screen, takes all remaining space.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                EntryDisplay(ui)
            }

            // Confirm bar: slides in exactly when the stream parses. It sits in
            // the layout flow above the numpad, so it can never be pushed
            // off-screen.
            AnimatedVisibility(
                visible = ui.result is ParseResult.Parsed && !ui.editing,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            ) {
                ConfirmBar(onCancel = onCancel, onEdit = onEdit, onOk = onOk)
            }

            Numpad(onDigit = onDigit, onBackspace = onBackspace)

            // Non-interactive strip below the last key row. It lifts the keys
            // off the very bottom edge so they never sit in the zone where a
            // gesture-nav bar reveals itself, and it carries the format hint
            // that was in the empty display (which disappears after the first
            // digit, so the hint had a short life there).
            EntryFooter()
            }
    }
}

/** Muted format example below the numpad; not a button, never competes for touch. */
@Composable
private fun EntryFooter() {
    Text(
        "e.g. 1218171 → 121 / 81 / 71",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        modifier = Modifier
            .testTag("entry-footer")
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 16.dp),
    )
}

/** "Save this reading?" with Cancel / Edit / OK. */
@Composable
private fun ConfirmBar(
    onCancel: () -> Unit,
    onEdit: () -> Unit,
    onOk: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Save this reading?",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = onCancel, modifier = Modifier.testTag("confirm-cancel")) { Text("Cancel") }
            TextButton(onClick = onEdit, modifier = Modifier.testTag("confirm-edit")) { Text("Edit") }
            Button(
                onClick = onOk,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f).testTag("confirm-ok"),
            ) {
                Icon(Icons.Filled.Check, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("OK")
            }
        }
    }
}

/** The live digit-stream display and its parsed form. */
@Composable
private fun EntryDisplay(ui: EntryUi) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (val result = ui.result) {
            is ParseResult.Waiting -> {
                if (ui.digits.isEmpty()) {
                    Text(
                        "Type your reading",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurfaceVariant,
                    )
                } else {
                    DigitStream(ui.digits, colors.onSurface)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "keep typing…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            }

            is ParseResult.Parsed -> ParsedGroups(result)

            is ParseResult.Unresolved -> {
                DigitStream(ui.digits, colors.error)
                Spacer(Modifier.height(10.dp))
                Text(
                    "not a valid reading — backspace and retry",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.error,
                )
            }

            is ParseResult.TooLong -> {
                DigitStream(ui.digits, colors.error)
                Spacer(Modifier.height(10.dp))
                Text(
                    "too many digits — backspace to fix",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.error,
                )
            }

            // Defensively unreachable with the current ranges (PLAN §4.3):
            // never guess, ask the user to keep typing or edit.
            is ParseResult.Ambiguous -> {
                DigitStream(ui.digits, colors.onSurface)
                Spacer(Modifier.height(10.dp))
                Text(
                    "ambiguous — keep typing or press Edit",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.error,
                )
            }
        }

        if (ui.editing && ui.digits.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                "editing — backspace to fix the digits",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

/** The parsed reading as three labeled groups: SYS / DIA / HR. */
@Composable
private fun ParsedGroups(result: ParseResult.Parsed) {
    val colors = MaterialTheme.colorScheme
    val mono = FontFamily.Monospace
    Row(
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ValueGroup(result.sys.toString(), "SYS", colors.primary, mono)
        ValueGroup(result.dia.toString(), "DIA", colors.primary, mono)
        ValueGroup(result.hr.toString(), "HR", colors.onSurfaceVariant, mono)
    }
}

@Composable
private fun ValueGroup(
    value: String,
    label: String,
    valueColor: Color,
    font: FontFamily,
) {
    val colors = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.displaySmall,
            fontFamily = font,
            color = valueColor,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
        )
    }
}

/** The raw digit stream, with the most recent digit highlighted. */
@Composable
private fun DigitStream(digits: String, color: Color) {
    val colors = MaterialTheme.colorScheme
    val mono = FontFamily.Monospace
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            digits.dropLast(1),
            style = MaterialTheme.typography.displayMedium,
            fontFamily = mono,
            color = color,
        )
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = colors.primaryContainer,
            modifier = Modifier.testTag("digit-current"),
        ) {
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(58.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    digits.last().toString(),
                    style = MaterialTheme.typography.displayMedium,
                    fontFamily = mono,
                    color = colors.onPrimaryContainer,
                )
            }
        }
    }
}

/**
 * The numpad: 1–9, 0 and a backspace, dialer-style at the bottom of the
 * screen. Keys are a fixed 56 dp tall so the pad never competes with the
 * display for space, and the whole pad stays in the thumb zone. Every key
 * haptics + press animation.
 */
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
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            NumpadKey("1", Modifier.weight(1f).height(56.dp).testTag("key-1"), onClick = { onKey('1') })
            NumpadKey("2", Modifier.weight(1f).height(56.dp).testTag("key-2"), onClick = { onKey('2') })
            NumpadKey("3", Modifier.weight(1f).height(56.dp).testTag("key-3"), onClick = { onKey('3') })
        }
        // Row 2: 4 5 6
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            NumpadKey("4", Modifier.weight(1f).height(56.dp).testTag("key-4"), onClick = { onKey('4') })
            NumpadKey("5", Modifier.weight(1f).height(56.dp).testTag("key-5"), onClick = { onKey('5') })
            NumpadKey("6", Modifier.weight(1f).height(56.dp).testTag("key-6"), onClick = { onKey('6') })
        }
        // Row 3: 7 8 9
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            NumpadKey("7", Modifier.weight(1f).height(56.dp).testTag("key-7"), onClick = { onKey('7') })
            NumpadKey("8", Modifier.weight(1f).height(56.dp).testTag("key-8"), onClick = { onKey('8') })
            NumpadKey("9", Modifier.weight(1f).height(56.dp).testTag("key-9"), onClick = { onKey('9') })
        }
        // Row 4: 0 backspace [gap]
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            NumpadKey("0", Modifier.weight(1f).height(56.dp).testTag("key-0"), onClick = { onKey('0') })
            NumpadKey(
                label = "",
                modifier = Modifier.weight(1f).height(56.dp).testTag("key-backspace"),
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onBackspace()
                },
            )
            Spacer(Modifier.weight(1f).height(56.dp))
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
        shape = RoundedCornerShape(18.dp),
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
