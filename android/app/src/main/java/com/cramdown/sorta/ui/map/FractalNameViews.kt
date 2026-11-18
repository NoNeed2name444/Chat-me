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
import com.cramdown.sorta.map.FractalModel
import com.cramdown.sorta.map.FractalTones
import com.cramdown.sorta.map.FractalTree
import com.cramdown.sorta.map.GraphicsTier
import com.cramdown.sorta.map.MapGraph
import com.cramdown.sorta.map.Octree
import com.cramdown.sorta.ui.ward.WardControlSize
import kotlin.math.max
import kotlin.math.roundToInt

/** The Fractal's tags (S12): the map, its Back, its first-visit card and each name's TalkBack element. */
object FractalTags {
    const val MAP = "fractal-map"
    const val BACK = "fractal-back"
    const val FIRST_VISIT = "fractal-first-visit"
    fun part(id: String) = "fractal-part-$id"
}

/** The Fractal's first view looks a little down on All ideas, as Performance's does, so its depth reads. */
internal const val FRACTAL_PITCH = (22.0 * Math.PI / 180.0).toFloat()

/**
 * The Fractal as the 3D map's scene (S12): node i is the tree's node i at its place (FractalLayout3D),
 * its group its region; [MapScene.ids] are the store's ids by node, All ideas' "". FractalGl draws it.
 */
fun fractalScene(model: FractalModel): MapScene {
    val t = model.tree; val l = model.layout
    val positions = FloatArray(t.count * 3) { l.pos[it].toFloat() }
    val groups = ByteArray(t.count) { (t.region[it].coerceAtLeast(0) % MapGraph.PALETTE_SIZE).toByte() }
    val graph = MapGraph(positions, groups, IntArray(0), t.titles.toList(), t.ids.toList())
    val octree = Octree.build(graph, 4096)
    return MapScene(octree, model.smallest, graph.titles, graph.ids, l.reach[0].toFloat(), MapTheme.FRACTAL,
        closest = model.smallest * 4, fractalModel = model)
}

/** The Graphics tier's budget of dots, for [tier] on a device that is or isn't short of memory (FractalModel.budgetFor). */
fun fractalBudget(tier: GraphicsTier, lowMemory: Boolean): Int = FractalModel.budgetFor(tier, lowMemory)

/**
 * The Fractal's words (S12): Nunito as the Tree's (TreeType's sizes), white on a halo of Performance's
 * sky, so a name reads over a line or a glow. [density] is pixels per dp.
 */
internal class FractalType(context: Context, density: Float) {
    private val face = runCatching { ResourcesCompat.getFont(context, R.font.nunito) }.getOrNull() ?: Typeface.DEFAULT_BOLD
    private val sp = context.resources.displayMetrics.scaledDensity.takeIf { it > 0f } ?: density
    private val hubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = face; fontVariationSettings = "'wght' 800"; textAlign = Paint.Align.CENTER }
    private val ideaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = face; fontVariationSettings = "'wght' 700"; textAlign = Paint.Align.CENTER }
    val hubs: NameMetrics = Metrics(hubPaint)
    val ideas: NameMetrics = Metrics(ideaPaint)
    val style = TreeNames.Style(bough = 13f * sp, leaf = 11.5f * sp, halo = 2.5f * density, gapX = 6f * density, gapY = 3f * density,
        edge = 12f * density, leastLeaf = 5f * density)
    private val haloWidth = style.halo * 2

    fun draw(canvas: android.graphics.Canvas, names: List<PlacedName>) {
        for (name in names) {
            val p = if (name.textSize == style.bough) hubPaint else ideaPaint
            val metrics = if (p === hubPaint) hubs else ideas
            p.textSize = name.textSize
            val base = name.baseline(metrics)
            p.style = Paint.Style.STROKE; p.strokeWidth = haloWidth; p.strokeJoin = Paint.Join.ROUND
            p.color = 0xFF000000.toInt() or FractalTones.SKY
            canvas.drawText(name.label, name.box.x, base, p)
            p.style = Paint.Style.FILL; p.color = 0xFF000000.toInt() or FractalTones.INK
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
 * TalkBack's elements for the Fractal's names (S12): one a name, over its words and at least a touch
 * target (48 dp) each way, read as the name ("…, folder" for a hub) with what activating it does. They
 * take no touches, so a finger still reaches the map under them.
 */
@Composable internal fun FractalNameTargets(names: List<PlacedName>, tree: FractalTree, activate: (Int) -> Unit) {
    val density = LocalDensity.current.density
    val folderWord = stringResource(R.string.fractal_folder_spoken, "%s")
    val zoomHint = stringResource(R.string.fractal_zoom_hint)
    val ideaHint = stringResource(R.string.fractal_idea_hint)
    val target = WardControlSize.target.toFloat() * density
    for (name in names) {
        val i = name.index
        val w = max(target, name.box.width); val h = max(target, name.box.height)
        val idea = i > 0 && tree.childCount[i] == 0 && tree.kind[i] == FractalTree.IDEA
        val words = tree.titles[i]
        Box(Modifier.offset { IntOffset((name.box.x - w / 2).roundToInt(), (name.box.y - h / 2).roundToInt()) }
            .size(androidx.compose.ui.unit.Dp(w / density), androidx.compose.ui.unit.Dp(h / density))
            .testTag(FractalTags.part(tree.ids[i]))
            .semantics {
                contentDescription = if (idea) words else folderWord.replace("%s", words)
                onClick(if (idea) ideaHint else zoomHint) { activate(i); true }
            })
    }
}
