package com.cramdown.sorta.ui.ward

// A port of WardSection from ios/RedPen/Shared/Ward/WardLook.swift.
//
// Stethoscore gave each part of the app a colour of its own. Sorta has one
// look, `sorta`, which is also the look outside any section: its edges and
// icons (`ink`) are the icon's coral (WardToken.accent), its words and glyphs
// (`accent`) are primaryInk, and its fills under words (`fill`) are primary.
// The base, the cards, the body text and danger stay the Ward palette's.

/** One section's colours, light and dark (WardSection's table). */
data class WardSectionColours(
    val hue: Double,
    val ink: WardPair,
    val accent: WardPair,
    val fill: WardPair,
)

@Suppress("EnumEntryName")
enum class WardSection {
    sorta;

    private val colours: WardSectionColours get() = table.getValue(this)

    /** The hue, in OKLCH degrees. */
    val hue: Double get() = colours.hue

    /** The controls' edges and the icons: 3:1 on the base and a card at a control's edge strength. */
    val ink: WardPair get() = colours.ink

    /** Words and glyphs in the section's colour: 4.5:1 on every tier of its mode. */
    val accent: WardPair get() = colours.accent

    /** A fill under words: onPrimary reads 4.5:1 on it. */
    val fill: WardPair get() = colours.fill

    fun accent(dark: Boolean): Int = accent.of(dark)
    fun fill(dark: Boolean): Int = fill.of(dark)
    fun ink(dark: Boolean): Int = ink.of(dark)

    /** The ground behind every page: the plain base (WardPalette background). */
    val wash: WardPair get() = WardPalette.table.getValue(WardToken.background)

    fun wash(dark: Boolean): Int = wash.of(dark)

    companion object {
        /** The look outside any section: Sorta's own, whose fill and accent are the palette's primary and primaryInk. */
        val outside: WardSection = sorta

        /** The section the app opens on. The window's starting background and the first frame stand on its ground. */
        val opening: WardSection = sorta

        /** The colours that mean something, which no section's accent may look like. */
        val semanticTokens: List<WardToken> = listOf(WardToken.danger)

        // (ink, accent, fill) from the palette: the icon's coral, primaryInk and primary.
        // The hue sits between the corals' own (32.6 to 34.5 degrees in OKLCH).
        private val table: Map<WardSection, WardSectionColours> by lazy {
            mapOf(
                sorta to WardSectionColours(33.6, WardPalette.table.getValue(WardToken.accent),
                    WardPalette.table.getValue(WardToken.primaryInk), WardPalette.table.getValue(WardToken.primary)),
            )
        }
    }
}

/** The wash behind a highlighted word: the section's fill at this strength, under ink words. */
object WardWash {
    const val alpha = 0.22
}
