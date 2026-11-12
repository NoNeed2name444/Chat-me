package com.cramdown.sorta

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.cramdown.sorta.data.IdeaStore
import com.cramdown.sorta.ui.SortaTheme
import com.cramdown.sorta.ui.ideas.IdeasModel
import com.cramdown.sorta.ui.ideas.IdeasView
import com.cramdown.sorta.ui.ideas.MapPreview
import com.cramdown.sorta.ui.ward.WindowShapeHost
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // the maps on sample ideas, for the instrumented tests and the renders
        // (MapPreview), as iOS's -graphPreview; no screen a user reaches opens it
        val preview: String? = intent?.getStringExtra(MAP_PREVIEW)
        if (preview != null) {
            setContent { SortaTheme(dark = isSystemInDarkTheme()) { MapPreview(preview) } }
            return
        }
        // the store, opened at launch: ideas.json in the files directory, or the tests' own
        val store = IdeaStore(testPath(this, STORE_DIR) ?: filesDir)
        val testImport = testPath(this, IMPORT_FILE)
        setContent { SortaRoot(store, testImport) }
    }

    companion object {
        /** The launch extra that shows [MapPreview]: "space", "neurons", "performance" or "board". */
        const val MAP_PREVIEW = "sortaMapPreview"
        /**
         * The tests' launch extras: a directory for the store (a new one gives a fresh store; the
         * same one again reopens it), and a file handed to the import as the picker's result is.
         * Each must lie in the app's own cache directory, so nothing outside the app can point
         * Sorta at a store or a file of its choosing.
         */
        const val STORE_DIR = "sortaStoreDir"
        const val IMPORT_FILE = "sortaImportFile"

        /** The extra [name] as a path, only if it lies inside the app's cache directory. */
        fun testPath(activity: ComponentActivity, name: String): File? {
            val path = activity.intent?.getStringExtra(name) ?: return null
            return inCache(activity, File(path))
        }

        internal fun inCache(context: Context, file: File): File? {
            val cache = runCatching { context.cacheDir.canonicalFile }.getOrNull() ?: return null
            val canonical = runCatching { file.canonicalFile }.getOrNull() ?: return null
            return canonical.takeIf { generateSequence(it.parentFile) { p -> p.parentFile }.any { p -> p == cache } }
        }
    }
}

/** The first screen: the Sort screen on the store, in the window's shape and the system's light or dark. */
@Composable
fun SortaRoot(store: IdeaStore, testImport: File? = null) {
    SortaTheme(dark = isSystemInDarkTheme()) {
        WindowShapeHost {
            val model = remember(store) { IdeasModel.open(store) }
            IdeasView(model, testImport)
        }
    }
}
