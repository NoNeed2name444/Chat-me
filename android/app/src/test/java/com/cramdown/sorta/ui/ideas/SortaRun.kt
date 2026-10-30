package com.cramdown.sorta.ui.ideas

import android.content.Context
import android.content.Intent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.cramdown.sorta.MainActivity
import java.io.File
import java.util.UUID

/**
 * Sorta launched as the tests launch it (S05, item 8): MainActivity with its store in a new
 * directory of the app's cache, so each test starts on a fresh store, and relaunched on the same
 * directory, which reopens that store. Every idea here is made up, and none is medical.
 */
class SortaRun(private val compose: ComposeTestRule) {
    val context: Context = ApplicationProvider.getApplicationContext()
    val store: File = File(context.cacheDir, "store-" + UUID.randomUUID()).also { it.mkdirs() }
    private var scenario: ActivityScenario<MainActivity>? = null

    /** Launches Sorta on this run's store, handing [import] to the import as the picker's result would. */
    fun launch(import: File? = null): SortaRun {
        val intent = Intent(context, MainActivity::class.java).putExtra(MainActivity.STORE_DIR, store.path)
        if (import != null) intent.putExtra(MainActivity.IMPORT_FILE, import.path)
        scenario = ActivityScenario.launch(intent)
        compose.waitForIdle()
        return this
    }

    /** Closes Sorta and opens it again on the same store. */
    fun relaunch(import: File? = null): SortaRun {
        scenario?.close()
        compose.waitForIdle()
        return launch(import)
    }

    fun close() { scenario?.close(); scenario = null }

    /** A file in the app's cache, for the import. */
    fun file(name: String, text: String): File = File(context.cacheDir, "import-" + UUID.randomUUID() + "-" + name).also { it.writeText(text) }

    // the screen's parts

    val field: SemanticsNodeInteraction get() = compose.onNode(hasTestTag(IdeasTags.FIELD))
    val sortIt: SemanticsNodeInteraction get() = compose.onNode(hasTestTag(IdeasTags.SORT_IT))
    val title: SemanticsNodeInteraction get() = compose.onNode(hasTestTag(IdeasTags.TITLE))
    fun idea(line: String): SemanticsNodeInteraction = compose.onNode(hasTestTag(IdeasTags.IDEA_LINE) and hasText(line))
    fun ideaCount(line: String): Int = compose.onAllNodes(hasTestTag(IdeasTags.IDEA_LINE) and hasText(line)).fetchSemanticsNodes().size
    /** A folder row, by its spoken label ("Garden, 1 idea"). */
    fun folderRow(spoken: String): SemanticsNodeInteraction =
        compose.onNode(hasContentDescription(spoken) and hasAnyAncestor(hasTestTag(IdeasTags.SCREEN)))
    fun inChooser(spoken: String): SemanticsNodeInteraction =
        compose.onNode(hasContentDescription(spoken) and hasAnyAncestor(hasTestTag(FOLDER_CHOOSER_TAG)))
    fun chooserShown(): Boolean = compose.onAllNodes(hasTestTag(FOLDER_CHOOSER_TAG)).fetchSemanticsNodes().isNotEmpty()
    fun field(label: String): SemanticsNodeInteraction = compose.onNode(hasContentDescription(label) and hasSetTextAction())
    fun banner(): String? = compose.onAllNodes(hasTestTag(IdeasTags.BANNER), useUnmergedTree = true).fetchSemanticsNodes()
        .firstOrNull()?.let { node -> textsUnder(node) }
    fun shows(words: String): Boolean = compose.onAllNodes(hasText(words, substring = true)).fetchSemanticsNodes().isNotEmpty()

    /** What the capture field holds now. */
    fun draft(): String = field.fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text ?: ""

    /** Runs the custom accessibility action [label] on the node, as TalkBack would. */
    fun SemanticsNodeInteraction.action(label: String) {
        val actions = fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions) ?: emptyList()
        val found = actions.firstOrNull { it.label == label } ?: error("no action $label among ${actions.map { it.label }}")
        compose.runOnIdle { found.action() }
        compose.waitForIdle()
    }

    fun actions(node: SemanticsNodeInteraction): List<String> =
        node.fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions)?.map { it.label } ?: emptyList()

    /**
     * Waits up to [ms] of real time for [condition]. Between looks the screen's clock moves half a second
     * on, so a sheet's or the banner's animation finishes, and real time passes too, for work off the
     * main thread (the import's read).
     */
    fun until(ms: Long = 10_000, condition: () -> Boolean) {
        val deadline = System.nanoTime() + ms * 1_000_000
        while (true) {
            compose.waitForIdle()
            if (condition()) return
            if (System.nanoTime() > deadline) throw AssertionError("still not so after $ms ms; on screen:\n" + screen())
            compose.mainClock.advanceTimeBy(STEP_MS)
            Thread.sleep(2)
        }
    }

    /** Every window's semantics, for a failure's message. */
    fun screen(): String = compose.onAllNodes(androidx.compose.ui.test.isRoot()).fetchSemanticsNodes().indices
        .joinToString("\n") { compose.onAllNodes(androidx.compose.ui.test.isRoot())[it].printToString() }

    private fun textsUnder(node: androidx.compose.ui.semantics.SemanticsNode): String {
        val own = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text } ?: ""
        return (listOf(own) + node.children.map { textsUnder(it) }).filter { it.isNotEmpty() }.joinToString(" ")
    }

    companion object {
        const val STEP_MS = 500L
        fun id(n: Int) = "00000000-0000-4000-8000-" + n.toString().padStart(12, '0')

        /** A made-up Stethoscore Android export: two ideas, one in a folder, one linked by hand, one placed on the map. */
        val export = """
            {"format":"stethoscore-android-v1","exportedAt":"2026-10-20T09:00:00Z",
             "noteFolders":[{"id":"${id(101)}","name":"Garden","parentId":null,"createdAt":"2026-10-01T08:00:00Z"}],
             "notes":[
               {"id":"${id(1)}","title":"Paint the shed green","body":"Two coats.","folderId":"${id(101)}","kind":"idea","links":["${id(2)}"],"tags":[],"boardX":120,"boardY":-40,"createdAt":"2026-10-03T07:00:00Z","updatedAt":"2026-10-04T07:00:00Z"},
               {"id":"${id(2)}","title":"Buy brushes","body":"","folderId":null,"kind":"idea","links":[],"tags":[],"boardX":0,"boardY":0,"createdAt":"2026-10-05T07:00:00Z","updatedAt":"2026-10-05T07:00:00Z"}
             ]}
        """.trimIndent()

        fun matcherOf(description: String, test: (androidx.compose.ui.semantics.SemanticsNode) -> Boolean) = SemanticsMatcher(description, test)
    }
}
