package app.bp.ui.entry

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.bp.domain.BpParser
import app.bp.ui.EntryUi
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Drives the real entry screen with synthetic clicks on a phone-sized
 * window (Robolectric). The v0.1.0 layout bug — weighted numpad rows filled
 * the whole screen, squeezed the live display to zero height and pushed the
 * confirm bar off-screen — is caught by the display-visibility assertions.
 */
@RunWith(RobolectricTestRunner::class)
// Robolectric's qualifier parser requires the density with a "dpi" suffix.
@Config(sdk = [34], qualifiers = "w412dp-h915dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
class EntryScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private class Harness {
        // Snapshot state: a plain var would not invalidate composition, so the
        // clicks would update state but the screen would never re-render.
        val digits = androidx.compose.runtime.mutableStateOf("")
        var okClicks = 0
        val ui: EntryUi
            get() = EntryUi(digits.value, editing = false, result = BpParser.parse(digits.value))
    }

    private fun launch(h: Harness, onOk: () -> Unit = {}) {
        compose.setContent {
            EntryScreen(
                ui = h.ui,
                onDigit = { h.digits.value += it },
                onBackspace = { h.digits.value = h.digits.value.dropLast(1) },
                onEdit = {},
                onCancel = { h.digits.value = "" },
                onOk = onOk,
            )
        }
    }

    @Test
    fun displayAndNumpadAreVisibleBeforeAnyInput() {
        val h = Harness()
        launch(h)
        // If the numpad squeezed the display, this fails: the hint would have
        // zero size.
        compose.onNodeWithText("Type your reading").assertIsDisplayed()
        compose.onNodeWithTag("key-1").assertIsDisplayed()
        compose.onNodeWithTag("key-backspace").assertIsDisplayed()
    }

    @Test
    fun typingSevenDigitsShowsParsedReadingAndConfirmBar() {
        val h = Harness()
        launch(h, onOk = { h.okClicks++ })

        "1218171".forEach { d ->
            compose.onNodeWithTag("key-$d").performClick()
            compose.waitForIdle()
        }

        // Parsed groups and the confirm bar are on screen.
        compose.onNodeWithText("121").assertIsDisplayed()
        compose.onNodeWithText("81").assertIsDisplayed()
        compose.onNodeWithText("71").assertIsDisplayed()
        compose.onNodeWithText("Save this reading?").assertIsDisplayed()

        compose.onNodeWithTag("confirm-ok").performClick()
        compose.waitForIdle()
        assertEquals(1, h.okClicks)
    }

    @Test
    fun typedDigitsAreShownLiveAndBackspaceClears() {
        val h = Harness()
        launch(h)

        compose.onNodeWithTag("key-1").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("digit-current").assertIsDisplayed()
        compose.onNodeWithText("keep typing…").assertIsDisplayed()

        compose.onNodeWithTag("key-backspace").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Type your reading").assertIsDisplayed()
    }
}
