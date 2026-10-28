package com.cramdown.sorta

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// Sorta opens on the page colour from edge to edge, bone by day and aubergine at night, on a
// phone and on a tablet; it carries Sorta's ids, and sorta:// links open it. The same checks
// as LaunchTests on iOS.
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

    /** The window drawn in software, as it would be on screen, read in the middle and near each corner. */
    private fun assertPage(expected: String) {
        val root = compose.activity.window.decorView.rootView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { root.draw(Canvas(bitmap)) }
        for ((fx, fy) in listOf(0.5f to 0.5f, 0.02f to 0.02f, 0.98f to 0.02f, 0.02f to 0.98f, 0.98f to 0.98f)) {
            val seen = "%06X".format(bitmap.getPixel((root.width * fx).toInt(), (root.height * fy).toInt()) and 0xFFFFFF)
            assertEquals("the page at $fx, $fy (window ${root.width} x ${root.height})", expected, seen)
        }
    }
}
