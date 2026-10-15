package nl.lloydstellar.override.render

import android.graphics.RectF
import nl.lloydstellar.override.engine.Tile

/**
 * Where everything sits on the screen, in the browser's logical units (its
 * canvas is 400 wide). Portrait stacks the panels like override.html;
 * landscape (tablets, foldables, Chromebooks) puts the matrix on the left.
 */
class TerminalLayout(
    /** device px per logical unit, and the logical origin in px */
    val s: Float, val ox: Float, val oy: Float,
    /** the whole logical area */
    val w: Float, val h: Float,
    val header: RectF,
    val hud: RectF,
    val trace: RectF,
    val grid: RectF,
    val ladder: RectF,
    val vu: RectF,
    val log: RectF,
    val n: Int,
) {
    val gap = if (n >= 5) 7f else 9f
    val cell = (grid.width() - (n + 1) * gap) / n
    fun cellX(x: Float) = grid.left + gap + x * (cell + gap)
    fun cellY(y: Float) = grid.top + gap + y * (cell + gap)

    companion object {
        /** Lays the terminal out in a view of [width] x [height] px, keeping [top] and [bottom] px free. */
        fun compute(width: Int, height: Int, top: Float, bottom: Float, n: Int): TerminalLayout {
            val availH = (height - top - bottom).coerceAtLeast(100f)
            val landscape = width > availH * 1.08f
            return if (landscape) landscape(width, availH, top, n) else portrait(width, availH, top, n)
        }

        private fun portrait(width: Int, availH: Float, top: Float, n: Int): TerminalLayout {
            val w = 400f
            val gw = 376f
            /* header 18, hud 44, trace 22, grid, ladder 14, panel 124 and the minimum gaps */
            val need = 18f + 6f + 44f + 4f + 22f + 12f + gw + 10f + 14f + 10f + 124f
            var s = width / w
            if (availH / s < need) s = availH / need
            val h = availH / s
            val extra = h - need
            val ox = (width - w * s) / 2
            val oy = top
            var y = extra * 0.30f
            val header = RectF(12f, y, w - 12f, y + 18f); y += 18f + 6f
            val hud = RectF(12f, y, w - 12f, y + 44f); y += 44f + 4f
            val trace = RectF(12f, y, w - 12f, y + 22f); y += 22f + 12f + extra * 0.22f
            val grid = RectF(12f, y, 12f + gw, y + gw); y += gw + 10f + extra * 0.10f
            val ladder = RectF(12f, y, w - 12f, y + 14f); y += 14f + 10f + extra * 0.08f
            val vu = RectF(12f, y, 172f, y + 124f)
            val log = RectF(180f, y, w - 12f, y + 124f)
            return TerminalLayout(s, ox, oy, w, h, header, hud, trace, grid, ladder, vu, log, n)
        }

        private fun landscape(width: Int, availH: Float, top: Float, n: Int): TerminalLayout {
            val gw = 376f
            val colW = 360f
            val w = 12f + gw + 24f + colW + 12f
            val need = 12f + gw + 12f
            val s = minOf(width / w, availH / need)
            val h = availH / s
            val ox = (width - w * s) / 2
            val gy = (h - gw) / 2
            val grid = RectF(12f, gy, 12f + gw, gy + gw)
            val x0 = grid.right + 24f
            val x1 = x0 + colW
            /* the right column, centred on the matrix */
            val colH = 18f + 6f + 44f + 6f + 22f + 16f + 14f + 16f + 124f
            var y = gy + (gw - colH) / 2
            val header = RectF(x0, y, x1, y + 18f); y += 18f + 6f
            val hud = RectF(x0, y, x1, y + 44f); y += 44f + 6f
            val trace = RectF(x0, y, x1, y + 22f); y += 22f + 16f
            val ladder = RectF(x0, y, x1, y + 14f); y += 14f + 16f
            val vu = RectF(x0, y, x0 + 150f, y + 124f)
            val log = RectF(x0 + 158f, y, x1, y + 124f)
            return TerminalLayout(s, ox, top, w, h, header, hud, trace, grid, ladder, vu, log, n)
        }
    }
}

/* ---------------- what moves on the screen ---------------- */

class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val color: Int)
class Ring(val x: Float, val y: Float, var r: Float, var life: Float, val color: Int)
class Floater(val text: String, val color: Int, var y: Float, var life: Float, val big: Boolean, val dy: Float)

/** The needle and LED ladder of the VU meter, with the browser's spring. */
class Vu {
    var level = 0f
    var needle = 0f
    var vel = 0f
    var peak = -1
    var peakHold = 0
}

/** The slide of the last move. Times in ms (uptime). */
class SlideAnim {
    var start = 0L
    var ghosts: List<Tile> = emptyList()
    var active = false
    fun progress(now: Long): Float = if (!active) 1f else ((now - start) / SLIDE_MS).coerceIn(0f, 1f)

    companion object {
        const val SLIDE_MS = 110f
        const val POP_MS = 200f
        const val APPEAR_MS = 160f
    }
}
