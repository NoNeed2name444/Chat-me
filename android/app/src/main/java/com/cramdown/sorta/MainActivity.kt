package com.cramdown.sorta

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.foundation.isSystemInDarkTheme
import com.cramdown.sorta.ui.SortaTheme
import com.cramdown.sorta.ui.ideas.MapPreview

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // the maps on sample ideas, for the instrumented tests and the renders
        // (MapPreview), as iOS's -graphPreview; no screen a user reaches opens it
        val preview: String? = intent?.getStringExtra(MAP_PREVIEW)
        setContent {
            if (preview != null) SortaTheme(dark = isSystemInDarkTheme()) { MapPreview(preview) } else SortaRoot()
        }
    }

    companion object {
        /** The launch extra that shows [MapPreview]: "space", "neurons", "performance" or "board". */
        const val MAP_PREVIEW = "sortaMapPreview"
    }
}

/** The first screen. For now it is the page colour from edge to edge, the same as the starting window. */
@Composable
fun SortaRoot() {
    Box(Modifier.fillMaxSize().background(colorResource(R.color.page)))
}
