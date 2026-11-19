package com.cramdown.sorta.ui.map

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.core.content.res.ResourcesCompat
import com.cramdown.sorta.R
import com.cramdown.sorta.map.TreeColors
import com.cramdown.sorta.map.nest.NestNodes
import com.cramdown.sorta.ui.ward.WardControlSize
import kotlin.math.max
import kotlin.math.roundToInt

/** The Tree's tags (S11): the map, its Back, its first-visit card and each name's TalkBack element. */
object TreeTags {
    const val MAP = "tree-map"
    const val BACK = "tree-back"
    const val FIRST_VISIT = "tree-first-visit"
    fun part(id: String) = "tree-part-$id"
}

/**
 * The Tree's words (S11): Nunito, a branch's at 800 and 13 sp, a leaf's at 700 and 11.5 sp, each on a
 * halo of the page's colour, made once per map. [density] is pixels per dp.
 */
internal class TreeType(context: Context, density: Float) {
    private val face = runCatching { ResourcesCompat.getFont(context, R.font.nunito) }.getOrNull() ?: Typeface.DEFAULT_BOLD
    private val sp = context.resources.displayMetrics.scaledDensity.takeIf { it > 0f } ?: density
    private val bough = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = face; fontVariationSettings = "'wght' 800"; textAlign = Paint.Align.CENTER }
    private val leaf = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = face; fontVariationSettings = "'wght' 700"; textAlign = Paint.Align.CENTER }
    val boughs: NameMetrics = Metrics(bough)
    val leaves: NameMetrics = Metrics(leaf)
    val style = TreeNames.Style(bough = 13f * sp, leaf = 11.5f * sp, halo = 2.5f * density, gapX = 6f * density, gapY = 3f * density,
        edge = 12f * density, leastLeaf = 5f * density)
    private val haloWidth = style.halo * 2
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = density; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }

    fun draw(canvas: android.graphics.Canvas, names: List<PlacedName>, colors: TreeColors) {
        // a name set out past the crown has a short leader back to its branch's tip, under the words
        line.color = (0x8C shl 24) or colors.ink
        for (name in names) if (!name.leadX.isNaN()) {
            val near = if (name.leadX < name.box.x) name.box.x - name.box.width / 2 + style.halo else name.box.x + name.box.width / 2 - style.halo
            canvas.drawLine(near, name.box.y, name.leadX, name.leadY, line)
        }
        for (name in names) {
            val p = if (name.textSize == style.bough) bough else leaf
            val metrics = if (p === bough) boughs else leaves
            p.textSize = name.textSize
            val base = name.baseline(metrics)
            p.style = Paint.Style.STROKE; p.strokeWidth = haloWidth; p.strokeJoin = Paint.Join.ROUND
            p.color = 0xFF000000.toInt() or colors.halo
            canvas.drawText(name.label, name.box.x, base, p)
            p.style = Paint.Style.FILL; p.color = 0xFF000000.toInt() or colors.ink
            canvas.drawText(name.label, name.box.x, base, p)
        }
    }

    private class Metrics(private val paint: Paint) : NameMetrics {
        private fun at(size: Float) = paint.also { it.textSize = size }
        override fun width(label: String, size: Float) = at(size).measureText(label)
        override fun ascent(size: Float) = at(size).fontMetrics.ascent
        override fun descent(size: Float) = at(size).fontMetrics.descent
    }
}

/**
 * TalkBack's elements for the Tree's names (S11): one a name, over its words and at least a touch
 * target (48 dp) each way, read as the name ("…, folder" for a folder) with what activating it does.
 * They take no touches, so a finger still reaches the map under them.
 */
@Composable internal fun TreeNameTargets(names: List<PlacedName>, nodes: NestNodes, activate: (Int) -> Unit) {
    val density = LocalDensity.current.density
    val folderWord = stringResource(R.string.tree_folder_spoken, "%s")
    val zoomHint = stringResource(R.string.tree_zoom_hint)
    val ideaHint = stringResource(R.string.tree_idea_hint)
    val target = WardControlSize.target.toFloat() * density
    for (name in names) {
        val i = name.index
        val w = max(target, name.box.width); val h = max(target, name.box.height)
        val leaf = i > 0 && nodes.childCount[i] == 0
        val words = nodes.titles[i]
        Box(Modifier.offset { IntOffset((name.box.x - w / 2).roundToInt(), (name.box.y - h / 2).roundToInt()) }
            .size(androidx.compose.ui.unit.Dp(w / density), androidx.compose.ui.unit.Dp(h / density))
            .testTag(TreeTags.part(nodes.ids[i]))
            .semantics {
                contentDescription = if (nodes.folder[i]) folderWord.replace("%s", words) else words
                onClick(if (leaf) ideaHint else zoomHint) { activate(i); true }
            })
    }
}
