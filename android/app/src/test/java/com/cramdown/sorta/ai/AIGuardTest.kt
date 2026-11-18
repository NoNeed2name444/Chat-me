package com.cramdown.sorta.ai

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// S08 A8 on the JVM: no PRO_PAYS symbol in either app, no key-shaped constant in either app or in what ai/ bundles
// (the mirror guard's own patterns, read from tools/mirror_chatme.py), no Gemini API library in the Gradle files or
// the resolved debugRuntimeClasspath (build/ai-guard, written by the aiClasspath task), and no Gemini model in
// ai/tables/models.json. On-device Gemini Nano through ML Kit is allowed (owner decision O1).
class AIGuardTest {
    private val repo = AIRepo.ai.parentFile!!
    private fun walk(path: String) = File(repo, path).walkTopDown().filter { it.isFile }.toList()

    @Test fun noProPaysInEitherApp() {
        val sources = walk("ios/Sorta") + walk("android/app/src/main")
        assertTrue(sources.size > 40)
        assertEquals(emptyList<String>(), sources.filter { it.readText().contains("PRO_PAYS") }.map { it.name })
    }

    /** The KEY patterns in tools/mirror_chatme.py: each `KEY = re.compile(...)` statement's rb"..." pieces joined. */
    private fun mirrorKeyPattern(): String {
        val lines = File(repo, "tools/mirror_chatme.py").readLines()
        var pattern = ""
        var i = 0
        while (i < lines.size) {
            if (!lines[i].startsWith("KEY = re.compile(")) { i++; continue }
            var statement = lines[i]
            var depth = statement.count { it == '(' } - statement.count { it == ')' }
            while (depth > 0 && i + 1 < lines.size) {
                i++
                statement += "\n" + lines[i]
                depth += lines[i].count { it == '(' } - lines[i].count { it == ')' }
            }
            val pieces = Regex("rb\"([^\"]*)\"").findAll(statement).joinToString("") { it.groupValues[1] }
            pattern = if (statement.contains("KEY.pattern")) pattern + pieces else pieces
            i++
        }
        return pattern
    }

    @Test fun noKeyInEitherAppOrInWhatAiBundles() {
        val pattern = mirrorKeyPattern()
        assertTrue(pattern, pattern.contains("sk-ant-") && pattern.contains("sk-or-v1-"))
        val key = Regex(pattern)
        // fakes built from halves, so no source file holds one whole
        val hex64 = "0a".repeat(32)
        for (fake in listOf("sk-or-" + "v1-" + hex64, "sk-" + "proj-" + "Ab3_".repeat(8), "hf" + "_" + "aB3".repeat(11), "sk-" + "ant-" + "api03-" + "x".repeat(20))) {
            assertTrue(fake.take(8), key.containsMatchIn("val k = \"$fake\""))
        }
        assertFalse(key.containsMatchIn("fake-key-for-tests-1"))
        val bundled = walk("ios/Sorta") + walk("android/app/src/main") + walk("ai/tables") + walk("ai/prompts") + walk("ai/embeddings/potion-base-4M")
        val keyed = bundled.filter { key.containsMatchIn(String(it.readBytes(), Charsets.ISO_8859_1)) }
        assertEquals("${bundled.size} files", emptyList<String>(), keyed.map { it.name })
    }

    private val banned = listOf("firebase-ai", "firebase-vertexai", "generativeai", "com.google.ai.client", "google-genai", "com.google.genai")

    @Test fun noGeminiApiLibraryInTheBuildOrItsClasspath() {
        val gradle = walk("android").filter { it.name.endsWith(".gradle.kts") || it.name.endsWith(".toml") || it.name == "gradle.properties" }
        assertTrue(gradle.any { it.name == "build.gradle.kts" })
        for (f in gradle) for (b in banned) assertFalse("${f.name}: $b", f.readText().contains(b))
        assertTrue("ML Kit's on-device Prompt API (O1)", File(repo, "android/app/build.gradle.kts").readText().contains("com.google.mlkit:genai-prompt:"))

        val classpath = File(repo, "android/app/build/ai-guard/debugRuntimeClasspath.txt").readLines().filter { it.isNotBlank() }
        assertTrue("the resolved classpath was written (${classpath.size})", classpath.size > 50)
        assertTrue(classpath.any { it.startsWith("com.google.mlkit:genai-prompt:") })
        for (b in banned) assertEquals(b, emptyList<String>(), classpath.filter { it.contains(b) })
    }

    @Test fun noGeminiModelInTheModelTable() {
        val models = File(AIRepo.ai, "tables/models.json").readText()
        assertTrue(models.contains("\"providers\""))
        assertFalse(models.lowercase().contains("gemini"))
        val providers = AIJSON.parse(models)!!["providers"]!!
        assertTrue(AIProviderId.entries.all { providers[it.raw] != null })
    }
}
