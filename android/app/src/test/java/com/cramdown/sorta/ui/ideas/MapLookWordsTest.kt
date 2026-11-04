package com.cramdown.sorta.ui.ideas

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.cramdown.sorta.R
import com.cramdown.sorta.ideas.GraphTheme
import com.cramdown.sorta.ideas.MapLook
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Map mode's looks as the chip names them (S05b), as iOS's MapLookWordsTests checks them, and the
// look kept in IdeasSettings: 2D keeps the theme, so 3D comes back to it.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MapLookWordsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before fun clear() {
        context.getSharedPreferences(IdeasSettings.FILE, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun theLooksAreNamed() {
        assertEquals(listOf("Space", "Neuron", "2D", "Performance"), MapLook.entries.map { context.getString(it.title) })
    }

    @Test fun theChipIsSpokenAsTheTheme() {
        assertEquals("Theme, 2D", context.getString(R.string.look_chip_spoken, context.getString(MapLook.TWO_D.title)))
        assertEquals("change how the map looks", context.getString(R.string.look_chip_hint))
    }

    @Test fun theLookIsKeptAnd2DKeepsTheTheme() {
        val settings = IdeasSettings(context)
        assertEquals(MapLook.SPACE, settings.look)
        settings.setLook(MapLook.NEURON)
        settings.setLook(MapLook.TWO_D)
        assertEquals(MapLook.TWO_D, IdeasSettings(context).look)
        assertEquals(GraphTheme.NEURONS.rawValue, IdeasSettings(context).theme)
        settings.setLook(MapLook.PERFORMANCE)
        assertEquals(MapLook.PERFORMANCE, IdeasSettings(context).look)
    }
}
