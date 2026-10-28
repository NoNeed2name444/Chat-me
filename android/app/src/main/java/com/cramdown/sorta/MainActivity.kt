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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SortaRoot() }
    }
}

/** The first screen. For now it is the page colour from edge to edge, the same as the starting window. */
@Composable
fun SortaRoot() {
    Box(Modifier.fillMaxSize().background(colorResource(R.color.page)))
}
