package com.cramdown.sorta.ui.ideas

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.cramdown.sorta.R
import com.cramdown.sorta.ui.SortaTheme
import com.cramdown.sorta.ui.ward.WardChipButton
import com.cramdown.sorta.ui.ward.WardToken
import com.cramdown.sorta.ui.ward.WindowShapeHost
import com.cramdown.sorta.ui.wardColor
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// S05's next step, item 4 (S05-result.md): the editor's folder chip has its glyph in the accent, as
// iOS's (NoteEditorView.folderChip: Color.wardGlyph), through the kit chip's new iconTint; a chip
// given no tint keeps its glyph in its words' ink, as before. The window is drawn by day in software,
// as WardDarkFacesTest draws it, and each chip read pixel by pixel: a pixel counts as the accent's
// when each of its channels is within 24 of it.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h914dp-port-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EditorChipGlyphTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private var accent = Color.Unspecified

    /** At least this many pixels more of the accent: the folder glyph, 18 dp at xxhdpi (about 550 when drawn). */
    private val GLYPH = 300

    private fun accentPixels(node: SemanticsNodeInteraction): Int {
        val target = accent.toArgb()
        val b = node.fetchSemanticsNode().boundsInWindow
        val root = compose.activity.window.decorView.rootView
        val bitmap = android.graphics.Bitmap.createBitmap(root.width, root.height, android.graphics.Bitmap.Config.ARGB_8888)
        compose.runOnIdle { root.draw(android.graphics.Canvas(bitmap)) }
        var n = 0
        for (y in b.top.toInt().coerceAtLeast(0) until b.bottom.toInt().coerceAtMost(bitmap.height))
            for (x in b.left.toInt().coerceAtLeast(0) until b.right.toInt().coerceAtMost(bitmap.width)) {
            val c = bitmap.getPixel(x, y)
            val near = listOf(16, 8, 0).all { shift -> kotlin.math.abs(((c shr shift) and 255) - ((target shr shift) and 255)) <= 24 }
            if (near) n++
        }
        return n
    }

    @Test fun aKitChipsGlyphIsItsWordsInkUnlessGivenATint() {
        compose.setContent {
            SortaTheme(dark = false) {
                accent = wardColor(WardToken.accent)
                Column {
                    WardChipButton("Garden", {}, Modifier.testTag("plain"), icon = Icons.Outlined.Folder, role = Role.Button)
                    WardChipButton("Garden", {}, Modifier.testTag("tinted"), icon = Icons.Outlined.Folder, role = Role.Button,
                        iconTint = wardColor(WardToken.accent))
                }
            }
        }
        compose.waitForIdle()
        val plain = accentPixels(compose.onNode(hasTestTag("plain")))
        val tinted = accentPixels(compose.onNode(hasTestTag("tinted")))
        // the chip's own edge is near the accent too, so the glyph is what one has more of than the other
        assertTrue("a tinted chip's glyph is the accent: $tinted pixels of it, against $plain untinted", tinted - plain >= GLYPH)
    }

    @Test fun theEditorsFolderChipHasItsGlyphInTheAccent() {
        val model = IdeasModel.open(IdeasFixture.store(compose.activity.cacheDir, true))
        compose.setContent {
            SortaTheme(dark = false) {
                accent = wardColor(WardToken.accent)
                // the kit's chip as the editor's would be with no tint, above the editor itself
                Column(Modifier.fillMaxSize()) {
                    WardChipButton(IdeasFixture.garden.name, {}, Modifier.testTag("plain"), icon = Icons.Outlined.Folder, role = Role.Button)
                    Box(Modifier.weight(1f)) { WindowShapeHost { IdeasView(model, start = IdeasStart(editing = IdeasFixture.shed.id)) } }
                }
            }
        }
        compose.waitForIdle()
        val chip = compose.onNode(hasContentDescription(compose.activity.getString(R.string.editor_chip_spoken, IdeasFixture.garden.name)))
        val seen = accentPixels(chip)
        val plain = accentPixels(compose.onNode(hasTestTag("plain")))
        assertTrue("the editor's chip glyph is the accent: $seen pixels of it, against $plain on an untinted chip", seen - plain >= GLYPH)
    }
}
