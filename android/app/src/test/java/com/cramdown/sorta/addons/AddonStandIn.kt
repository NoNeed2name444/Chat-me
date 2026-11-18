package com.cramdown.sorta.addons

import com.cramdown.sorta.data.Note
import com.cramdown.sorta.ideas.NoteFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.CRC32

// The stand-in engine and what the add-on tests share (S07b). The stand-in is Kotlin and never runs
// JavaScript: each add-on is a script, a Kotlin block that makes the API calls the add-on's code would make
// and gives the result. It is what the JVM tests run on, here and with -Psorta.addonsEngine=standin; the
// engine itself (libsorta_addons.so) is tested on the emulator (src/androidTest/.../addons).

/** What a scripted add-on can do: call the API as the prelude would, and see whether it was stopped. */
class AddonScriptRun(private val host: (String, String) -> String, private val stop: () -> Boolean, val kind: String, val id: String,
    val input: String) {
    class Thrown(val code: String, val text: String) : Exception(code)

    val stopped: Boolean get() = stop()

    /** One API call: its JSON text ("" for undefined); a refusal is thrown, as the add-on's own code would see it. */
    fun api(name: String, vararg args: AddonJson): String {
        val reply = host(name, AddonJson.Arr(args.toList()).canonical)
        if (reply.startsWith("!")) {
            val space = reply.indexOf(' ')
            throw if (space < 0) Thrown(reply.substring(1), reply.substring(1)) else Thrown(reply.substring(1, space), reply.substring(space + 1))
        }
        return reply
    }
}

/** [load] is the add-on's top level; [call] answers a command, card view or settings change with its value as JSON text. */
class AddonScript(val load: AddonScriptRun.() -> Unit = {}, val call: AddonScriptRun.() -> String = { "null" })

class AddonStandInEngine(private val host: (String, String) -> String, private val script: AddonScript) : AddonEngine {
    @Volatile private var stopping = false
    @Volatile private var running = false
    private var used = false
    override var lastRunMs = 0.0
        private set
    override val spent: Boolean get() = used && stopping

    private fun guarded(run: AddonScriptRun, work: AddonScriptRun.() -> String): String {
        val start = System.nanoTime()
        running = true
        val out = try {
            val value = run.work()
            if (stopping) AddonCall.failure("stopped") else """{"ok":true,"value":$value}"""
        } catch (t: AddonScriptRun.Thrown) {
            AddonCall.failure(t.code, t.text)
        }
        running = false
        lastRunMs = (System.nanoTime() - start) / 1e6
        used = true
        return out
    }

    override fun load(files: AddonFiles): String? {
        val out = guarded(AddonScriptRun(host, { stopping }, "load", "", "")) { script.load(this); "null" }
        return AddonJson.parse(out)["code"]?.string
    }

    override fun call(kind: String, id: String, input: String, runMs: Int, wallMs: Int): String =
        guarded(AddonScriptRun(host, { stopping }, kind, id, input), script.call)

    /** As the engine: only a running call is stopped. */
    override fun stop() { if (running) stopping = true }
    override fun free() {}

    companion object {
        /** A maker for one script, and how many runtimes it made. */
        class Maker(private val script: AddonScript) : AddonEngineMaker {
            @Volatile var made = 0
            override fun make(host: (String, String) -> String): Pair<AddonEngine?, String> {
                made++
                return AddonStandInEngine(host, script) to ""
            }
        }
    }
}

object AddonTestFiles {
    /** A file of addons/fixtures or addons/conformance, which the test source set takes as resources. */
    fun file(path: String): File = File(requireNotNull(javaClass.classLoader!!.getResource(path)) { "no fixture $path" }.toURI())
    fun text(path: String): String = file(path).readText(Charsets.UTF_8)
    fun json(path: String): AddonJson = AddonJson.parse(text(path))

    fun temporaryFolder(): File = File.createTempFile("sorta-addons", "").let { it.delete(); it.mkdirs(); it.deleteOnExit(); it }

    fun id(n: Int) = "00000000-0000-4000-8000-" + n.toString().padStart(12, '0')

    /** The conformance store as the book's notes and folders. */
    fun store(): Pair<List<Note>, List<NoteFolder>> {
        val snap = AddonSnapshot.read(json("store.json"))
        return snap.ideas.map { i ->
            Note(id = i.id, title = i.title, body = i.text, folderId = i.folder, kind = i.kind, links = i.links, tags = i.tags,
                createdAt = i.createdAt, updatedAt = i.updatedAt)
        } to snap.folders.map { f -> NoteFolder(id = f.id, name = f.name, parentId = f.parent, words = f.words) }
    }

    /** A made-up add-on's manifest. */
    fun manifest(id: String = "io.github.example.tests", version: String = "1.0.0", permissions: List<String> = listOf("ideas.read", "ideas.write", "commands"),
        hosts: List<String> = emptyList(), api: Int = 1, minSorta: String = "1.0.0", more: List<Pair<String, AddonJson>> = emptyList()): String =
        AddonJson.Obj(listOf(
            "format" to AddonJson.str("sorta-addon"), "formatVersion" to AddonJson.num(1), "id" to AddonJson.str(id),
            "name" to AddonJson.str("Tests"), "version" to AddonJson.str(version),
            "author" to AddonJson.obj("name" to AddonJson.str("Example"), "url" to AddonJson.str("https://github.com/example")),
            "license" to AddonJson.str("MIT"), "minSorta" to AddonJson.str(minSorta), "api" to AddonJson.num(api),
            "main" to AddonJson.str("main.js"), "permissions" to AddonJson.strings(permissions), "hosts" to AddonJson.strings(hosts),
            "contributes" to AddonJson.Obj((if ("commands" in permissions) listOf("commands" to AddonJson.Arr(listOf(
                AddonJson.obj("id" to AddonJson.str("run"), "title" to AddonJson.str("Run"))))) else emptyList()) +
                (if ("cards" in permissions) listOf("cardView" to AddonJson.obj("id" to AddonJson.str("view"), "title" to AddonJson.str("View"))) else emptyList())),
        ) + more).canonical

    /** A made-up add-on as a `.sortaaddon` file. */
    fun addon(manifest: String = manifest(), files: Map<String, String> = emptyMap()): ByteArray =
        AddonZipWriter.stored(mapOf("manifest.json" to manifest, "main.js" to "// scripted in the tests\n", "LICENSE" to "MIT\n") + files)
}

/** A small, test-only zip writer: stored entries, sorted by name, nothing that varies (the kit's sortazip.py layout). */
object AddonZipWriter {
    class Entry(val name: ByteArray, val data: ByteArray, val method: Int = 0, val packed: ByteArray = data, val flags: Int = 0,
        val external: Long = 0x81A4L shl 16, val crc: Long = CRC32().also { it.update(data) }.value, val size: Int = data.size)

    fun stored(files: Map<String, String>): ByteArray =
        write(files.toSortedMap(compareBy { it }).map { Entry(it.key.toByteArray(Charsets.UTF_8), it.value.toByteArray(Charsets.UTF_8)) })

    fun folder(dir: File): ByteArray {
        val files = dir.walkTopDown().filter { it.isFile && !it.name.startsWith(".") }.map { it.relativeTo(dir).invariantSeparatorsPath to it.readBytes() }
        return write(files.sortedBy { it.first }.map { Entry(it.first.toByteArray(Charsets.UTF_8), it.second) }.toList())
    }

    private fun ByteArrayOutputStream.u16(v: Int) { write(v and 0xFF); write((v shr 8) and 0xFF) }
    private fun ByteArrayOutputStream.u32(v: Long) { u16((v and 0xFFFF).toInt()); u16(((v shr 16) and 0xFFFF).toInt()) }

    fun write(entries: List<Entry>, comment: ByteArray = ByteArray(0)): ByteArray {
        val out = ByteArrayOutputStream()
        val offsets = ArrayList<Int>()
        for (e in entries) {
            offsets.add(out.size())
            out.u32(0x04034B50); out.u16(20); out.u16(e.flags); out.u16(e.method); out.u16(0); out.u16(0x21)
            out.u32(e.crc); out.u32(e.packed.size.toLong()); out.u32(e.size.toLong()); out.u16(e.name.size); out.u16(0)
            out.write(e.name); out.write(e.packed)
        }
        val dirAt = out.size()
        entries.forEachIndexed { k, e ->
            out.u32(0x02014B50); out.u16(0x0314); out.u16(20); out.u16(e.flags); out.u16(e.method); out.u16(0); out.u16(0x21)
            out.u32(e.crc); out.u32(e.packed.size.toLong()); out.u32(e.size.toLong()); out.u16(e.name.size); out.u16(0); out.u16(0)
            out.u16(0); out.u16(0); out.u32(e.external); out.u32(offsets[k].toLong())
            out.write(e.name)
        }
        val dirSize = out.size() - dirAt
        out.u32(0x06054B50); out.u16(0); out.u16(0); out.u16(entries.size); out.u16(entries.size)
        out.u32(dirSize.toLong()); out.u32(dirAt.toLong()); out.u16(comment.size); out.write(comment)
        return out.toByteArray()
    }
}
