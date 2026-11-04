package com.cramdown.sorta

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import com.cramdown.sorta.ui.ideas.IdeasTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// Sorta opens on the page colour from edge to edge, bone by day and aubergine at night, on a
// phone, a tablet, and a foldable closed and open; it carries Sorta's ids, and sorta:// links
// open it. The same checks as LaunchTests on iOS.
//
// Since S05b a broad window opens on two panes, the Sort screen leading from the window's edge, so
// on the tablet its capture well covers the top-left point. iOS's rule (S05 A9) applies: only the
// two top points may move, only when the capture well (tag `capture`) covers them, which the test
// checks; a moved point keeps its height and reads the page half a gutter (8 dp) in from the nearer
// side. The count, the middle point and the bottom corners don't change.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LaunchTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test @Config(qualifiers = "notnight")
    fun theDayPageIsBone() = assertPage("F3EADB")

    @Test @Config(qualifiers = "night")
    fun theNightPageIsAubergine() = assertPage("281E2A")

    @Test @Config(qualifiers = "w1280dp-h800dp-land-notnight")
    fun aTabletsDayPageIsBone() = assertPage("F3EADB")

    @Test @Config(qualifiers = "w1280dp-h800dp-land-night")
    fun aTabletsNightPageIsAubergine() = assertPage("281E2A")

    // Closed, a foldable shows a tall, narrow cover screen; open, the size of Android's reference
    // unfolded foldable (PreviewScreenSizes).
    @Test @Config(qualifiers = "w344dp-h882dp-port-notnight")
    fun aClosedFoldablesDayPageIsBone() = assertPage("F3EADB")

    @Test @Config(qualifiers = "w344dp-h882dp-port-night")
    fun aClosedFoldablesNightPageIsAubergine() = assertPage("281E2A")

    @Test @Config(qualifiers = "w673dp-h841dp-port-notnight")
    fun anOpenFoldablesDayPageIsBone() = assertPage("F3EADB")

    @Test @Config(qualifiers = "w673dp-h841dp-port-night")
    fun anOpenFoldablesNightPageIsAubergine() = assertPage("281E2A")

    @Test fun itCarriesSortasIds() {
        val app = compose.activity
        assertEquals("com.cramdown.sorta", app.packageName)
        assertEquals("Sorta", app.applicationInfo.loadLabel(app.packageManager).toString())
    }

    @Test fun aSortaLinkOpensSorta() {
        val link = Intent(Intent.ACTION_VIEW, Uri.parse("sorta://idea")).addCategory(Intent.CATEGORY_BROWSABLE)
        val opens = compose.activity.packageManager.queryIntentActivities(link, 0).map { it.activityInfo.name }
        assertEquals(listOf(MainActivity::class.java.name), opens)
    }

    /**
     * The window drawn in software, read in the middle and near each corner. The window's own background is
     * the page colour too (the starting window's), so it's taken away first: what's read is SortaRoot's fill.
     */
    private fun assertPage(expected: String) {
        val window = compose.activity.window
        val root = window.decorView.rootView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle {
            window.setBackgroundDrawable(null)
            root.draw(Canvas(bitmap))
        }
        val well = compose.onAllNodesWithTag(IdeasTags.CAPTURE).onFirst().fetchSemanticsNode().boundsInWindow
        val margin = 8 * compose.activity.resources.displayMetrics.density
        val points = listOf(0.5f to 0.5f, 0.02f to 0.02f, 0.98f to 0.02f, 0.02f to 0.98f, 0.98f to 0.98f)
        assertEquals("five points", 5, points.size)
        for ((k, point) in points.withIndex()) {
            val (fx, fy) = point
            var x = root.width * fx
            val y = root.height * fy
            if (k in TOP_POINTS && well.contains(Offset(x, y))) {
                // under the capture well: the same height, half a gutter in from the nearer side
                x = if (fx < 0.5f) margin else root.width - margin
                assertFalse("the moved point $x, $y is clear of the capture well $well", well.contains(Offset(x, y)))
            }
            val seen = "%06X".format(bitmap.getPixel(x.toInt(), y.toInt()) and 0xFFFFFF)
            assertEquals("the page at $x, $y px, from $fx, $fy (window ${root.width} x ${root.height})", expected, seen)
        }
    }

    private companion object {
        /** The two points the capture well may cover, the only ones that may move. */
        val TOP_POINTS = setOf(1, 2)
    }
}
