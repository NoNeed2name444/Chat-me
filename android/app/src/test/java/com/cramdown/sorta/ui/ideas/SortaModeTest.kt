package com.cramdown.sorta.ui.ideas

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Sorta's Sort / Map switch (S05b, task 1): two modes in this order, with iOS's identifiers, the
// glyphs of iOS's list.bullet and cube.transparent, and their words from strings.xml.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SortaModeTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun theModesAreSortThenMap() {
        assertEquals(listOf(SortaMode.SORT, SortaMode.MAP), SortaMode.entries)
        assertEquals(listOf("sort", "map"), SortaMode.entries.map { it.rawValue })
    }

    @Test fun theIdentifiersAreIosOnes() {
        assertEquals(listOf("mode-sort", "mode-map"), SortaMode.entries.map { it.identifier })
        assertEquals("modeSwitcherToggle", SortaMode.TOGGLE)
    }

    @Test fun theGlyphsMatchIos() {
        assertSame(Icons.AutoMirrored.Outlined.List, SortaMode.SORT.icon)
        assertSame(Icons.Outlined.ViewInAr, SortaMode.MAP.icon)
    }

    @Test fun theWordsComeFromStrings() {
        assertEquals(listOf("Sort", "Map"), SortaMode.entries.map { context.getString(it.title) })
        assertEquals(listOf("Sort ideas", "Map of ideas"), SortaMode.entries.map { context.getString(it.spoken) })
    }
}
