package app.bp.ui

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.bp.data.Reading
import app.bp.domain.BpParser
import app.bp.ui.entry.EntryScreen
import app.bp.ui.review.ReviewScreen
import app.bp.ui.theme.BpTrackerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Dark-mode rendering regression test.
 *
 * Robolectric's "night" qualifier makes the configuration report night mode,
 * so [BpTrackerTheme] resolves exactly as it does on a real phone in dark
 * mode; dynamicColor is forced off so the assertions run against the
 * deterministic TealDark fallback palette instead of wallpaper-derived
 * colors.
 *
 * The v0.1.1 bug this guards: text stayed dark (a black "New entry" button
 * that was barely visible) because the theme did not adapt. The pixel
 * assertions below fail in that case, because they look at the actual
 * rendered colors, not the declared scheme.
 *
 * Capturing real pixels requires bypassing `captureToImage()`: it posts a
 * redraw runnable to the main looper and waits on the Compose test clock
 * only, so it times out under Robolectric's paused looper. Instead we idle
 * the looper and call [PixelCopy.request] directly; Robolectric's shadow
 * renders the window and delivers the listener callback.
 */
@RunWith(RobolectricTestRunner::class)
// Robolectric's qualifier grammar is strictly ordered (aapt2's
// ConfigDescription): uiModeNight ("night") must precede density ("420dpi").
@Config(sdk = [34], qualifiers = "w412dp-h915dp-night-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DarkModeTest {

    @get:Rule
    val compose = createAndroidComposeRule(ComponentActivity::class.java)

    private fun luminance(argb: Int): Float {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f
    }

    /** Renders the activity window to a bitmap; coordinates are window px. */
    private fun captureWindow(): Bitmap {
        compose.waitForIdle()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        val decor = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
        val latch = CountDownLatch(1)
        PixelCopy.request(
            compose.activity.window,
            null,
            bitmap,
            { latch.countDown() },
            Handler(Looper.getMainLooper()),
        )
        // The shadow may deliver the callback through the main looper.
        if (!latch.await(250, TimeUnit.MILLISECONDS)) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertTrue("pixel copy did not complete", latch.await(5, TimeUnit.SECONDS))
        }
        return bitmap
    }

    /** Highest luminance inside the node's window-space bounds. */
    private fun Bitmap.maxLuminanceIn(node: SemanticsNode): Float {
        val b = node.boundsInWindow
        return maxLuminanceIn(
            b.left.toInt(), b.top.toInt(),
            (b.right - b.left).toInt(), (b.bottom - b.top).toInt(),
        )
    }

    /** Highest luminance inside an arbitrary window-space rect. */
    private fun Bitmap.maxLuminanceIn(x: Int, y: Int, w: Int, h: Int): Float {
        val x0 = x.coerceIn(0, width - 1)
        val y0 = y.coerceIn(0, height - 1)
        val w0 = w.coerceIn(1, width - x0)
        val h0 = h.coerceIn(1, height - y0)
        val buf = IntArray(w0 * h0)
        getPixels(buf, 0, w0, x0, y0, w0, h0)
        return buf.maxOf { luminance(it) }
    }

    private fun entryHarness() {
        val digits = mutableStateOf("")
        compose.setContent {
            BpTrackerTheme(dynamicColor = false) {
                EntryScreen(
                    ui = EntryUi(digits.value, editing = false, result = BpParser.parse(digits.value)),
                    onDigit = { digits.value += it },
                    onBackspace = { digits.value = digits.value.dropLast(1) },
                    onEdit = {},
                    onCancel = { digits.value = "" },
                    onOk = {},
                )
            }
        }
    }

    @Test
    fun entryScreenHasDarkBackgroundAndLightTitleInNightMode() {
        entryHarness()

        val image = captureWindow()

        // Sample below the status bar so the platform window chrome cannot
        // leak in: the entry background is the Compose scheme's dark
        // TealDark background (0xFF111413, luminance ~0.007).
        val bg = image.getPixel(4, 200)
        assertTrue(
            "entry background should be dark in night mode, was 0x${bg.toString(16)}",
            luminance(bg) < 0.2f,
        )

        // The title must render light; black text on a dark window was the
        // v0.1.1 report.
        val title = compose.onNodeWithText("New reading").fetchSemanticsNode()
        assertTrue(
            "'New reading' title should be light in night mode (max luminance ${image.maxLuminanceIn(title)})",
            image.maxLuminanceIn(title) > 0.55f,
        )

        // The footer strip (v0.1.2) carries the format hint:
        // onSurfaceVariant @ 0.8 over the dark background must still read.
        val footer = compose.onNodeWithTag("entry-footer").fetchSemanticsNode()
        assertTrue(
            "entry footer hint should be legible in night mode (max luminance ${image.maxLuminanceIn(footer)})",
            image.maxLuminanceIn(footer) > 0.4f,
        )
    }

    @Test
    fun currentDigitChipIsDarkTealInNightMode() {
        entryHarness()
        compose.onNodeWithTag("key-1").performClick()
        compose.waitForIdle()

        val image = captureWindow()
        val chip = compose.onNodeWithTag("digit-current").fetchSemanticsNode()

        // Sample the chip's top-left corner (clear of the digit glyph and of
        // the rounded corner): it must be the dark-teal container
        // (TealDark primaryContainer 0xFF00514A, luminance ~0.25), not the
        // light-theme container 0xFF99F6E4 (luminance ~0.87).
        val b = chip.boundsInWindow
        val x = b.left.toInt() + 10
        val y = b.top.toInt() + 10
        val buf = IntArray(9 * 9)
        image.getPixels(buf, 0, 9, x, y, 9, 9)
        val maxLum = buf.maxOf { luminance(it) }
        assertTrue(
            "digit chip should be dark teal in night mode (corner max luminance $maxLum)",
            maxLum < 0.5f,
        )

        // An untouched key's digit renders light (onSurface on surfaceVariant).
        val key = compose.onNodeWithTag("key-5").fetchSemanticsNode()
        assertTrue(
            "numpad digit should be light in night mode (max luminance ${image.maxLuminanceIn(key)})",
            image.maxLuminanceIn(key) > 0.55f,
        )
    }

    private fun reviewScreenHarness() {
        val readings = (1..3).map {
            Reading(ts = System.currentTimeMillis() - it * 3_600_000L, sys = 118, dia = 76, hr = 70)
        }
        compose.setContent {
            BpTrackerTheme(dynamicColor = false) {
                ReviewScreen(
                    chartReadings = readings,
                    allReadings = readings,
                    onNewEntry = {},
                    onDelete = {},
                    onRestore = {},
                )
            }
        }
    }

    @Test
    fun newEntryButtonOnReviewScreenIsReadableInNightMode() {
        reviewScreenHarness()

        val image = captureWindow()

        val bg = image.getPixel(4, image.height / 2)
        assertTrue(
            "review background should be dark in night mode, was 0x${bg.toString(16)}",
            luminance(bg) < 0.2f,
        )

        // The user's exact complaint: a black "New entry" text on the dark
        // theme. The button label must render light (onSecondaryContainer
        // 0xFFCCE8E2 in TealDark).
        val btnText = compose.onNodeWithText("New entry").fetchSemanticsNode()
        assertTrue(
            "'New entry' text should be light in night mode (max luminance ${image.maxLuminanceIn(btnText)})",
            image.maxLuminanceIn(btnText) > 0.55f,
        )
    }

    @Test
    fun entryErrorTextIsLegibleInNightMode() {
        entryHarness()
        // Ten digits: the parser returns TooLong before any parsing attempt.
        for (d in "1234567890") compose.onNodeWithTag("key-$d").performClick()
        compose.waitForIdle()

        val image = captureWindow()
        val err = compose.onNodeWithText("too many digits — backspace to fix").fetchSemanticsNode()
        assertTrue(
            "error text should be light in night mode (max luminance ${image.maxLuminanceIn(err)})",
            image.maxLuminanceIn(err) > 0.5f,
        )
    }

    @Test
    fun chartAxisLabelsAreLegibleInNightMode() {
        reviewScreenHarness()

        val image = captureWindow()
        val canvas = compose.onNodeWithTag("bp-chart").fetchSemanticsNode()
        val b = canvas.boundsInWindow

        // Y-axis labels: right-aligned in the 30 dp left gutter, one per
        // gridline. v0.1.2 drew them with the gridline paint (onSurface at
        // 10% alpha — luminance ~0.1), the "very low contrast" device
        // report; they must be onSurfaceVariant (0xFFC4C7C7) now.
        val yLabels = image.maxLuminanceIn(
            x = b.left.toInt(),
            y = b.top.toInt() + 10,
            w = 70,
            h = (b.bottom - b.top).toInt() - 115,
        )
        assertTrue(
            "chart y-axis labels should be legible in night mode (max luminance $yLabels)",
            yLabels > 0.45f,
        )

        // X-axis labels (weekday + day number) sit in the 40 dp bottom
        // gutter; the strip starts below the plot so bars cannot leak in.
        val xLabels = image.maxLuminanceIn(
            x = b.left.toInt() + 80,
            y = b.bottom.toInt() - 95,
            w = (b.right - b.left).toInt() - 90,
            h = 85,
        )
        assertTrue(
            "chart x-axis labels should be legible in night mode (max luminance $xLabels)",
            xLabels > 0.45f,
        )

        // v0.1.3 layout fix: the card wraps the chart. A pixel below the
        // card must be the screen background (0xFF111413), not the card's
        // surfaceContainerLow fill (0xFF1A1D1B) — the "border fills the
        // entire page" device report.
        val below = image.getPixel(
            b.left.toInt() + 20,
            (b.bottom + 60).toInt().coerceAtMost(image.height - 1),
        )
        assertTrue(
            "card should wrap the chart (pixel below card is 0x${below.toString(16)}, expected background 0xff111413)",
            // 0xFF111413 does not fit in a positive Int; compare the low 32 bits.
            (below.toLong() and 0xFFFFFFFFL) == 0xFF111413L,
        )
    }

    @Test
    fun listTextIsLegibleInNightMode() {
        reviewScreenHarness()
        compose.onNodeWithText("List").performClick()
        compose.waitForIdle()

        val image = captureWindow()

        // Column-header labels (onSurfaceVariant on surfaceVariant).
        val header = compose.onNodeWithTag("list-header").fetchSemanticsNode()
        assertTrue(
            "list header labels should be legible in night mode (max luminance ${image.maxLuminanceIn(header)})",
            image.maxLuminanceIn(header) > 0.5f,
        )

        // Day band header (primary on surfaceContainer).
        val day = compose.onAllNodesWithTag("day-header")[0].fetchSemanticsNode()
        assertTrue(
            "list day header should be legible in night mode (max luminance ${image.maxLuminanceIn(day)})",
            image.maxLuminanceIn(day) > 0.5f,
        )

        // Row time (plain Text → the card's onSurface content color).
        val time = compose.onAllNodesWithTag("list-row-time")[0].fetchSemanticsNode()
        assertTrue(
            "list row text should be legible in night mode (max luminance ${image.maxLuminanceIn(time)})",
            image.maxLuminanceIn(time) > 0.5f,
        )
    }
}
