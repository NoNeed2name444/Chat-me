package com.cramdown.sorta.ui.ideas

import android.graphics.Bitmap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.draw
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.cramdown.sorta.R
import com.cramdown.sorta.data.Capsule
import com.cramdown.sorta.ideas.BoardPoint
import com.cramdown.sorta.ideas.IdeaBoardDrop
import com.cramdown.sorta.ideas.IdeaBoardSpread
import com.cramdown.sorta.ideas.IdeaBook
import com.cramdown.sorta.ui.LocalWardDark
import com.cramdown.sorta.ui.LocalWardLook
import com.cramdown.sorta.ui.ward.*
import com.cramdown.sorta.ui.ward.WardLook
import com.cramdown.sorta.ui.wardColor
import java.io.ByteArrayOutputStream
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// The capsule on screen (S06d A4), as iOS's CapsuleViews: the monthly offer under the capture well,
// and the map's picture the page shows, drawn at save time by the board itself (IdeaBoard, held still).

/** The offer's tags, as iOS's identifiers. */
object CapsuleTags {
    const val OFFER = "capsule-offer"
    const val OFFER_SAVE = "capsule-offer-save"
    const val OFFER_LATER = "capsule-offer-later"
}

/**
 * The quiet offer: the kit's banner well (WardBanner's inset, glyph and words) with its two actions
 * under the words, at the end, each a full target: Not now, then Save a capsule. Where the two don't
 * fit on one line (a large font on a narrow phone) Save a capsule goes under Not now, at the end, as
 * iOS's stack does (S06d review N12).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun CapsuleOfferCard(onSave: () -> Unit, onLater: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().wardInset(WardRadius.field, fill = WardControlStates.wellFill)
        .padding(start = WardSpace.m.wardDp, end = WardSpace.m.wardDp, top = WardSpace.m.wardDp, bottom = WardSpace.xs.wardDp)
        .semantics { testTag = CapsuleTags.OFFER }) {
        Row(horizontalArrangement = Arrangement.spacedBy(WardSpace.s.wardDp)) {
            Icon(Icons.Rounded.Inventory2, null, tint = WardTone.Blue.color(), modifier = Modifier.size(wardGlyph(18.0)))
            Text(stringResource(R.string.capsule_offer), Modifier.weight(1f), style = WardTextStyle.subheadline.textStyle(),
                color = wardColor(WardToken.ink))
        }
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(WardSpace.m.wardDp, Alignment.End)) {
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

/** The capsule's page for [book] as it is now, with the board's [picture] ([CapsuleBoardShot]) and the phone's time zone. */
fun capsuleHtml(book: IdeaBook, picture: Capsule.Picture?, now: Long = System.currentTimeMillis(),
                zone: ZoneId = ZoneId.systemDefault()): String =
    Capsule.html(book.notes, book.folders, book.allEdges(), now, zone, picture)

/** The page's picture at most this wide and tall, in CSS pixels; drawn at twice that. */
private const val PICTURE_MAX = 960.0
const val PICTURE_SCALE = 2f

/**
 * The picture's size in CSS pixels and the board held still to fill it: every card at its drawn
 * place (IdeaBoardSpread.places, the board's), at the least card the board spreads by ([cardWidth]
 * across), WardSpace.xl in from the edges, zoomed out (never in) to fit 960. Null with no ideas. As
 * iOS's CapsuleSave.fitting.
 */
class CapsuleFrame(val width: Int, val height: Int, val still: BoardStill)

fun capsuleFrame(book: IdeaBook, cardWidth: Double, straight: Boolean): CapsuleFrame? {
    val notes = book.notes
    if (notes.isEmpty()) return null
    val w = cardWidth; val h = IdeaBoardDrop.CARD_HEIGHT
    val drawn = IdeaBoardSpread.places(notes.map { IdeaBoardSpread.Card(it.id, BoardPoint(it.boardX, it.boardY), w, h) })
    fun at(id: String) = drawn[id] ?: notes.first { it.id == id }.let { BoardPoint(it.boardX, it.boardY) }
    val margin = WardSpace.xl
    val minX = notes.minOf { at(it.id).x } - w / 2 - margin; val maxX = notes.maxOf { at(it.id).x } + w / 2 + margin
    val minY = notes.minOf { at(it.id).y } - h / 2 - margin; val maxY = notes.maxOf { at(it.id).y } + h / 2 + margin
    val fit = min(1.0, min(PICTURE_MAX / (maxX - minX), PICTURE_MAX / (maxY - minY)))
    val cssW = max(1, ((maxX - minX) * fit).toInt()); val cssH = max(1, ((maxY - minY) * fit).toInt())
    // the board's origin is at the picture's middle; the pan brings the cards' box there
    return CapsuleFrame(cssW, cssH, BoardStill(fit, -(minX + maxX) / 2 * fit, -(minY + maxY) / 2 * fit,
        cssW.toDouble(), cssH.toDouble(), straight))
}

/**
 * The board as the person last left it, for the capsule's picture (S06d A4), drawn by the board itself:
 * [IdeaBoard] held still ([BoardStill]) in day's colours, laid out at the picture's size with two
 * pixels to a CSS pixel, and drawn into a bitmap instead of onto the screen, so it shows nothing and
 * is not read out. [onShot] has the PNG once the board has drawn, or null with no ideas. As iOS's
 * CapsuleSave.picture.
 */
@Composable fun CapsuleBoardShot(book: IdeaBook, settings: IdeasSettings, straight: Boolean, onShot: (Capsule.Picture?) -> Unit) {
    val shoot by rememberUpdatedState(onShot)
    val section = LocalWardLook.current.section
    CompositionLocalProvider(LocalDensity provides Density(PICTURE_SCALE, 1f), LocalWardDark provides false,
        LocalWardLook provides WardLook(dark = false, section = section), LocalWardGlide provides null,
        // no idea open beside it: no card edged as like one
        LocalIdeaShapes provides null) {
        val cardWidth = boardCardWidth()
        val frame = remember(book, cardWidth, straight) { capsuleFrame(book, cardWidth, straight) }
        if (frame == null) LaunchedEffect(Unit) { shoot(null) } else {
            val image = remember(frame) { ImageBitmap((frame.width * PICTURE_SCALE).toInt(), (frame.height * PICTURE_SCALE).toInt()) }
            // the board's first drawing into the picture
            val drawn = remember(frame) { CompletableDeferred<Unit>() }
            val page = wardColor(WardToken.background)
            // at the picture's size (a node of no size is not drawn), but off the window's edge, where
            // no finger reaches it, and with nothing for TalkBack
            Box(Modifier.offset { IntOffset(-(image.width + 1), 0) }.clearAndSetSemantics { }) {
                Box(Modifier.requiredSize(frame.width.dp, frame.height.dp)
                    .drawWithContent {
                        // into the picture, not onto the screen
                        val board = this
                        draw(board, layoutDirection, Canvas(image), size) {
                            drawRect(page)
                            board.drawContent()
                        }
                        drawn.complete(Unit)
                    }) {
                    IdeaBoard(book, settings, hidden = 0.dp, open = {}, still = frame.still)
                }
            }
            LaunchedEffect(drawn) {
                drawn.await()
                val png = withContext(Dispatchers.Default) {
                    ByteArrayOutputStream().also { image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
                }
                shoot(Capsule.Picture(png, frame.width, frame.height))
            }
        }
    }
}
