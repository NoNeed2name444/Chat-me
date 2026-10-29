package com.cramdown.sorta.ui.ward

import org.junit.Test

// iOS's Reduce Motion on Android: with the system's Remove animations on (the
// animator duration scale at 0, read once by the theme: wardReduceMotion)
// every surface that moves is simply where it is going, as iOS's are. Read
// from the screens' sources (WardScreens, ui/** but not ui/map): each call
// that animates is decided by the flag on its own line or in its brackets.
class WardReduceMotionTest {

    @Test fun everySurfaceThatMovesHonoursRemoveAnimations() = checks {
        val moving = WardScreens.sites("animate[A-Za-z]*AsState|animateTo|animateContentSize|animateScrollTo|animateScrollToItem|" +
            "animateScrollBy|animateScrollToPage|rememberInfiniteTransition|AnimatedVisibility|AnimatedContent|Crossfade|updateTransition")
        // Sorta's kit has 7 (Stethoscore's whole app had 15 or more)
        check("found the surfaces that move", moving.size >= 7, "${moving.size}")
        val deaf = moving.filter { !(it.lead + it.args).contains("reduce", ignoreCase = true) }
        check("every one of them reads Remove animations where it moves", deaf.isEmpty(), deaf.joinToString { it.at })
        check("with animations off a held control does not sink (iOS CapPop: pressed && !reduceMotion)",
            (WardScreens.sources["ward/WardFaces.kt"] ?: error("ward/WardFaces.kt is missing")).contains("if (held && !reduce) WardPress.sinkScale"))
        check("the raised press face reads the same depth (wardDepth), so it keeps the flag too",
            (WardScreens.sources["ward/WardSurfaces.kt"] ?: error("ward/WardSurfaces.kt is missing")).contains("val depth by wardDepth(pressed)"))
    }
}
