package com.cramdown.sorta.ideas

/**
 * The map's four looks, in the look chip's menu order (S05b checklist 4): Space, Neuron, 2D and
 * Performance; a port of iOS's MapLook (ios/Sorta/Ideas/MapLook.swift). Three are the 3D map's
 * themes ([GraphTheme]); 2D is the flat board. The look is stored as two values, so the 3D map keeps
 * reading its theme where it always has: the theme under [GraphTheme.KEY], and whether the board is
 * shown under [BOARD_KEY]. Choosing 2D keeps the theme, so going back to 3D finds the one left.
 * Pure Kotlin, tested in MapLookTest.
 */
enum class MapLook(val rawValue: String, val identifier: String, val theme: GraphTheme?) {
    SPACE("space", "look-space", GraphTheme.SPACE),
    NEURON("neuron", "look-neuron", GraphTheme.NEURONS),
    // S11: a flat nested map, drawn on the page, kept under [FLAT_KEY]
    TREE("tree", "look-tree", null),
    TWO_D("twoD", "look-2d", null),
    PERFORMANCE("performance", "look-performance", GraphTheme.PERFORMANCE);

    companion object {
        /** Where the board's choice is kept, beside the theme's key. */
        const val BOARD_KEY = "vignette.ideas.mapBoard"
        /** The chip's identifier. */
        const val CHIP = "map-look"

        /** The look the two stored values make; an unknown theme is the standard one ([GraphTheme.stored]). */
        fun stored(board: Boolean, theme: String?, flat: String? = null): MapLook =
            if (board) TWO_D else flatOf(flat) ?: of(GraphTheme.stored(theme))

        /** S11: where a flat nested map (Tree) is kept; empty when the look is the board or a theme. */
        const val FLAT_KEY = "vignette.ideas.mapFlat"
        /** S11: the flat nested map [flat] names, or null for the board, a theme or nothing. */
        fun flatOf(flat: String?): MapLook? = entries.firstOrNull { it.theme == null && it != TWO_D && it.rawValue == flat }

        fun of(theme: GraphTheme): MapLook = entries.first { it.theme == theme }
    }
}
