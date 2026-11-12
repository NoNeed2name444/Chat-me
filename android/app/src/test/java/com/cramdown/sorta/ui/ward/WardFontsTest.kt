package com.cramdown.sorta.ui.ward

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import org.robolectric.RuntimeEnvironment
import com.cramdown.sorta.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs

// A7: Nunito and Baloo Bhaijaan 2 ship in the app with their OFL licences,
// load by name, and the kit's type draws Arabic in Baloo Bhaijaan 2 and Latin
// in Nunito. Measured on Android 15 with the platform's own text layout.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WardFontsTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    // Arabic words one at a time: the space between them is Nunito's, the first face in the fallback
    private val arabic = listOf("فكرة", "جديدة", "في", "القائمة")
    private val latin = "A new idea on the list"

    /** The family names in a TrueType file's name table (IDs 1 and 16). */
    private fun familyNames(bytes: ByteArray): Set<String> {
        fun u16(at: Int) = ((bytes[at].toInt() and 0xFF) shl 8) or (bytes[at + 1].toInt() and 0xFF)
        fun u32(at: Int) = (u16(at).toLong() shl 16) or u16(at + 2).toLong()
        val tables = u16(4)
        var name = -1
        for (i in 0 until tables) {
            val r = 12 + 16 * i
            if (String(bytes, r, 4, Charsets.US_ASCII) == "name") name = u32(r + 8).toInt()
        }
        if (name < 0) return emptySet()
        val count = u16(name + 2)
        val strings = name + u16(name + 4)
        val found = mutableSetOf<String>()
        for (i in 0 until count) {
            val r = name + 6 + 12 * i
            val platform = u16(r)
            val id = u16(r + 6)
            if (id != 1 && id != 16) continue
            val length = u16(r + 8)
            val at = strings + u16(r + 10)
            found += if (platform == 3 || platform == 0) String(bytes, at, length, Charsets.UTF_16BE) else String(bytes, at, length, Charsets.ISO_8859_1)
        }
        return found
    }

    @Test fun theFontsLoadByName() = checks {
        for ((resource, family) in listOf("nunito" to "Nunito", "nunito_italic" to "Nunito", "baloo_bhaijaan_2" to "Baloo Bhaijaan 2")) {
            val id = context.resources.getIdentifier(resource, "font", context.packageName)
            check("res/font/$resource is in the app", id != 0)
            if (id == 0) continue
            val bytes = context.resources.openRawResource(id).use { it.readBytes() }
            val names = familyNames(bytes)
            check("res/font/$resource is $family", family in names, names.toString())
            val face = runCatching { androidx.core.content.res.ResourcesCompat.getFont(context, id) }.getOrNull()
            check("res/font/$resource loads as a typeface", face != null)
        }
        check("the kit names them by resource", R.font.nunito != 0 && R.font.nunito_italic != 0 && R.font.baloo_bhaijaan_2 != 0)
    }

    @Test fun theLicencesShip() = checks {
        for (name in listOf("nunito_OFL.txt", "baloo_bhaijaan_2_OFL.txt")) {
            val text = runCatching { context.assets.open("licenses/$name").use { String(it.readBytes()) } }.getOrNull()
            check("assets/licenses/$name ships", text != null)
            check("assets/licenses/$name is the SIL Open Font License 1.1",
                text != null && text.contains("SIL OPEN FONT LICENSE") && text.contains("Version 1.1"))
        }
    }

    @Test fun theKitsTypeIsSortasFaces() = checks {
        for (spec in listOf(WardType.body, WardType.title, WardType.buttonPrimary, WardType.caption)) {
            val style = spec.textStyle()
            check("$spec: the kit's family", style.fontFamily == WardFonts.family)
        }
        check("observations stay mono", WardType.obs.textStyle().fontFamily == androidx.compose.ui.text.font.FontFamily.Monospace)
        val source = File("src/main/java/com/cramdown/sorta/ui/ward/WardType.kt")
        check("WardType.kt is found", source.exists(), source.absolutePath)
        check("the kit's family falls back from Nunito to Baloo Bhaijaan 2",
            source.readText().contains("CustomFallbackBuilder") && source.readText().contains("R.font.baloo_bhaijaan_2"))
    }

    private fun width(face: Typeface, text: String): Float = Paint().apply { typeface = face; textSize = 40f }.measureText(text)
    private fun width(face: Typeface, words: List<String>): Float = words.sumOf { width(face, it).toDouble() }.toFloat()

    /** A typeface of one variable font at a weight, no fallback of ours. */
    private fun alone(id: Int, weight: Int): Typeface {
        val font = android.graphics.fonts.Font.Builder(context.resources, id).setWeight(weight)
            .setFontVariationSettings("'wght' $weight").build()
        return Typeface.CustomFallbackBuilder(android.graphics.fonts.FontFamily.Builder(font).build())
            .setStyle(android.graphics.fonts.FontStyle(weight, android.graphics.fonts.FontStyle.FONT_SLANT_UPRIGHT)).build()
    }

    @Test fun arabicIsDrawnInBalooBhaijaan2() = checks {
        val resolver = createFontFamilyResolver(context)
        for (spec in listOf(WardType.body, WardType.headline, WardType.buttonPrimary)) {
            val style = spec.textStyle()
            val w = style.fontWeight!!.weight
            val kit = resolver.resolve(style.fontFamily, style.fontWeight!!, FontStyle.Normal).value as? Typeface
            check("$spec: the kit's type resolves to a typeface", kit != null)
            if (kit == null) continue
            val baloo = alone(R.font.baloo_bhaijaan_2, w)
            val nunito = alone(R.font.nunito, w)
            val kitArabic = width(kit, arabic)
            val balooArabic = width(baloo, arabic)
            val plainArabic = width(Typeface.DEFAULT, arabic)
            check("$spec: Arabic in the kit's type measures as Baloo Bhaijaan 2", abs(kitArabic - balooArabic) < 0.01f,
                "kit ${f2(kitArabic.toDouble())}, Baloo ${f2(balooArabic.toDouble())}")
            check("$spec: and not as the system's font (the check bites)", abs(plainArabic - balooArabic) > 1f,
                "system ${f2(plainArabic.toDouble())}, Baloo ${f2(balooArabic.toDouble())}")
            val kitLatin = width(kit, latin)
            check("$spec: Latin in the kit's type measures as Nunito", abs(kitLatin - width(nunito, latin)) < 0.01f,
                "kit ${f2(kitLatin.toDouble())}, Nunito ${f2(width(nunito, latin).toDouble())}")
            check("$spec: and not as Baloo Bhaijaan 2's Latin (the check bites)", abs(kitLatin - width(baloo, latin)) > 1f,
                "Baloo ${f2(width(baloo, latin).toDouble())}")
        }
    }
}
