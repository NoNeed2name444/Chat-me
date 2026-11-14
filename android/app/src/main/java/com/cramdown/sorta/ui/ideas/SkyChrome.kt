package com.cramdown.sorta.ui.ideas

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness1
import androidx.compose.material.icons.outlined.Brightness2
import androidx.compose.material.icons.outlined.Brightness3
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.NightlightRound
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.cramdown.sorta.R
import com.cramdown.sorta.ideas.IdeaBook
import com.cramdown.sorta.map.FolderWords
import com.cramdown.sorta.map.Leaning
import com.cramdown.sorta.map.MoonDays
import com.cramdown.sorta.map.SharedFrom
import com.cramdown.sorta.map.SkySpace
import com.cramdown.sorta.map.UniversePlan
import com.cramdown.sorta.ui.sources.ReaderNavBar
import com.cramdown.sorta.ui.ward.*
import com.cramdown.sorta.ui.wardColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// S06b around Map mode, as iOS's GraphSkyMap.swift and IdeasSkyChrome.swift: the peek card's lean
// line and File there, a shared idea's source mark, the strip's Days chip and the moons row it opens,
// and the sheet a share opens. The rules are SkyIdeas.kt's; the words are strings.xml's (S06b).

/** The tags iOS's identifiers give the same controls. */
object SkyTags {
    const val LEAN = "graphPeekLean"
    const val FILE_THERE = "graphPeekFileThere"
    const val SHARED_FROM = "sharedFromMark"
    const val DAYS_CHIP = "map-days-chip"
    const val MOONS = "moons-row"
    const val MOONS_HEADING = "moons-heading"
    const val MOONS_CLEAR = "moons-clear"
    const val MOONS_DONE = "moons-done"
    /** A moon, [back] days before today (0 today). */
    fun moon(back: Int) = "moon-$back"
    const val SHARE_SHEET = "share-sheet"
    const val SHARE_SAVE = "share-save"
    const val SHARE_CANCEL = "share-cancel"
    /** The share sheet's word [k], in order. */
    fun shareWord(k: Int) = "share-word-$k"
}

/** What the words sheet says under its field as it is typed: what the words do, and what a save keeps. */
object SkyWords {
    /** [note] the words' line, [kept] "Saves: %1$s" as strings.xml has it. */
    fun folderWordsFootnote(text: String, note: String, kept: String): String {
        val words = FolderWords.parse(text)
        return if (words.isEmpty()) note else note + "\n" + kept.format(words.joinToString(", "))
    }
}

/** The peek card's lean: "Shares ‘garden’ with Raised beds". */
data class SkyLean(val word: String, val folder: String)

/** A loose idea's lean and the folder's id (for File there); null when it is filed or shares no folder's words. */
internal fun skyLean(book: IdeaBook, id: String): Pair<SkyLean, String>? {
    val note = book.notes.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: return null
    if (note.folderId != null) return null
    val folders = book.folders.filter { it.words.isNotEmpty() }.map { Leaning.Folder(it.id, it.name, it.words) }
    if (folders.isEmpty()) return null
    val folderOf = book.notes.associate { it.id to it.folderId }
    val links = HashMap<String, Int>()
    for ((a, b) in book.allEdges()) {
        val other = when (note.id) { a -> b; b -> a; else -> continue }
        folderOf[other]?.let { links.merge(it, 1, Int::plus) }
    }
    val lean = Leaning.choose(note.title, note.body, folders, links) ?: return null
    val folder = book.folders.firstOrNull { it.id == lean.folder } ?: return null
    return SkyLean(lean.word, folder.name) to folder.id
}

/** Space's Universe's leans, circles and tails for [plan], from the book as it is now (MapScene.sky). */
internal fun skySpace(plan: UniversePlan, book: IdeaBook, edges: List<Pair<String, String>>, now: Long = System.currentTimeMillis()): SkySpace =
    SkySpace.of(plan.bodies,
        book.notes.map { SkySpace.Idea(it.id, it.title, it.body, it.folderId, it.createdAt, it.sharedFrom) },
        book.folders.map { Leaning.Folder(it.id, it.name, it.words) }, edges, now)

/**
 * The lean line on a loose idea's peek card, with File there: the one thing a lean offers, a tap,
 * never a drag (S06-common: gestures). The button takes the next line when the two don't fit.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun LeanRow(lean: SkyLean, file: () -> Unit) {
    val line = stringResource(R.string.lean_shares, lean.word, lean.folder)
    val spoken = stringResource(R.string.lean_file_spoken, lean.folder)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(WardSpace.s.wardDp), verticalArrangement = Arrangement.spacedBy(WardSpace.s.wardDp),
        itemVerticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.testTag(SkyTags.LEAN).clearAndSetSemantics { contentDescription = line },
            horizontalArrangement = Arrangement.spacedBy(WardSpace.xs.wardDp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.NorthEast, null, tint = wardColor(WardToken.accent), modifier = Modifier.size(wardGlyph(15.0)))
            Text(line, style = WardTextStyle.footnote.textStyle(), color = wardColor(WardToken.inkSecondary))
        }
        WardButtonBox(file, Modifier.testTag(SkyTags.FILE_THERE).semantics { contentDescription = spoken },
            kind = WardButtonKind.quiet, fills = false) {
            Icon(Icons.Outlined.Folder, null, Modifier.size(wardGlyph(17.0)))
            Text(stringResource(R.string.lean_file), maxLines = 1)
        }
    }
}

/** Where a shared idea came from: a glyph and "From example.org", plain text, not a control (the editor, the peek card). */
@Composable fun SharedFromMark(mark: String, modifier: Modifier = Modifier) {
    val spoken = stringResource(R.string.shared_from_spoken, mark)
    Row(modifier.testTag(SkyTags.SHARED_FROM).clearAndSetSemantics { contentDescription = spoken },
        horizontalArrangement = Arrangement.spacedBy(WardSpace.xs.wardDp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Download, null, tint = wardColor(WardToken.accent), modifier = Modifier.size(wardGlyph(15.0)))
        Text(stringResource(R.string.shared_from, mark), style = WardTextStyle.footnote.textStyle(), color = wardColor(WardToken.inkSecondary),
            maxLines = 1)
    }
}

/** The strip's Days chip, beside the look chip: a moon and "Days", pressed in while the row is open. Space and 2D only. */
@Composable fun MapDaysChip(on: Boolean, toggle: () -> Unit) {
    val hint = stringResource(R.string.moons_chip_hint)
    WardChipButton(stringResource(R.string.moons_chip), toggle, on = on, role = Role.Button, icon = Icons.Outlined.NightlightRound,
        modifier = Modifier.testTag(SkyTags.DAYS_CHIP).semantics { onClick(hint) { toggle(); true } })
}

/** A day's name as a moon says it, in the phone's own order: "14 November" (iOS's day and wide month). */
fun moonDayName(day: LocalDate, locale: Locale = Locale.getDefault()): String =
    day.format(DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(locale, "dMMMM"), locale))

/** "14 November, 3 ideas"; a day with none, "14 November, no ideas". */
@Composable fun moonSpoken(day: MoonDays.Day): String {
    val name = moonDayName(day.date)
    return if (day.count == 0) stringResource(R.string.moons_moon_none, name)
        else pluralStringResource(R.plurals.moons_moon_spoken, day.count, name, day.count)
}

/** A phase's glyph: new, crescent, half, gibbous, full (Material's moons and circles; never drawn in code). */
fun moonGlyph(phase: Int): ImageVector = when (phase) {
    1 -> Icons.Outlined.Brightness3
    2 -> Icons.Outlined.Contrast
    3 -> Icons.Outlined.Brightness2
    4 -> Icons.Filled.Brightness1
    else -> Icons.Outlined.Circle
}

/**
 * Recent days as moons, newest first, as many as fit and the rest a scroll away (up to MoonDays.MOST):
 * each as full as that day was busy next to the busiest day shown. Picking one lights its ideas on the
 * map; picking it again, Clear or Done ends it. A raised card under the strip.
 */
@Composable fun MoonRow(days: List<MoonDays.Day>, lit: LocalDate?, pick: (LocalDate?) -> Unit, close: () -> Unit, modifier: Modifier = Modifier) {
    val litDay = days.firstOrNull { it.date == lit }
    val heading = if (litDay != null) moonSpoken(litDay) else stringResource(R.string.moons_pick)
    val title = stringResource(R.string.moons_title)
    val clearSpoken = stringResource(R.string.moons_clear_spoken)
    val doneWords = stringResource(R.string.ward_done)
    WardCardFace(modifier.widthIn(max = MapPanel.MAX_WIDTH.dp).fillMaxWidth().testTag(SkyTags.MOONS)
        .semantics { customActions = listOf(CustomAccessibilityAction(doneWords) { close(); true }) },
        lift = WardLift.high, spacing = WardSpace.s.wardDp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(WardSpace.s.wardDp)) {
            Text(heading, Modifier.weight(1f).testTag(SkyTags.MOONS_HEADING).semantics { this.heading() },
                style = WardTextStyle.subheadline.textStyle(), color = wardColor(WardToken.inkSecondary))
            if (litDay != null) WardButtonBox({ pick(null) }, Modifier.testTag(SkyTags.MOONS_CLEAR).semantics { contentDescription = clearSpoken },
                kind = WardButtonKind.quiet, fills = false) { Text(stringResource(R.string.moons_clear), maxLines = 1) }
            WardTextButton(doneWords, close, Modifier.testTag(SkyTags.MOONS_DONE), kind = WardButtonKind.compact, fills = false)
        }
        LazyRow(Modifier.intoMargin(WardSpace.s.wardDp).semantics { contentDescription = title },
            contentPadding = PaddingValues(horizontal = WardSpace.s.wardDp), horizontalArrangement = Arrangement.spacedBy(WardSpace.xs.wardDp)) {
            itemsIndexed(days, key = { _, day -> day.date.toEpochDay() }) { back, day -> Moon(day, back, on = day.date == lit) { pick(if (day.date == lit) null else day.date) } }
        }
    }
}

@Composable private fun Moon(day: MoonDays.Day, back: Int, on: Boolean, tap: () -> Unit) {
    val spoken = moonSpoken(day)
    val hint = stringResource(R.string.moons_moon_hint)
    WardTile(tap, Modifier.defaultMinSize(WardControlSize.target.wardDp, WardControlSize.target.wardDp).testTag(SkyTags.moon(back))
        .semantics { contentDescription = spoken; onClick(hint) { tap(); true } }, chosen = on, cornerRadius = WardRadius.icon) {
        Column(Modifier.padding(horizontal = WardSpace.s.wardDp, vertical = WardSpace.xs.wardDp).clearAndSetSemantics {},
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Icon(moonGlyph(day.phase), null, tint = wardColor(if (day.count == 0) WardToken.inkSecondary else WardToken.ink),
                modifier = Modifier.size(wardGlyph(20.0)))
            Text("${day.date.dayOfMonth}", style = WardTextStyle.caption.textStyle().copy(fontFeatureSettings = "tnum"),
                color = wardColor(if (on) WardToken.primaryInk else WardToken.inkSecondary))
        }
    }
}

/** Text shared to Sorta, waiting in the sheet: [mark] only the link's host, or the sending app's name (SharedFrom). */
data class ShareIntake(val text: String, val mark: String?) {
    companion object {
        fun of(text: String, app: String?): ShareIntake? =
            text.trim().takeIf { it.isNotEmpty() }?.let { ShareIntake(it.take(4_000), SharedFrom.mark(it, app)) }
    }
}

/**
 * What a share keeps: its words as chips, each kept until tapped, saved as a new idea in To sort with
 * where it came from ([save] gets the kept words). Nothing is sent anywhere; Cancel keeps nothing.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable fun ShareSheet(intake: ShareIntake, onDismiss: () -> Unit, save: (String) -> Unit) {
    val words = remember(intake) { SharedFrom.words(intake.text) }
    var kept by remember(intake) { mutableStateOf(words.indices.toSet()) }
    val keptWord = stringResource(R.string.share_word_kept)
    val outWord = stringResource(R.string.share_word_out)
    fun submit() {
        val text = SharedFrom.kept(words, kept)
        if (text.isNotEmpty()) { save(text); onDismiss() }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = wardColor(WardToken.background)) {
        Column(Modifier.fillMaxWidth().testTag(SkyTags.SHARE_SHEET)) {
            ReaderNavBar(stringResource(R.string.share_title),
                leading = { WardTextButton(stringResource(R.string.sheet_cancel), onDismiss, Modifier.testTag(SkyTags.SHARE_CANCEL),
                    kind = WardButtonKind.quiet, fills = false) },
                trailing = { WardTextButton(stringResource(R.string.sky_save), ::submit, Modifier.testTag(SkyTags.SHARE_SAVE),
                    kind = WardButtonKind.compact, fills = false, enabled = kept.isNotEmpty()) })
            Column(Modifier.align(Alignment.CenterHorizontally).widthIn(max = IdeasLayout.column.dp).fillMaxWidth()
                .padding(horizontal = WardSpace.gutter.wardDp, vertical = WardSpace.m.wardDp),
                verticalArrangement = Arrangement.spacedBy(WardSpace.m.wardDp)) {
                Text(stringResource(R.string.share_hint), style = WardTextStyle.subheadline.textStyle(), color = wardColor(WardToken.inkSecondary))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(WardSpace.s.wardDp), verticalArrangement = Arrangement.spacedBy(WardSpace.s.wardDp)) {
                    words.forEachIndexed { k, word ->
                        val on = k in kept
                        WardChipButton(word, { kept = if (on) kept - k else kept + k }, on = on,
                            modifier = Modifier.testTag(SkyTags.shareWord(k)).semantics { stateDescription = if (on) keptWord else outWord })
                    }
                }
                intake.mark?.let { SharedFromMark(it) }
            }
            Spacer(Modifier.height(WardSpace.xl.wardDp))
        }
    }
}
