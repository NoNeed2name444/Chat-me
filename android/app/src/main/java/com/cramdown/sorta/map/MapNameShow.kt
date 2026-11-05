package com.cramdown.sorta.map

/**
 * A body on screen as the names see it (S05f): its index in the scene, its centre in the view's
 * pixels, its drawn size across ([pixels], as ScreenNode reports it: what the tap's pick reads),
 * its pick [radius] in pixels (the body's own radius, which a name clears), and whether it is in
 * the Space depth fade's crisp zone.
 */
data class NameBody(val index: Int, val x: Float, val y: Float, val pixels: Float, val crisp: Boolean = true, val radius: Float = pixels / 2f)

/**
 * The words' tight box at the full size ([MapNameShow.WORDS_DP] tall), in pixels from the start
 * of the line on its baseline, as Paint.getTextBounds gives it: [left] to [right] across, [top]
 * (above the baseline, negative) to [bottom] (below it).
 */
data class NameText(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/**
 * A name on show: its body's [index] and centre, the box its rim covers on screen, its pill's
 * box inside that, where its words start and their baseline, and how far it has popped in
 * ([scale], 0.85 to 1), all in the view's pixels.
 */
data class NamePill(
    val index: Int, val bodyX: Float, val bodyY: Float,
    val left: Float, val top: Float, val right: Float, val bottom: Float,
    val pillLeft: Float, val pillTop: Float, val pillRight: Float, val pillBottom: Float,
    val textX: Float, val baseline: Float, val textSize: Float, val scale: Float,
)

/**
 * Which bodies show a name in Space and Neurons, and where, as iOS's (GraphSim.updateLabels,
 * wantLabel, placeLabel; GraphLabelNode's pill): only the hovered body (a pointer with no button
 * down), the pressed one (a finger down on it), the held one and the chosen one while its card is
 * up. A name pops in from 85 % to full size over 0.12 s, easing out, at once with animations off,
 * and goes the instant it is not wanted. It sits above its body on screen, whichever way the camera
 * is turned, at 1.25 times the body's pick radius plus 16 dp, its words always 21 dp tall, on a
 * round-ended pill. The style of its words and pill is LabelChooser's (LabelContrast.kt).
 */
object MapNameShow {
    /** How long a name takes to pop in, in seconds (labelPopTime), and the size it starts at. */
    const val POP_SECONDS = 0.12
    const val POP_FROM = 0.85f
    /** How tall a name's words are on screen, in dp (labelPoints), whatever the zoom. */
    const val WORDS_DP = 21f
    /** How far above the body a name sits: [CLEAR] of its pick radius, and [GAP_DP] more (labelClear, labelGap). */
    const val CLEAR = 1.25f
    const val GAP_DP = 16f
    /** The pill's room round the words, across and up and down, and the rim's, in words' heights (GraphLabelNode.fill). */
    const val PAD_X = 0.22f
    const val PAD_Y = 0.10f
    const val RIM = 0.12f
    /** How far from a body a finger or pointer still picks it, in dp: the tap's reach. */
    const val PICK_DP = 40f
    /** A title longer than this shows its first [LONG] − 1 characters and "…" (GraphSceneBuilder.label). */
    const val LONG = 28

    /**
     * The bodies that want a name, in iOS's order (hovered, pressed, held, chosen), each once: only
     * a body on screen ([bodies]), and only in the crisp zone unless it is the chosen one.
     */
    fun wanted(hovered: Int?, pressed: Int?, held: Int?, chosen: Int?, bodies: Map<Int, NameBody>): List<Int> {
        val out = ArrayList<Int>(4)
        for (i in listOf(hovered, pressed, held, chosen)) {
            if (i == null || i in out) continue
            val body = bodies[i] ?: continue
            if (!body.crisp && i != chosen) continue
            out.add(i)
        }
        return out
    }

    /** The body a touch or pointer at ([x], [y]) picks: the nearest drawn bigger than a speck, within [within] pixels. */
    fun pick(bodies: List<NameBody>, x: Float, y: Float, within: Float): NameBody? {
        var best: NameBody? = null
        var bestD = Float.MAX_VALUE
        for (b in bodies) {
            if (b.pixels <= 2f) continue
            val dx = b.x - x; val dy = b.y - y
            val d = kotlin.math.sqrt(dx * dx + dy * dy)
            if (d < bestD) { bestD = d; best = b }
        }
        return if (best != null && bestD < within) best else null
    }

    /** How far a name has popped in, [seconds] after it went up: 0.85 to 1, easing out; 1 at once when not [lively]. */
    fun growth(seconds: Double, lively: Boolean): Float {
        if (!lively) return 1f
        val t = (seconds / POP_SECONDS).coerceIn(0.0, 1.0)
        val left = 1 - t
        return (POP_FROM + (1 - POP_FROM) * (1 - left * left)).toFloat()
    }

    /** The words a name shows: a title past [LONG] characters cut to its first 27 and "…"; a folder's label comes already cut ([UniverseTree.folderLabel]). */
    fun cut(title: String, folder: Boolean = false): String =
        if (!folder && title.length > LONG) title.take(LONG - 1) + "…" else title

    /**
     * Where [body]'s name goes: its words' foot (the bottom of [text]'s box) at the body's centre less
     * [CLEAR] of its pick radius and [GAP_DP] (in pixels at [density] a dp), the pill round the words
     * with its padding, the rim round the pill, all grown to [scale] from that foot, as iOS's label
     * node grows from its origin.
     */
    fun place(body: NameBody, text: NameText, density: Float, scale: Float): NamePill {
        val h = WORDS_DP * density
        val footX = body.x
        val footY = body.y - (body.radius * CLEAR + GAP_DP * density)
        val pillW = text.width + 2 * PAD_X * h
        val pillH = text.height + 2 * PAD_Y * h
        val edge = RIM * h
        // the pill's middle is half the words' height above the foot
        val midY = footY - text.height / 2f * scale
        val halfW = pillW / 2f * scale; val halfH = pillH / 2f * scale
        val rimW = (pillW + edge) / 2f * scale; val rimH = (pillH + edge) / 2f * scale
        return NamePill(
            index = body.index, bodyX = body.x, bodyY = body.y,
            left = footX - rimW, top = midY - rimH, right = footX + rimW, bottom = midY + rimH,
            pillLeft = footX - halfW, pillTop = midY - halfH, pillRight = footX + halfW, pillBottom = midY + halfH,
            // the words' box centred across the foot, its bottom on it
            textX = footX - (text.left + text.width / 2f) * scale, baseline = footY - text.bottom * scale,
            textSize = h * scale, scale = scale,
        )
    }

    /**
     * The pressed body (S05f): the one under a finger that went down on it and has not lifted, moved
     * past the touch slop, become a two-finger gesture or been cancelled. The map's pointer loop tells
     * it each of these as it reads them, and consumes nothing for it.
     */
    class Press(private val slop: Float) {
        var pressed: Int? = null
            private set
        private var moved = 0f

        /** A finger down on [picked] (null: on no body). */
        fun down(picked: Int?) { pressed = picked; moved = 0f }

        /** The finger moved [distance] pixels. */
        fun move(distance: Float) { moved += distance; if (moved > slop) pressed = null }

        /** How many fingers are down now: two or more is a pinch or a slide, not a press. */
        fun fingers(count: Int) { if (count >= 2) pressed = null }

        fun up() { pressed = null }

        fun cancel() { pressed = null }
    }

    /** What a pointer event does to the hovered body (S05f). */
    enum class HoverEvent { ENTER, MOVE, EXIT }

    /**
     * The hovered body after a pointer event: a pointer with no button down picks the body under it
     * ([picked]) as it comes in or moves; leaving the map lets it go; with a button down the hovered
     * body stays as it was (that is a press, which [Press] reads).
     */
    fun hover(current: Int?, event: HoverEvent, buttonDown: Boolean, picked: Int?): Int? = when {
        event == HoverEvent.EXIT -> null
        buttonDown -> current
        else -> picked
    }

    /**
     * The names on show over time, one board a scene: when each went up (its pop) and its style's
     * tracker (GraphSim.updateLabels, styleLabels). Times are in seconds on one clock.
     */
    class Board {
        private val up = LinkedHashMap<Int, Double>()
        private val trackers = HashMap<Int, LabelTracker>()
        private var sinceMeasure = 0.0
        private var last = Double.NaN

        /** The bodies whose names are up, in the order they were wanted. */
        val shown: List<Int> get() = up.keys.toList()

        /** The names wanted at [now]: one newly wanted goes up now; one no longer wanted goes at once, with its style. */
        fun keep(wanted: List<Int>, now: Double) {
            up.keys.retainAll(wanted.toSet())
            trackers.keys.retainAll(wanted.toSet())
            for (i in wanted) if (i !in up) up[i] = now
        }

        /** How far body [i]'s name has popped in at [now]; 0.85 for one not up yet, when [lively]. */
        fun scale(i: Int, now: Double, lively: Boolean): Float = growth(now - (up[i] ?: now), lively)

        /** Body [i]'s style as drawn now; the first time, chosen from [measure] (its backdrop and its body's tint), as iOS's fresh name is. */
        fun look(i: Int, settings: LabelSettings, measure: (Int) -> Pair<LabelBackdrop, LabelRgb>?): LabelLook? {
            trackers[i]?.let { return it.shown }
            val (back, tint) = measure(i) ?: return null
            return LabelTracker(LabelChooser.choose(back, tint, settings)).also { trackers[i] = it }.shown
        }

        /**
         * Moves the styles on to [now]: every [interval] seconds, and at once for a name with none yet,
         * measures what is behind the first [LabelSampler.MOST] names ([measure]; null for a body not on
         * screen) and eases each toward its style, at once when not [lively]. Whether a name is still
         * popping in or its style easing, so the next frame should be drawn.
         */
        fun step(now: Double, lively: Boolean, settings: LabelSettings, interval: Double, measure: (Int) -> Pair<LabelBackdrop, LabelRgb>?): Boolean {
            val dt = if (last.isNaN()) 0.0 else kotlin.math.max(now - last, 0.0)
            last = now
            sinceMeasure += dt
            val due = sinceMeasure >= interval
            if (due) sinceMeasure = 0.0
            for (i in up.keys.take(LabelSampler.MOST)) {
                val tracker = trackers[i]
                if (tracker == null) { look(i, settings, measure); continue }
                if (due) measure(i)?.let { (back, tint) -> tracker.measure(back, tint, settings, interval) }
                tracker.step(dt, instant = !lively)
            }
            return trackers.values.any { it.easing } || (lively && up.values.any { now - it < POP_SECONDS })
        }
    }
}
