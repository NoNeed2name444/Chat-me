package com.cramdown.sorta.map

/**
 * The window's refresh vote (perf, 15 Nov: the owner's "make it 120 fps"): the display's fastest
 * seamless mode while anything on screen moves, and none once all is still, so the system lowers
 * an idle screen's rate (RefreshRate.moving says what moves). The vote is reckoned again only as
 * moving starts or stops, as a finger goes down or up, or as a map's frame loop starts or stops,
 * so the window is written about twice a gesture, never on every touch. Main thread only; times
 * are uptime milliseconds. [apply] writes the window's preferred mode (0: no preference); [later]
 * asks for [check] at a time, once the last touch's linger runs out.
 */
class MotionVote(
    private val fastest: () -> DisplayModeInfo?,
    private val apply: (Int) -> Unit,
    private val later: (atMs: Long) -> Unit,
) {
    private var loops = 0
    private var down = false
    private var touchedAt = -1L
    var moving = false; private set
    var voted = 0; private set

    /** A touch: [down] while a finger stays down, false as the last lifts or for a wheel's turn. */
    fun touch(down: Boolean, nowMs: Long) {
        val was = this.down
        this.down = down
        touchedAt = nowMs
        if (!moving || was != down) update(nowMs)
    }

    /** A map's frame loop started or stopped. */
    fun loop(running: Boolean, nowMs: Long) {
        loops = maxOf(0, loops + if (running) 1 else -1)
        update(nowMs)
    }

    fun check(nowMs: Long) = update(nowMs)

    private fun update(nowMs: Long) {
        moving = RefreshRate.moving(loops, down, if (touchedAt < 0) Long.MAX_VALUE else nowMs - touchedAt)
        // the mode is read as moving starts, and kept while it lasts
        val want = if (!moving) 0 else if (voted != 0) voted else RefreshRate.windowVote(true, fastest())
        if (want != voted) { voted = want; apply(want) }
        if (moving && loops == 0 && !down) later(touchedAt + RefreshRate.LINGER_MS)
    }
}
