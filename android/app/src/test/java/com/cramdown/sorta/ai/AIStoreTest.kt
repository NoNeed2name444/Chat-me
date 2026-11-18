package com.cramdown.sorta.ai

import android.content.Context
import android.util.Log
import com.cramdown.sorta.data.Capsule
import com.cramdown.sorta.data.IdeaStore
import com.cramdown.sorta.ideas.IdeaBook
import com.cramdown.sorta.ideas.NoteFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

// S08 A3, A6 and A7 with the app's own parts: a planted fake credential shows up nowhere but the vault (not the
// ideas file, a capsule, SharedPreferences, the log or output, the ledger, or a request's address or body); keeping
// a split is one change and one Undo, through IdeaBook only; and the app's assets are the repository's ai/ bytes.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AIStoreTest {
    @get:Rule val temp = TemporaryFolder()
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val split = AIRepo.fixture("model/split.json")
    private val capture = split["capture"]!!.string!!
    private var count = 0

    private fun pieces(garden: String, house: String): List<IdeaBook.SplitPiece> {
        val folders = listOf(AISplit.Folder(garden, "Garden", listOf("plants", "beans", "fence")), AISplit.Folder(house, "House", listOf("paint", "repairs")))
        val (verdict, got) = AISplit.check(split["cases"]!!.array!![0]["answer"]!!.string!!, capture, folders)
        assertEquals(AIVerdict.OK, verdict)
        return got.map { IdeaBook.SplitPiece(it.title, it.text, it.folder) }
    }

    @Test fun keepingASplitCaptureIsOneUndoStep() {
        val store = IdeaStore(temp.root)
        var clock = 1_000L
        val book = IdeaBook(emptyList(), emptyList(), now = { clock++ }, onChange = store::replaceIdeas, makeId = { "made-${++count}" })
        val garden = book.createFolder("Garden").id
        val house = book.createFolder("House").id
        val before1 = book.create("Kite", "A red one.")
        val made = book.create("Weekend", capture)
        val after1 = book.create("Bike bell")
        book.link(after1.id, before1.id)
        val before = book.notes
        val file = File(temp.root, IdeaStore.FILE_NAME)
        val savedBefore = file.readBytes()

        val kept = book.keepSplit(pieces(garden, house), made.id)!!
        assertNull("the capture became its ideas", book.note(made.id))
        assertEquals(3, kept.added.size)
        assertEquals(listOf(before1.id) + kept.added + listOf(after1.id), book.notes.map { it.id })
        val ideas = kept.added.map { book.note(it)!! }
        assertEquals(listOf("Paint the shed blue", "Borrow a ladder", "Beans up the fence"), ideas.map { it.title })
        assertEquals(listOf("Paint the shed blue before spring.", "Ask the neighbours about sharing a ladder.", "Try growing beans up the fence."),
            ideas.map { it.body })
        assertEquals("a folder picked, or To sort", listOf(house, null, garden), ideas.map { it.folderId })
        assertEquals("each its own place on the board", 3, ideas.map { it.boardX to it.boardY }.toSet().size)
        assertTrue("saved", file.readBytes().toString(Charsets.UTF_8).contains("Borrow a ladder"))

        book.undo(kept)
        assertEquals("one Undo puts the store back exactly", before, book.notes)
        assertEquals(before, store.notes)

        val typed = IdeaBook(listOf(before1), emptyList(), makeId = { "typed-${++count}" })
        assertNull(typed.keepSplit(emptyList()))
        assertNull(typed.keepSplit(pieces(garden, house), "not-there"))
        val added = typed.keepSplit(pieces(garden, house))!!
        assertEquals(listOf(before1.id) + added.added, typed.notes.map { it.id })
        typed.undo(added)
        assertEquals(listOf(before1), typed.notes)
        assertTrue(savedBefore.isNotEmpty())
    }

    @Test fun aPlantedCredentialIsNowhereButTheVault() {
        ShadowLog.clear()
        val store = IdeaStore(temp.root)
        val book = IdeaBook(emptyList(), emptyList(), onChange = store::replaceIdeas)
        val garden = book.createFolder("Garden")
        val made = book.create("Weekend", capture)
        book.create("Fence posts", "Runner beans along the south fence.", folderId = garden.id)
        val prefs = context.getSharedPreferences("sorta.ai.test", Context.MODE_PRIVATE)
        val settings = AIPrefsKeyValue(prefs)
        val vault = AIMemoryVault() // the Keystore vault is checked on a device (androidTest), Robolectric has no AndroidKeyStore
        val ledgerFile = File(temp.root, "ai-ledger.json")
        val keyCheck = AIRepo.fixture("http/openrouter-key.json")
        val good = split["cases"]!!.array!![0]["answer"]!!.string!!
        val net = StandInTransport { if (it.url.endsWith("/key")) StandInTransport.response(keyCheck) else aiChat(good) }
        val runner = AIRunner(vault, settings, AIRepo.prices(), AILedger(ledgerFile), net, null, AIRepo.files)
        val folders = listOf(AISplit.Folder(garden.id, "Garden", listOf("plants", "beans", "fence")))
        val errors = ArrayList<String>()

        val out = ByteArrayOutputStream()
        val (stdout, stderr) = System.out to System.err
        System.setOut(PrintStream(out, true))
        System.setErr(PrintStream(out, true))
        val outcome = try {
            offMain {
                AIConnect(vault, net).connect(AIProviderId.OPENROUTER, "  " + AIRepo.KEY + "\n")
                AISettings(settings).provider = AIProviderId.OPENROUTER
                AISettings(settings).setModel("deepseek/deepseek-v4.1-flash", AIProviderId.OPENROUTER)
                AIConsent(settings).setOn(AIFeature.SPLIT, true)
                AIConsent(settings).agree(AIFeature.SPLIT, AIProviderId.OPENROUTER)
                val o = runner.split(capture, folders)
                net.handler = { AIHttpResponse(401, "{\"error\":{\"code\":401,\"message\":\"No auth credentials found\"}}".toByteArray()) }
                (runner.split(capture, folders) as? AIOutcome.Failed)?.let { errors.add(it.error.toString()); errors.add(it.error.message ?: "") }
                thrown { AIConnect(vault, net).connect(AIProviderId.OPENAI, AIRepo.KEY) }?.let { errors.add(it.toString()); Log.w("ai-test", "refused", it) }
                o
            }
        } finally {
            System.out.println("capture-marker")
            System.setOut(stdout)
            System.setErr(stderr)
        }
        val pieces = (outcome as AIOutcome.Answered).value!!
        val kept = book.keepSplit(pieces.map { IdeaBook.SplitPiece(it.title, it.text, it.folder) }, made.id)!!
        assertEquals(3, kept.added.size)

        val key = AIRepo.KEY
        val printed = out.toString("UTF-8")
        assertTrue("the capture works", printed.contains("capture-marker"))
        assertEquals("it is in the vault, trimmed", key, vault.read(AIProviderId.OPENROUTER))
        assertFalse("not in anything printed", printed.contains(key))
        assertTrue(ShadowLog.getLogs().isNotEmpty())
        assertTrue("not in the log", ShadowLog.getLogs().none { it.msg.contains(key) || (it.throwable?.toString() ?: "").contains(key) })
        assertFalse("not in the ideas file", File(temp.root, IdeaStore.FILE_NAME).readText().contains(key))
        assertFalse("not in a capsule's data", Capsule.data(book.notes, book.folders, 0).toString().contains(key))
        assertFalse("not in a capsule's page", Capsule.html(book.notes, book.folders, book.allEdges(), 0, ZoneId.of("UTC"), null).contains(key))
        assertFalse("not in SharedPreferences", prefs.all.entries.any { it.key.contains(key) || it.value.toString().contains(key) })
        val ledger = ledgerFile.readText()
        assertTrue("the send was recorded", ledger.isNotEmpty())
        assertFalse("not in the ledger", ledger.contains(key))
        assertTrue(errors.size >= 3)
        assertTrue("not in any error: $errors", errors.none { it.contains(key) })
        assertTrue(net.requests.size >= 4)
        assertTrue("not in a request's address or body", net.requests.all { !it.url.contains(key) && !(it.body?.toString(Charsets.UTF_8) ?: "").contains(key) })
        assertTrue("only in the one header the provider reads", net.requests.all { r -> r.headers.filter { it.second.contains(key) }.map { it.first } == listOf("Authorization") })

        AIConnect(vault, net).disconnect(AIProviderId.OPENROUTER)
        assertNull(vault.read(AIProviderId.OPENROUTER))
        assertEquals(AIOutcome.Off, offMain { runner.split(capture, folders) })
        prefs.edit().clear().commit()
    }

    @Test fun theAppsAssetsAreTheRepositorysBytes() {
        val assets = AIApp.files(context)
        for (folder in listOf("prompts", "tables")) {
            val names = File(AIRepo.ai, folder).list()!!.sorted()
            assertEquals(folder, names, context.assets.list("ai/$folder")!!.sorted())
            for (name in names) assertTrue("$folder/$name", assets.read("$folder/$name").contentEquals(File(AIRepo.ai, "$folder/$name").readBytes()))
        }
        val table = AIEmbeddings.TABLE
        assertTrue(assets.read("${AIApp.EMBEDDINGS}/$table").contentEquals(File(AIRepo.ai, "embeddings/potion-base-4M/$table").readBytes()))
        assertEquals("the app's runner reads its suggested models", "claude-haiku-5-5", AIApp.runner(context).suggested[AIProviderId.ANTHROPIC])
        val e = AIEmbeddings.load(assets, AIApp.EMBEDDINGS)
        val v = AIRepo.fixture("embeddings/vectors.json")["vectors"]!!.array!![0]
        assertEquals(v["ids"]!!.array!!.map { it.int!! }, e.tokenIds(v["text"]!!.string!!))
        assertTrue("the index is kept out of backups", AIApp.index(context).file.path.startsWith(context.noBackupFilesDir.path))
    }
}
