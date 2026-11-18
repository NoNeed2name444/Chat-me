package com.cramdown.sorta

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.cramdown.sorta.data.IdeaStore
import com.cramdown.sorta.data.Note
import com.cramdown.sorta.ideas.MapLook
import com.cramdown.sorta.ideas.NoteFolder
import com.cramdown.sorta.map.TreeColors
import com.cramdown.sorta.ui.ideas.IdeasSettings
import com.cramdown.sorta.ui.ideas.IdeasTags
import com.cramdown.sorta.ui.ideas.SortaMode
import com.cramdown.sorta.ui.map.MapDrawn
import com.cramdown.sorta.ui.map.MapDrawnKey
import com.cramdown.sorta.ui.map.MapTapKey
import com.cramdown.sorta.ui.map.TreeTags
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

/**
 * S11 on the emulator, by day and by night: the 3D Tree on the approved concept's sample, drawn by the
 * map's own renderer (GL frames draw only on a device). Kept as stills in /sdcard/Download/sorta-renders,
 * each named tree-<what>-<light|dark>-<window>.png: whole (the first view, the first visit's card up and
 * the trunk above it), orbit (a drag of about 40 degrees round the trunk), cardiology (flown in to
 * Cardiology by a tap on it). At each: the names drawn (MapDrawn.names) never overlap, the big branches
 * are named, and the frame is the page's colour with the tree on it and no logo coral. On the tablet the
 * day's pass is shot again at an open foldable's inner screen (wm size 2208x1840, 420 dpi), "foldable".
 */
@RunWith(AndroidJUnit4::class)
class TreeMapRenderTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val ui = UiDevice.getInstance(instrumentation)
    private val device: String =
        InstrumentationRegistry.getArguments().getString("device")?.takeIf { it.matches(Regex("[a-z0-9]+")) } ?: "phone"
    private var scenario: ActivityScenario<MainActivity>? = null
    private var activity: MainActivity? = null

    @Before fun clearChoices() {
        instrumentation.targetContext.getSharedPreferences(IdeasSettings.FILE, android.content.Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After fun back() {
        scenario?.close()
        activity = null
        ui.executeShellCommand("wm size reset")
        ui.executeShellCommand("wm density reset")
        ui.executeShellCommand("cmd uimode night no")
    }

    @Test fun treeByDay() {
        tree(dark = false, window = device)
        if (device == "tablet") {
            scenario?.close(); scenario = null
            ui.executeShellCommand("wm size 2208x1840")
            ui.executeShellCommand("wm density 420")
            SystemClock.sleep(1_500)
            clearChoices()
            tree(dark = false, window = "foldable")
        }
    }

    @Test fun treeByNight() = tree(dark = true, window = device)

    private fun tree(dark: Boolean, window: String) {
        val suffix = "${if (dark) "dark" else "light"}-$window"
        open(dark, suffix)
        waitFor(TreeTags.FIRST_VISIT, "the first visit's card, $suffix")
        settle(1_500)
        // each still is kept before its checks, so a failed check still leaves the picture
        shot("tree-whole-$suffix", dark)
        names("whole", suffix, listOf("All ideas", "Examples", "Cardiology"))
        // the first view keeps above the card: the trunk's foot, its body's place, is drawn above the card's top
        val card = node(TreeTags.FIRST_VISIT).boundsInRoot
        val top = checkNotNull(mapNode()).boundsInRoot.top
        val trunk = checkNotNull(drawn()?.names?.firstOrNull { it.index == 0 }) { "The trunk is not named for $suffix" }
        check(top + trunk.box[3] < card.top) { "The trunk's name ${trunk.box} (map top $top) runs under the card $card for $suffix" }

        // a drag of about 40 degrees round the trunk (Map3DView's drag turns 3.2 radians a view's width)
        val before = checkNotNull(drawn()).pose
        val map = checkNotNull(mapNode()).boundsInRoot
        val y = (map.top + map.height * 0.42f).toInt()
        val from = (map.left + map.width * 0.70f).toInt(); val to = (map.left + map.width * 0.482f).toInt()
        check(ui.swipe(from, y, to, y, 40)) { "The drag could not be made for $suffix" }
        settle(1_500)
        shot("tree-orbit-$suffix", dark)
        val turned = checkNotNull(drawn()).pose[4] - before[4]
        check(turned in 0.45f..1.0f) { "The drag turned the tree $turned radians, not about 40 degrees, for $suffix" }
        names("orbit", suffix, listOf("All ideas", "Cardiology"))

        // a tap on Cardiology flies in to it
        val cardiology = TITLE.indexOf("Cardiology")
        val tap = checkNotNull(mapNode()?.config?.getOrNull(MapTapKey)) { "The map hands on no hit test for $suffix" }
        val index = tap.ids.indexOfFirst { it.equals(id(cardiology), ignoreCase = true) }
        check(index >= 0) { "Cardiology is not on the map for $suffix" }
        onMain { tap.tap(index) }
        waitFor(TreeTags.BACK, "Back after flying in, $suffix")
        settle(2_500)
        shot("tree-cardiology-$suffix", dark)
        val inside = names("cardiology", suffix, listOf("Cardiology", "Heart failure"))
        check(inside.count { under(cardiology, it) } >= 6) { "Few of Cardiology's parts are named for $suffix: $inside" }
        // each name is a TalkBack element at least 48 dp each way
        val least = 48 * instrumentation.targetContext.resources.displayMetrics.density - 0.5f
        val part = node(TreeTags.part(id(TITLE.indexOf("Heart failure")))).touchBoundsInRoot
        check(part.width >= least && part.height >= least) { "Heart failure's element is ${part.width} x ${part.height} for $suffix" }
    }

    /** The names drawn now: none overlapping, [want] among them; their nodes. */
    private fun names(what: String, suffix: String, want: List<String>): List<Int> {
        try { compose.waitUntil(10_000) { drawn()?.names?.map { TITLE.getOrNull(it.index) }?.containsAll(want) == true } } catch (_: ComposeTimeoutException) {}
        val names = checkNotNull(drawn()).names
        val words = names.map { TITLE.getOrNull(it.index) }
        check(words.containsAll(want)) {
            val d = checkNotNull(drawn())
            val card = if (shown(TreeTags.FIRST_VISIT)) node(TreeTags.FIRST_VISIT).boundsInRoot.toString() else "none"
            "$what, $suffix: $want not all named in a ${d.width} x ${d.height} map (its top ${mapNode()?.boundsInRoot?.top}, the card $card): " +
                names.joinToString { n -> "${TITLE.getOrNull(n.index)} ${n.box.map { it.toInt() }}" }
        }
        for (a in names.indices) for (b in a + 1 until names.size) {
            val p = names[a].box; val q = names[b].box
            val apart = p[2] <= q[0] || q[2] <= p[0] || p[3] <= q[1] || q[3] <= p[1]
            check(apart) { "$what, $suffix: ${words[a]} ${p} overlaps ${words[b]} $q" }
        }
        return names.map { it.index }
    }

    private fun under(p: Int, i: Int): Boolean {
        var q = i
        while (q > 0) { if (q == p) return true; q = PARENT[q] }
        return q == p
    }

    /** Sorta on the sample with the Tree chosen, its map drawn. */
    private fun open(dark: Boolean, name: String) {
        ui.executeShellCommand("cmd uimode night ${if (dark) "yes" else "no"}")
        SystemClock.sleep(1_000)
        val context = instrumentation.targetContext
        IdeasSettings(context).setLook(MapLook.TREE)
        val dir = File(context.cacheDir, "tree-" + UUID.randomUUID()).also { it.mkdirs() }
        IdeaStore(dir).replaceIdeas(notes, folders)
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java).putExtra(MainActivity.STORE_DIR, dir.path))
        checkNotNull(scenario).onActivity { activity = it }
        compose.waitUntil(20_000) { compose.onAllNodes(hasTestTag(IdeasTags.TITLE)).fetchSemanticsNodes().isNotEmpty() }
        if (shown(SortaMode.MAP.identifier) && !shown(TreeTags.MAP)) tapOnScreen(node(SortaMode.MAP.identifier), "Map mode's switch")
        try { compose.waitUntil(60_000) { drawn()?.let(::framed) == true } } catch (_: ComposeTimeoutException) {}
        check(drawn()?.let(::framed) == true) { "The Tree was not drawn within 60 s for $name: ${drawn()}" }
    }

    private fun shown(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun waitFor(tag: String, what: String) {
        try { compose.waitUntil(15_000) { shown(tag) } } catch (_: ComposeTimeoutException) {}
        check(shown(tag)) { "Not on screen: $what" }
    }

    private fun node(tag: String): SemanticsNode = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().first()

    private fun tapOnScreen(node: SemanticsNode, what: String) {
        val middle = node.positionOnScreen + Offset(node.size.width / 2f, node.size.height / 2f)
        check(ui.click(middle.x.toInt(), middle.y.toInt())) { "$what could not be tapped" }
    }

    private fun settle(ms: Long) {
        try { compose.waitUntil(ms) { false } } catch (_: ComposeTimeoutException) {}
    }

    private fun <T> onMain(block: () -> T): T {
        var out: Result<T>? = null
        instrumentation.runOnMainSync { out = runCatching(block) }
        return checkNotNull(out).getOrThrow()
    }

    private fun mapNode(): SemanticsNode? = onMain {
        val found = ArrayList<SemanticsNode>()
        fun nodes(n: SemanticsNode) {
            if (n.config.getOrNull(MapDrawnKey) != null) found.add(n)
            n.children.forEach(::nodes)
        }
        fun views(v: View) {
            if (v is RootForTest) nodes(v.semanticsOwner.unmergedRootSemanticsNode)
            if (v is ViewGroup) for (k in 0 until v.childCount) views(v.getChildAt(k))
        }
        activity?.window?.decorView?.let(::views)
        found.firstOrNull()
    }

    private fun drawn(): MapDrawn? = mapNode()?.config?.getOrNull(MapDrawnKey)

    private fun framed(drawn: MapDrawn) = drawn.frames >= MapDrawn.ENOUGH && drawn.bodyFrames >= MapDrawn.ENOUGH

    /** The still, kept; and read back here: mostly the page's colour, the tree on it, no logo coral. */
    private fun shot(name: String, dark: Boolean) {
        require(name.matches(Regex("[a-z0-9-]+")))
        ui.executeShellCommand("mkdir -p $DIRECTORY")
        ui.executeShellCommand("screencap -p $DIRECTORY/$name.png")
        val size = ui.executeShellCommand("stat -c %s $DIRECTORY/$name.png").trim().toLongOrNull()
        check(size != null && size > 0) { "The still was not written: $name" }
        val local = File(instrumentation.targetContext.cacheDir, "$name.png")
        check(ui.takeScreenshot(local)) { "No screenshot to read back: $name" }
        val bitmap = checkNotNull(BitmapFactory.decodeFile(local.path)) { "The screenshot does not read back: $name" }
        val pixels = IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
        local.delete()
        val colors = TreeColors(dark)
        val sky = pixels.count { (it and 0xFFFFFF) == colors.sky }
        check(sky > pixels.size / 4) { "$name: only $sky of ${pixels.size} pixels are the page's colour" }
        // the tree: pixels well away from the page's colour, its lit bark and leaves
        val tree = pixels.count { far(it, colors.sky) }
        check(tree > pixels.size / 200) { "$name: only $tree pixels of tree" }
        val coral = pixels.count { (it and 0xFFFFFF) == LOGO_CORAL }
        check(coral == 0) { "$name: $coral pixels are the logo's coral" }
    }

    private fun far(a: Int, b: Int): Boolean {
        fun d(s: Int) = abs(((a shr s) and 255) - ((b shr s) and 255))
        return d(16) + d(8) + d(0) > 90
    }

    private companion object {
        const val DIRECTORY = "/sdcard/Download/sorta-renders"
        const val LOGO_CORAL = 0xC9553E
        const val T = 1_793_000_000_000L

        fun id(i: Int) = "00000000-0000-4000-8000-%012x".format(Locale.ROOT, 0x7000 + i)

        /** The concept's sample (tools/tree_fixture.mjs's SAMPLE, as tree-fixture.json's "sample"): each part's parent and title. */
        val PARENT = intArrayOf(-1, 0, 1, 2, 3, 4, 5, 5, 4, 8, 8, 4, 11, 12, 4, 14, 14, 3, 17, 17, 3, 20, 20, 20, 2, 24, 24, 26, 26, 24, 29, 29, 2, 32, 32, 32, 35, 2, 37, 37, 37, 2, 41, 41, 2, 44, 44, 1, 47, 48, 48, 48, 51, 51, 51, 51, 47, 56, 56, 47, 59, 59, 59, 1, 63, 63, 63, 1, 67, 68, 67, 70, 67, 1, 73, 0, 75, 76, 77, 78, 77, 80, 77, 82, 76, 84, 84, 84, 76, 88, 88, 88, 76, 92, 92, 92, 92, 75, 97, 97, 75, 100, 101, 100, 75, 104, 104, 104, 75, 108, 108, 108, 75, 112, 112, 75, 115, 115, 75, 118, 118, 75, 121, 121, 75, 124, 124, 124, 75, 128, 75, 130, 130, 130, 75, 134, 134, 75, 137, 137, 0, 140, 140, 140, 0, 144, 144, 144, 144)
        val TITLE = arrayOf("All ideas", "Examples", "Inguinal", "Anatomy", "Canal boundaries", "Anterior",
            "External oblique all through", "Internal oblique laterally", "Posterior", "Fascia transversalis all through",
            "Internal oblique medially", "Roof", "Conjoined muscles", "Internal oblique and transversus", "Floor",
            "Inguinal ligament", "Lacunar ligament", "LA/PM mnemonic", "Anterior wall Laterally", "Posterior wall Medially",
            "Spermatic cord coverings", "Internal spermatic fascia", "Cremasteric muscle and fascia",
            "External spermatic fascia", "Inguinal canal", "4 cm, running obliquely", "In males", "Spermatic cord",
            "Ilio-inguinal nerve", "In females", "Round ligament of the uterus", "Ilio-inguinal nerve",
            "Indirect inguinal hernia", "70% of all hernias", "The defect is the stretched deep inguinal ring",
            "The sac lies inside the cord", "May reach the scrotum", "Direct inguinal hernia",
            "Through the posterior wall of the canal", "Adult or elderly", "Reduces backwards", "Inferior epigastric vessels",
            "Sac lateral = indirect inguinal hernia", "Sac medial = direct inguinal hernia", "Internal ring test",
            "Positive (swelling controlled) = oblique", "Negative = direct", "Femoral", "Femoral hernia", "More common in women",
            "Neck below and lateral to the pubic tubercle", "Femoral ring", "Inguinal ligament in front",
            "Pectineal ligament behind", "Femoral vein laterally", "Lacunar ligament medially", "Femoral canal",
            "Medial to the femoral vein", "1.25 cm long, cone shaped", "Saphena varix", "Disappears completely lying flat",
            "Fluid thrill on cough", "Venous hum", "Groin hernia", "Three to know", "The anatomy first",
            "The only treatment is surgery", "Hernia repair", "TAPP", "Transabdominal preperitoneal", "TEP",
            "Totally extraperitoneal", "Robotic-assisted repair", "Expansile cough impulse", "A hernia until shown otherwise",
            "Cardiology", "Heart failure", "Types", "HFrEF", "Ejection fraction 40% or less", "HFpEF",
            "Ejection fraction 50% or more", "Mildly reduced", "41 to 49%", "Causes", "Ischaemic heart disease", "Hypertension",
            "Valve disease", "Symptoms and signs", "Raised JVP", "Ankle swelling", "Basal crackles", "Treatment",
            "Loop diuretics for congestion", "Beta-blocker", "SGLT2 inhibitor", "Start low and titrate", "BNP",
            "Raised in heart failure", "A normal level makes it unlikely", "Loop diuretics", "Furosemide", "For congestion",
            "Watch hypokalaemia", "ACE inhibitors", "Prognostic in heart failure", "Cough", "Check hypokalaemia the other way",
            "Hypokalaemia", "Flat T waves", "U waves", "Risk with loop diuretics", "Acute coronary syndrome", "Unstable angina",
            "Troponin tells them apart", "STEMI", "ST elevation", "Primary PCI", "NSTEMI", "Raised troponin",
            "Without ST elevation", "Troponin", "Rises in 3 hours", "Peaks at 24", "Atrial fibrillation",
            "Irregularly irregular", "Rate or rhythm control", "CHA2DS2-VASc for anticoagulation", "CHA2DS2-VASc", "Stroke risk",
            "Murmurs", "Aortic stenosis", "Mitral regurgitation", "Heart failure as the end point", "Heart sounds", "S1 and S2",
            "Listen at the apex for S3", "Syncope", "Cardiac or not?", "Exertional syncope needs an echo", "Richter's hernia",
            "Only part of the bowel wall is trapped", "Can strangulate without obstructing",
            "Most often through the femoral ring", "Pericarditis", "Sharp chest pain", "Better sitting forward",
            "Saddle-shaped ST elevation", "PR depression")


        /** A part with parts is a folder, the rest ideas, made a minute apart in the concept's order. */
        private val kids = IntArray(PARENT.size).also { k -> for (i in 1 until PARENT.size) k[PARENT[i]]++ }
        val folders: List<NoteFolder> = (1 until PARENT.size).filter { kids[it] > 0 }.map { i ->
            NoteFolder(id = id(i), name = TITLE[i], parentId = if (PARENT[i] == 0) null else id(PARENT[i]), createdAt = T + i)
        }
        val notes: List<Note> = (1 until PARENT.size).filter { kids[it] == 0 }.map { i ->
            Note(id = id(i), title = TITLE[i], folderId = if (PARENT[i] == 0) null else id(PARENT[i]), createdAt = T + i * 60_000L)
        }
    }
}
