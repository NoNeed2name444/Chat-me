package com.cramdown.sorta.ui.ideas

import android.graphics.Bitmap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.cramdown.sorta.R
import com.cramdown.sorta.data.Capsule
import com.cramdown.sorta.ideas.BoardPoint
import com.cramdown.sorta.ideas.IdeaBoardDrop
import com.cramdown.sorta.ideas.IdeaBoardSpread
import com.cramdown.sorta.ideas.IdeaBook
import com.cramdown.sorta.ideas.IdeaLinkShape
import com.cramdown.sorta.ideas.noteKind
import com.cramdown.sorta.ui.ward.*
import com.cramdown.sorta.ui.wardColor
import java.io.ByteArrayOutputStream
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min

// The capsule on screen (S06d A4), as iOS's CapsuleViews: the monthly offer under the capture well,
// and the map's picture the page shows, drawn at save time from the board's own pieces.

/** The offer's tags, as iOS's identifiers. */
object CapsuleTags {
    const val OFFER = "capsule-offer"
    const val OFFER_SAVE = "capsule-offer-save"
    const val OFFER_LATER = "capsule-offer-later"
}

/**
 * The quiet offer: the kit's banner well (WardBanner's inset, glyph and words) with its two actions
 * under the words, at the end, each a full target: Not now, then Save a capsule.
 */
@Composable fun CapsuleOfferCard(onSave: () -> Unit, onLater: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().wardInset(WardRadius.field, fill = WardControlStates.wellFill)
        .padding(start = WardSpace.m.wardDp, end = WardSpace.m.wardDp, top = WardSpace.m.wardDp, bottom = WardSpace.xs.wardDp)
        .semantics { testTag = CapsuleTags.OFFER }) {
        Row(horizontalArrangement = Arrangement.spacedBy(WardSpace.s.wardDp)) {
            Icon(Icons.Rounded.Inventory2, null, tint = WardTone.Blue.color(), modifier = Modifier.size(wardGlyph(18.0)))
            Text(stringResource(R.string.capsule_offer), Modifier.weight(1f), style = WardTextStyle.subheadline.textStyle(),
                color = wardColor(WardToken.ink))
        }
        Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(WardSpace.m.wardDp)) {
            @Composable fun Action(text: String, tag: String, weight: WardWeight, onClick: () -> Unit) =
                Box(Modifier.defaultMinSize(WardControlSize.target.wardDp, WardControlSize.target.wardDp)
                    .clickable(role = Role.Button, onClick = onClick).semantics { testTag = tag }, contentAlignment = Alignment.Center) {
                    Text(text, style = WardTextStyle.subheadline.textStyle(weight), color = wardColor(WardToken.primaryInk))
                }
            Action(stringResource(R.string.capsule_offer_later), CapsuleTags.OFFER_LATER, WardWeight.regular, onLater)
            Action(stringResource(R.string.capsule_offer_save), CapsuleTags.OFFER_SAVE, WardWeight.semibold, onSave)
        }
    }
}

/** The capsule's page for [book] as it is now, with the board's picture ([capsulePicture]) and the phone's time zone. */
fun capsuleHtml(book: IdeaBook, straight: Boolean, measurer: TextMeasurer, density: Density, now: Long = System.currentTimeMillis(),
                zone: ZoneId = ZoneId.systemDefault()): String =
    Capsule.html(book.notes, book.folders, book.allEdges(), now, zone, capsulePicture(book, straight, measurer, density))

/** The page's picture at most this wide and tall, in CSS pixels; drawn at twice that. */
private const val PICTURE_MAX = 960.0
private const val PICTURE_SCALE = 2f

/**
 * The board as the person last left it, in day's colours: every card at its drawn place
 * (IdeaBoardSpread.places, the board's), its folder's wash and name or its kind, its title on one
 * line; the links as the board draws them (IdeaLinkShape, Straight or Curved). Null with no ideas.
 */
fun capsulePicture(book: IdeaBook, straight: Boolean, measurer: TextMeasurer, density: Density): Capsule.Picture? {
    val notes = book.notes
    if (notes.isEmpty()) return null
    val w = IdeaBoardDrop.CARD_WIDTH; val h = IdeaBoardDrop.CARD_HEIGHT
    val drawn = IdeaBoardSpread.places(notes.map { IdeaBoardSpread.Card(it.id, BoardPoint(it.boardX, it.boardY), w, h) })
    fun at(id: String) = drawn[id] ?: notes.first { it.id == id }.let { BoardPoint(it.boardX, it.boardY) }
    val margin = WardSpace.xl
    val minX = notes.minOf { at(it.id).x } - w / 2 - margin; val maxX = notes.maxOf { at(it.id).x } + w / 2 + margin
    val minY = notes.minOf { at(it.id).y } - h / 2 - margin; val maxY = notes.maxOf { at(it.id).y } + h / 2 + margin
    val fit = min(1.0, min(PICTURE_MAX / (maxX - minX), PICTURE_MAX / (maxY - minY)))
    val cssW = max(1, ((maxX - minX) * fit).toInt()); val cssH = max(1, ((maxY - minY) * fit).toInt())
    val k = (fit * PICTURE_SCALE).toFloat()
    fun place(p: BoardPoint) = BoardPoint((p.x - minX) * k, (p.y - minY) * k)
    fun day(token: WardToken) = Color(0xFF000000 or WardPalette.table.getValue(token).light.toLong())
    val image = ImageBitmap((cssW * PICTURE_SCALE).toInt(), (cssH * PICTURE_SCALE).toInt())
    val one = Density(1f, 1f)
    CanvasDrawScope().draw(one, LayoutDirection.Ltr, Canvas(image), Size(image.width.toFloat(), image.height.toFloat())) {
        drawRect(day(WardToken.background))
        val edges = book.allEdges()
        val lanes = IdeaLinkShape.lanes(edges.map { (a, b) -> "$a|$b" })
        val known = notes.map { it.id }.toSet()
        edges.forEachIndexed { i, (a, b) ->
            if (a !in known || b !in known) return@forEachIndexed
            val p = place(at(a)); val q = place(at(b))
            val (lane, count) = lanes[i]
            val path = Path()
            if (straight) {
                val (from, to) = IdeaLinkShape.straightEnds(p, q, lane, count)
                path.moveTo(from.x.toFloat(), from.y.toFloat()); path.lineTo(to.x.toFloat(), to.y.toFloat())
            } else {
                val c = IdeaLinkShape.curve(p, q, lane, count)
                path.moveTo(c.start.x.toFloat(), c.start.y.toFloat())
                path.quadraticTo(c.control.x.toFloat(), c.control.y.toFloat(), c.end.x.toFloat(), c.end.y.toFloat())
            }
            drawPath(path, day(WardToken.inkSecondary), style = Stroke(width = max(1f, 1.5f * k), cap = StrokeCap.Round))
        }
        val pad = (WardSpace.m * k).toFloat()
        val cw = (w * k).toFloat(); val ch = (h * k).toFloat()
        val corner = CornerRadius((WardRadius.card * k).toFloat())
        val caption = TextStyle(color = day(WardToken.inkSecondary), fontSize = (11 * k).sp)
        val title = TextStyle(color = day(WardToken.ink), fontSize = (15 * k).sp, fontWeight = FontWeight.Medium)
        for (note in notes) {
            val c = place(at(note.id))
            val left = c.x.toFloat() - cw / 2; val top = c.y.toFloat() - ch / 2
            val tone = toneColor(note.folderId, book)
            drawRoundRect(day(WardToken.raised), Offset(left, top), Size(cw, ch), corner)
            drawRoundRect(tone.copy(alpha = 0.14f), Offset(left, top), Size(cw, ch), corner)
            drawRoundRect(day(WardToken.hairline), Offset(left, top), Size(cw, ch), corner, style = Stroke(max(1f, k)))
            val dot = (4 * k)
            drawCircle(tone, dot, Offset(left + pad + dot, top + pad + 7 * k))
            val words = book.folder(note.folderId)?.name ?: note.noteKind.label
            val inner = max(1, (cw - 2 * pad - 3 * dot).toInt())
            drawText(measurer.measure(words, caption, maxLines = 1, overflow = TextOverflow.Ellipsis, density = one,
                constraints = Constraints(maxWidth = inner)), topLeft = Offset(left + pad + 3 * dot, top + pad))
            drawText(measurer.measure(note.title.trim().ifEmpty { Capsule.Words.UNTITLED }, title, maxLines = 2,
                overflow = TextOverflow.Ellipsis, density = one, constraints = Constraints(maxWidth = max(1, (cw - 2 * pad).toInt()))),
                topLeft = Offset(left + pad, top + pad + 18 * k))
        }
    }
    val out = ByteArrayOutputStream()
    image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, out)
    return Capsule.Picture(out.toByteArray(), cssW, cssH)
}
