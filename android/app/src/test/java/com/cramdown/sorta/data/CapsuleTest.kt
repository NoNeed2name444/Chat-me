package com.cramdown.sorta.data

import com.cramdown.sorta.ideas.IdeaBook
import com.cramdown.sorta.ideas.NoteFolder
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The capsule (S06d A4-A5), as iOS's CapsuleTests: the two shared fixtures (one as iOS writes it,
 * one as Android does, the same files in both test folders) read to the same ideas; this writer's
 * data is its own fixture's; a capsule written and read back, and through the store's import, gives
 * every idea, folder, link and stored field again, a second time adds nothing, and ids match in any
 * case; each refusal changes nothing; the page escapes what it shows and its data can't close its
 * block. Made-up ideas, none medical.
 */
class CapsuleTest {
    @get:Rule val folder = TemporaryFolder()
    private fun fixture(name: String) = checkNotNull(javaClass.getResource("/capsule/$name")) { "no fixture $name" }.readText()
    private fun ms(iso: String) = Instant.parse(iso).toEpochMilli()
    private fun id(n: String) = "00000000-0000-4000-8000-" + n.padStart(12, '0')

    /** The fixtures' ideas, as Android holds them: lower-case ids, times to the millisecond. */
    private val garden = NoteFolder(id("a1"), "Garden", null, ms("2026-10-01T08:00:00.100Z"))
    private val bulbs = NoteFolder(id("a2"), "Bulbs", garden.id, ms("2026-10-01T08:05:00.200Z"))
    private val shed = Note(id("1"), "Paint the shed green", "Two coats, after the rain. Check [[Buy brushes]] first.", garden.id, "idea",
        listOf(id("2")), listOf("outside"), 120.5, -40.0, ms("2026-10-03T23:15:00.250Z"), ms("2026-10-04T07:00:00.750Z"))
    private val brushes = Note(id("2"), "Buy brushes", "", null, "idea", emptyList(), emptyList(), 0.0, 0.0,
        ms("2026-10-05T07:00:00.125Z"), ms("2026-10-05T07:00:00.125Z"))
    private val plan = Note(id("3"), "Bulb planting plan", "Tulips first </script> then crocus & co?", bulbs.id, "page", emptyList(),
        listOf("spring", "bulbs"), -75.5, 210.25, ms("2026-10-06T10:30:00.500Z"), ms("2026-10-07T11:45:00.001Z"))
    private val notes = listOf(shed, brushes, plan)
    private val folders = listOf(garden, bulbs)

    /** A note's stored fields, ids in upper case, times to [precision] ms (iOS writes whole seconds). */
    private fun fields(n: Note, precision: Long = 1) = listOf(n.id.uppercase(), n.title, n.body, n.folderId?.uppercase(), n.kind,
        n.links.map { it.uppercase() }, n.tags, n.boardX, n.boardY, n.createdAt / precision, n.updatedAt / precision)
    private fun fields(f: NoteFolder, precision: Long = 1) = listOf(f.id.uppercase(), f.name, f.parentId?.uppercase(), f.createdAt / precision)

    /** The data block's JSON, as a value: nulls dropped (iOS leaves a nil out, Android writes null), numbers as doubles. */
    private fun value(any: Any?): Any? = when (any) {
        is JSONObject -> any.keys().asSequence().filter { !any.isNull(it) }.associateWith { value(any.get(it)) }
        is JSONArray -> (0 until any.length()).map { value(any.get(it)) }
        is Number -> any.toDouble()
        else -> any
    }
    private fun block(html: String) = JSONObject(Regex("<script type=\"application/json\" id=\"sorta-capsule\">(.*?)</script>", RegexOption.DOT_MATCHES_ALL)
        .find(html)!!.groupValues[1])

    @Test fun bothFixturesReadToTheSameIdeas() {
        val android = Capsule.read(fixture("capsule-from-android.html"))
        assertEquals(notes.map { fields(it) }, android.notes.map { fields(it) })
        assertEquals(folders.map { fields(it) }, android.folders.map { fields(it) })
        val ios = Capsule.read(fixture("capsule-from-ios.html"))
        assertEquals("iOS's: the same ideas, to the second", notes.map { fields(it, 1000) }, ios.notes.map { fields(it, 1000) })
        assertEquals(folders.map { fields(it, 1000) }, ios.folders.map { fields(it, 1000) })
        assertEquals("iOS's ids as it wrote them", id("A1").uppercase(), ios.folders[0].id)
        // a field only iOS has (a note's source) is kept as it came, and written back
        val source = JSONObject(ios.notes[2].rawJson).getJSONObject("source")
        assertEquals("lecture", source.getString("kind"))
        val again = Capsule.read(Capsule.html(ios.notes, ios.folders, emptyList(), 0L, ZoneOffset.UTC, null))
        assertEquals(3, JSONObject(again.notes[2].rawJson).getJSONObject("source").getInt("page"))
    }

    @Test fun thisWritersDataIsItsFixtures() {
        val written = Capsule.data(notes, folders, ms("2026-11-14T19:05:00.500Z"))
        assertEquals(value(block(fixture("capsule-from-android.html"))), value(written))
        // and iOS's fixture holds the same keys, nil and source aside
        val ios = value(block(fixture("capsule-from-ios.html"))) as Map<*, *>
        assertEquals((value(written) as Map<*, *>).keys, ios.keys)
    }

    @Test fun aCapsuleWrittenAndReadBackHoldsEveryField() {
        val book = IdeaBook(notes, folders)
        val html = Capsule.html(notes, folders, book.allEdges(), ms("2026-11-14T19:05:00Z"), ZoneId.of("Africa/Cairo"), null)
        val back = Capsule.read(html)
        assertEquals(notes.map { fields(it) }, back.notes.map { fields(it) })
        assertEquals(folders.map { fields(it) }, back.folders.map { fields(it) })
        // through the store, as Open a capsule does: everything comes in, once
        val store = IdeaStore(folder.newFolder())
        assertEquals(ImportCounts(ideas = 3, folders = 2, links = 1, placed = 2), store.importCapsule(html))
        assertEquals(notes.map { fields(it) }, store.notes.map { fields(it) })
        assertEquals(ImportCounts(), store.importCapsule(html))
        // iOS's capsule of the same ideas: its ids are upper case, and nothing is added twice
        assertEquals(ImportCounts(), store.importCapsule(fixture("capsule-from-ios.html")))
        assertEquals(3, store.notes.size)
        // an empty store takes iOS's too, its links and folders whole
        val fresh = IdeaStore(folder.newFolder())
        assertEquals(ImportCounts(ideas = 3, folders = 2, links = 1, placed = 2), fresh.importCapsule(fixture("capsule-from-ios.html")))
        assertEquals(listOf(listOf(id("2").uppercase())), fresh.notes.filter { it.links.isNotEmpty() }.map { it.links })
        assertEquals(1, IdeaBook(fresh.notes, fresh.folders).allEdges().size)
    }

    @Test fun eachRefusalChangesNothing() {
        val store = IdeaStore(folder.newFolder())
        fun refused(text: String): ImportRefusal? = try { store.importCapsule(text); null } catch (e: ImportRefused) { e.refusal }
        fun page(json: String) = "<!doctype html><p>Hello</p><script type=\"application/json\" id=\"sorta-capsule\">$json</script>"
        assertEquals("a page with no data", ImportRefusal.NOT_A_CAPSULE, refused("<!doctype html><title>Recipes</title><p>Soup</p>"))
        assertEquals("a Stethoscore export", ImportRefusal.NOT_A_CAPSULE, refused(page("{\"format\":\"stethoscore-android-v1\",\"notes\":[]}")))
        assertEquals("no version", ImportRefusal.NOT_A_CAPSULE, refused(page("{\"format\":\"sorta-capsule\",\"notes\":[]}")))
        assertEquals("a later one", ImportRefusal.NEWER_CAPSULE, refused(page("{\"format\":\"sorta-capsule\",\"version\":2,\"notes\":[]}")))
        assertEquals("not JSON", ImportRefusal.UNREADABLE, refused(page("{\"format\": sorta")))
        assertEquals("notes not a list", ImportRefusal.UNREADABLE, refused(page("{\"format\":\"sorta-capsule\",\"version\":1,\"notes\":{}}")))
        assertEquals("folders not a list", ImportRefusal.UNREADABLE, refused(page("{\"format\":\"sorta-capsule\",\"version\":1,\"folders\":7}")))
        assertEquals("over 32 MB", ImportRefusal.TOO_LARGE, refused("x".repeat(IdeaStore.MAX_IMPORT_BYTES + 1)))
        assertTrue(store.notes.isEmpty() && store.folders.isEmpty())
        // a capsule of an earlier shape, with no folders, is read
        assertEquals(1, store.importCapsule(page("{\"format\":\"sorta-capsule\",\"version\":1,\"notes\":[{\"id\":\"${id("9")}\",\"title\":\"Fix the gate\"}]}")).ideas)
    }

    @Test fun thePageShowsEveryIdeaAndEscapesIt() {
        val tricky = shed.copy(title = "Shed <b>now</b> & \"then\"")
        val all = listOf(tricky, brushes, plan)
        val book = IdeaBook(all, folders)
        val html = Capsule.html(all, folders, book.allEdges(), ms("2026-11-14T19:05:00Z"), ZoneOffset.UTC,
            Capsule.Picture(byteArrayOf(1, 2, 3), 300, 200))
        assertTrue("its title escaped", "Shed &lt;b&gt;now&lt;/b&gt; &amp; &quot;then&quot;" in html)
        assertFalse("no markup from an idea", "<b>now" in html)
        assertEquals("one script block, the data, which closes once", 1, Regex("<script").findAll(html).count())
        assertEquals(1, Regex("</script").findAll(html).count())
        for (words in listOf("<h1>Sorta capsule</h1>", "Saved 14 November 2026 at 7:05 PM. 3 ideas and 2 folders.", "<h2>To sort</h2>",
                "<h2>Garden</h2>", "<h2>Garden › Bulbs</h2>", "Page, caught 6 October 2026 at 10:30 AM",
                "<article id=\"n-${brushes.id}\">", "Linked to <a href=\"#n-${brushes.id}\">Buy brushes</a>",
                "Check <a href=\"#n-${brushes.id}\">Buy brushes</a> first.",
                "Tulips first &lt;/script&gt; then crocus &amp; co?", "data:image/png;base64,AQID", "width=\"300\" height=\"200\"",
                Capsule.Words.RESTORE)) assertTrue("the page has $words", words in html)
        // the folder sections come after To sort, Garden before its Bulbs
        assertTrue(html.indexOf("<h2>To sort</h2>") < html.indexOf("<h2>Garden</h2>") && html.indexOf("<h2>Garden</h2>") < html.indexOf("Garden › Bulbs"))
        assertEquals("Sorta capsule 2026-11-14.html", Capsule.fileName(ms("2026-11-14T19:05:00Z"), ZoneOffset.UTC))
        assertEquals("1 idea.", Capsule.Words.counts(1, 0))
        assertEquals("2 ideas and 1 folder.", Capsule.Words.counts(2, 1))
        assertEquals("2 ideas and 3 folders.", Capsule.Words.counts(2, 3))
        assertFalse("no brackets on the page", "[[" in html.substringBefore("<script"))
        try { Capsule.read("<p>no data</p>"); fail() } catch (e: ImportRefused) { assertEquals(ImportRefusal.NOT_A_CAPSULE, e.refusal) }
    }

    /** The page as the app reads (S06d review): newest first, To sort left out when empty, `[[Title]]`s as words. */
    @Test fun thePageReadsAsTheAppDoes() {
        val newer = brushes.copy(id = id("4"), title = "Oil the gate", createdAt = ms("2026-10-09T07:00:00Z"))
        val html = Capsule.html(listOf(brushes, newer), emptyList(), emptyList(), 0L, ZoneOffset.UTC, null)
        assertTrue("the newer first", html.indexOf("Oil the gate") < html.indexOf("Buy brushes"))
        val none = Capsule.html(listOf(shed), listOf(garden), emptyList(), 0L, ZoneOffset.UTC, null)
        assertFalse("no To sort with nothing in it", "<h2>To sort</h2>" in none)
        val index = Capsule.titleIndex(notes)
        val cases = listOf(
            "Check [[Buy brushes]] first." to "Check <a href=\"#n-${brushes.id}\">Buy brushes</a> first.",
            "See [[ buy BRUSHES |the brushes]]." to "See <a href=\"#n-${brushes.id}\">the brushes</a>.",
            "Ask [[Nobody here]] & then" to "Ask Nobody here &amp; then",
            "Open [[ and more" to "Open [[ and more",
            "[[]] stays" to "[[]] stays",
            "[[two\nlines]]" to "[[two\nlines]]",
            "<b>[[Buy brushes]]</b>" to "&lt;b&gt;<a href=\"#n-${brushes.id}\">Buy brushes</a>&lt;/b&gt;",
        )
        for ((text, shown) in cases) assertEquals(text, shown, Capsule.words(text, index))
    }

    @Test fun theDataCantCloseItsBlock() {
        assertEquals("\\u003c/script\\u003e \\u0026 \\u2028", Capsule.scriptSafe("</script> & \u2028"))
        val nasty = brushes.copy(title = "</script><script>alert(1)</script>", body = "<!-- & -->")
        val html = Capsule.html(listOf(nasty), emptyList(), emptyList(), 0L, ZoneOffset.UTC, null)
        assertEquals(1, Regex("<script").findAll(html).count())
        val back = Capsule.read(html).notes.single()
        assertEquals(nasty.title, back.title)
        assertEquals(nasty.body, back.body)
    }
}
