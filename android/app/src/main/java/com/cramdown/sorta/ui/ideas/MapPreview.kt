package com.cramdown.sorta.ui.ideas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
    Box(Modifier.fillMaxSize().background(wardColor(WardToken.background))) {
        if (look == "board") {
            IdeaBoard(model.book, settings, hidden = 0.dp, open = {})
        } else {
            val theme = GraphTheme.entries.firstOrNull { it.rawValue == look } ?: GraphTheme.SPACE
            if (settings.theme != theme.rawValue) settings.setTheme(theme.rawValue)
            IdeasSpace(model.book, settings, covered = false, hidden = 0.dp, open = {}, openFolder = {})
        }
    }
}
