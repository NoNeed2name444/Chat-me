package com.cramdown.sorta.ai

import android.content.Context
import android.content.res.AssetManager
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

// The AI core as the app builds it (S08): the Keystore vault, HttpURLConnection, Gemini Nano through ML Kit's Prompt
// API, and the bundled ai/ files (build.gradle.kts copies ai/prompts, ai/tables and the embedding table into the
// assets under ai/). S08u adds the screens that use it.

/** The ai/ files: prompts/, tables/ and the embedding table. */
fun interface AIFiles {
    fun read(path: String): ByteArray
}

/** The repository's ai/ folder, for tests. */
class AIDirectoryFiles(val root: File) : AIFiles {
    override fun read(path: String): ByteArray = File(root, path).readBytes()
}

/** The app's assets under ai/. */
class AIAssetFiles(private val assets: AssetManager) : AIFiles {
    override fun read(path: String): ByteArray = assets.open("ai/$path").use { it.readBytes() }
}

object AIApp {
    /** Where the embedding table is among the assets. */
    const val EMBEDDINGS = "potion-base-4M"

    fun files(context: Context): AIFiles = AIAssetFiles(context.assets)

    fun runner(context: Context): AIRunner {
        val files = files(context)
        val folder = File(context.noBackupFilesDir, "ai")
        return AIRunner(
            vault = AIKeystoreVault(folder),
            store = AIPrefsKeyValue(context.getSharedPreferences("sorta.ai", Context.MODE_PRIVATE)),
            prices = AIPrices(files.read("tables/prices.json")),
            ledger = AILedger(File(context.filesDir, "ai-ledger.json")),
            transport = AIUrlTransport(),
            onDevice = AIMlKitOnDevice(),
            files = files,
        )
    }

    /** noBackupFilesDir/ai-index.bin: never in a backup or a device transfer. */
    fun index(context: Context) = AIIndex(File(context.noBackupFilesDir, AIIndex.NAME))
}

/**
 * Android's on-device model (S08 A1, owner decision O1): Gemini Nano through ML Kit's Prompt API, free, on the phone,
 * and nothing leaves it. Where the phone lacks it (CI's emulators do) every call reports MODEL_UNAVAILABLE and nothing
 * else happens. The Prompt API takes no schema, so the operation's instructions and input go as one text and the
 * answer is checked as any other.
 */
class AIMlKitOnDevice(private val model: () -> GenerativeModel = { Generation.getClient() }) : AIProvider {
    override val id = AIProviderId.MLKIT
    override val sendsSchema = false

    /** Whether the model is on this phone now. Blocks; never on the main thread. */
    val isAvailable: Boolean
        get() {
            AIThread.requireBackground()
            return try { status(model()) == FeatureStatus.AVAILABLE } catch (_: Exception) { false }
        }

    /** The model's status, or -1 when the phone gives none within ten seconds. */
    private fun status(client: GenerativeModel): Int = runBlocking { withTimeoutOrNull(10_000) { client.checkStatus() } } ?: -1

    override fun complete(request: AIRequest): AIResponse {
        AIThread.requireBackground()
        val client = try { model() } catch (_: Exception) { throw AIError.provider(AIErrorKind.MODEL_UNAVAILABLE) }
        val status = try { status(client) } catch (_: Exception) { -1 }
        if (status != FeatureStatus.AVAILABLE) throw AIError.provider(AIErrorKind.MODEL_UNAVAILABLE)
        val text = try {
            runBlocking {
                client.generateContent(generateContentRequest(TextPart(request.instructions + "\n\n" + request.input)) {
                    maxOutputTokens = request.maxOutput
                    temperature = 0.2f
                }).candidates.firstOrNull()?.text
            }
        } catch (_: Exception) { null } ?: throw AIError.provider(AIErrorKind.TRY_LATER)
        return AIResponse(text, AIUsage(0, 0, 0))
    }
}
