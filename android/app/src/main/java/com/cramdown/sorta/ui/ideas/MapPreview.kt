package com.cramdown.sorta.ui.ideas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.cramdown.sorta.ideas.GraphTheme
import com.cramdown.sorta.ideas.SampleIdeas
import com.cramdown.sorta.ui.ward.WardToken
import com.cramdown.sorta.ui.wardColor

/**
 * The maps on the sample ideas (SampleIdeas), for the instrumented tests and the renders, as
 * iOS's GraphPreviewRoot is: [look] is "space", "neurons" or "performance" for the 3D map, or
 * "board" for the flat board. MainActivity shows it only under its map-preview extra; no
 * screen a user reaches opens it. Until step 4 brings the editor, opening an idea does nothing.
 */
@Composable fun MapPreview(look: String) {
    val context = LocalContext.current
    val model = remember { SampleIdeas.book().let { IdeasModel(it.notes, it.folders) } }
    val settings = remember { IdeasSettings(context) }
    // what the navigation bar covers, which the tools stay above, as iOS's keep to its safe area
    val hidden = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()
    Box(Modifier.fillMaxSize().background(wardColor(WardToken.background))) {
        if (look == "board") {
            // the board keeps clear of the status bar and any cutout, as iOS's stays in its safe
            // area, and runs on under the navigation bar
            Box(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
                IdeaBoard(model.book, settings, hidden = hidden, open = {})
            }
        } else {
            val theme = GraphTheme.entries.firstOrNull { it.rawValue == look } ?: GraphTheme.SPACE
            if (settings.theme != theme.rawValue) settings.setTheme(theme.rawValue)
            IdeasSpace(model.book, settings, covered = false, hidden = hidden, open = {}, openFolder = {})
        }
    }
}
