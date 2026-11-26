package app.bp.ui

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
        val x = b.left.toInt().coerceIn(0, width - 1)
        val y = b.top.toInt().coerceIn(0, height - 1)
        val w = (b.right - b.left).toInt().coerceIn(1, width - x)
        val h = (b.bottom - b.top).toInt().coerceIn(1, height - y)
        val buf = IntArray(w * h)
        getPixels(buf, 0, w, x, y, w, h)
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
    }

    @Test
    fun newEntryButtonOnReviewScreenIsReadableInNightMode() {
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
}
