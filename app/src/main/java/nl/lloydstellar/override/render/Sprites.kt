package nl.lloydstellar.override.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import nl.lloydstellar.override.data.Corrupt
import nl.lloydstellar.override.data.Icon
import nl.lloydstellar.override.data.signal
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The tile artwork of override.js (bakeTile, drawIcon, bakeCorrupt,
 * glowSprite), baked once per tile size at the screen's own resolution.
 * Everything is drawn in the browser's 72 x 72 cell space and scaled, so
 * the numbers below are the browser's numbers.
 */
class Sprites(private val mono: Typeface, private val display: Typeface) {
    /* keyed by size and value; value 0 is the Corrupted Node */
    private val tiles = HashMap<Long, Bitmap>()
    private val glows = HashMap<Int, Bitmap>()

    /** A tile sprite at [size] device pixels. */
    @Synchronized
    fun tile(value: Int, size: Int): Bitmap = tiles.getOrPut(size.toLong() shl 32 or value.toLong()) { bakeTile(value, size) }

    @Synchronized
    fun corrupt(size: Int): Bitmap = tiles.getOrPut(size.toLong() shl 32) { bakeCorrupt(size) }

    /** A soft radial glow in [color], 64 px, drawn scaled. */
    @Synchronized
    fun glow(color: Int): Bitmap = glows.getOrPut(color) {
        val s = 64
        val b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = RadialGradient(s / 2f, s / 2f, s / 2f,
            intArrayOf(withAlpha(color, 0.55f), withAlpha(color, 0.55f), withAlpha(color, 0.18f), withAlpha(color, 0f)),
            floatArrayOf(0f, 0.18f, 0.59f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, s.toFloat(), s.toFloat(), p)
        b
    }

    /* ---------------- baking ---------------- */

    private fun canvas(size: Int): Pair<Bitmap, Canvas> {
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.scale(size / CELL, size / CELL)
        return b to c
    }

    private fun bakeTile(value: Int, size: Int): Bitmap {
        val d = signal(value)
        val (bmp, g) = canvas(size)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val body = RectF(0f, 0f, CELL, CELL)
        p.shader = LinearGradient(0f, 0f, CELL, CELL, d.col2.toInt(), d.col.toInt(), Shader.TileMode.CLAMP)
        g.drawRoundRect(body, R, R, p)
        p.shader = null

        /* printed circuit texture */
        g.save()
        g.clipPath(Path().apply { addRoundRect(body, R, R, Path.Direction.CW) })
        val rnd = seededRandom(value * 7919 + 13)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 1.2f; strokeCap = Paint.Cap.ROUND
            color = withAlpha(d.text.toInt(), 0.13f)
        }
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(d.text.toInt(), 0.2f) }
        repeat(9) {
            val x0 = rnd() * CELL
            val y0 = CELL * 0.35f + rnd() * CELL * 0.4f
            val len = 14f + rnd() * 30f
            val horiz = rnd() < 0.5
            val path = Path().apply { moveTo(x0, y0) }
            if (horiz) { path.lineTo(x0 + len, y0); path.lineTo(x0 + len, y0 + if (rnd() < 0.5) -8f else 8f) }
            else { path.lineTo(x0, y0 + len); path.lineTo(x0 + if (rnd() < 0.5) -8f else 8f, y0 + len) }
            g.drawPath(path, stroke)
            g.drawCircle(x0, y0, 1.6f, dot)
        }
        g.restore()

        /* bevel */
        val bevel = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f }
        bevel.color = Color.argb(71, 255, 255, 255)
        g.drawLine(R, 1.2f, CELL - R, 1.2f, bevel)
        g.drawLine(1.2f, R, 1.2f, CELL - R, bevel)
        bevel.color = Color.argb(97, 0, 0, 0)
        g.drawLine(R, CELL - 1.2f, CELL - R, CELL - 1.2f, bevel)
        g.drawLine(CELL - 1.2f, R, CELL - 1.2f, CELL - R, bevel)
        bevel.color = Color.argb(128, 0, 0, 0); bevel.strokeWidth = 1f
        g.drawRoundRect(RectF(0.5f, 0.5f, CELL - 0.5f, CELL - 0.5f), R, R, bevel)

        /* label strip */
        p.color = Color.argb(82, 0, 0, 0)
        g.drawRect(0f, CELL - 15f, CELL, CELL, p)
        val t = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = mono; isFakeBoldText = true; textSize = 6.4f; textAlign = Paint.Align.CENTER; color = d.text.toInt()
        }
        fitText(t, d.name, CELL - 6f)
        drawMiddle(g, d.name, CELL / 2, CELL - 7.5f, t)

        /* value */
        val txt = value.toString()
        val fs = when { txt.length <= 2 -> 26f; txt.length == 3 -> 22f; txt.length == 4 -> 17f; else -> 14f }
        t.typeface = display; t.isFakeBoldText = false; t.fontVariationSettings = "'wght' 700"; t.textSize = fs
        t.setShadowLayer(4f, 0f, 0f, if (value >= 2048) Color.WHITE else Color.argb(89, 0, 0, 0))
        drawMiddle(g, txt, CELL / 2, CELL * 0.47f, t)
        t.clearShadowLayer()

        /* icon, top left */
        g.save()
        g.translate(7f, 6f)
        val ic = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = d.text.toInt(); alpha = 230; strokeWidth = 1.2f
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        drawIcon(g, d.icon, ic)
        g.restore()
        return bmp
    }

    private fun bakeCorrupt(size: Int): Bitmap {
        val (bmp, g) = canvas(size)
        val body = RectF(0f, 0f, CELL, CELL)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, CELL, CELL, Corrupt.COL2.toInt(), Corrupt.COL.toInt(), Shader.TileMode.CLAMP)
        g.drawRoundRect(body, R, R, p)
        p.shader = null
        /* rogue code */
        g.save()
        g.clipPath(Path().apply { addRoundRect(body, R, R, Path.Direction.CW) })
        val rnd = seededRandom(66)
        val t = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = mono; textSize = 5.5f; color = Color.argb(71, 255, 120, 130) }
        val hex = "0123456789ABCDEF"
        for (row in 0 until 7) {
            val line = buildString { repeat(11) { append(hex[(rnd() * 16).toInt()]) } }
            g.drawText(line, 3f + (row % 2) * 3f, 3f + row * 8.6f - t.ascent(), t)
        }
        g.restore()
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f; color = Color.argb(230, 255, 77, 109) }
        g.drawRoundRect(RectF(1f, 1f, CELL - 1f, CELL - 1f), R, R, edge)
        p.color = Color.argb(107, 0, 0, 0)
        g.drawRect(0f, CELL - 15f, CELL, CELL, p)
        t.color = Corrupt.TEXT.toInt(); t.textSize = 6.4f; t.isFakeBoldText = true; t.textAlign = Paint.Align.CENTER
        fitText(t, Corrupt.NAME, CELL - 6f)
        drawMiddle(g, Corrupt.NAME, CELL / 2, CELL - 7.5f, t)
        /* the AI's eye */
        edge.color = 0xFFFF8A9A.toInt(); edge.strokeWidth = 1.4f
        g.drawOval(RectF(CELL * 0.26f - 8f, CELL * 0.2f - 4.5f, CELL * 0.26f + 8f, CELL * 0.2f + 4.5f), edge)
        p.color = 0xFFFF2A44.toInt()
        g.drawCircle(CELL * 0.26f, CELL * 0.2f, 2.6f, p)
        return bmp
    }

    /* icons are drawn in a 22 x 14 box */
    private fun drawIcon(g: Canvas, kind: Icon, p: Paint) {
        val st = Paint(p).apply { style = Paint.Style.STROKE }
        val fi = Paint(p).apply { style = Paint.Style.FILL }
        fun poly(vararg xy: Float, close: Boolean = false, fill: Boolean = false) {
            val path = Path()
            path.moveTo(xy[0], xy[1])
            var i = 2
            while (i < xy.size) { path.lineTo(xy[i], xy[i + 1]); i += 2 }
            if (close) path.close()
            g.drawPath(path, if (fill) fi else st)
        }
        when (kind) {
            Icon.SAW -> poly(0f, 11f, 5f, 3f, 5f, 11f, 10f, 3f, 10f, 11f, 15f, 3f, 15f, 11f, 20f, 3f)
            Icon.LED -> {
                g.drawCircle(7f, 7f, 4.5f, st)
                poly(4f, 11.5f, 4f, 14f); poly(10f, 11.5f, 10f, 14f)
                fi.alpha = 89; g.drawCircle(7f, 7f, 2.6f, fi); fi.alpha = 230
                poly(13f, 7f, 21f, 7f); poly(17f, 3f, 17f, 11f)
            }
            Icon.JACKS -> for (ox in floatArrayOf(0f, 11f)) {
                g.drawRect(ox + 1f, 6f, ox + 8f, 12f, st)
                g.drawRect(ox + 3.5f, 1f, ox + 5.5f, 6f, fi)
                g.drawCircle(ox + 4.5f, 9f, 1.3f, fi)
            }
            Icon.METER -> {
                g.drawArc(RectF(1f, 3f, 19f, 21f), 180f * 1.08f, 180f * 0.84f, false, st)
                for (i in 0..4) {
                    val a = PI * (1.08 + 0.84 * i / 4)
                    poly(10f + cos(a).toFloat() * 7f, 12f + sin(a).toFloat() * 7f, 10f + cos(a).toFloat() * 9f, 12f + sin(a).toFloat() * 9f)
                }
                poly(10f, 12f, 14.5f, 4.5f)
                g.drawCircle(10f, 12f, 1.4f, fi)
            }
            Icon.PCB -> {
                poly(1f, 2f, 8f, 2f, 8f, 8f, 14f, 8f, 14f, 13f)
                poly(1f, 12f, 5f, 12f, 5f, 6f, 11f, 6f, 11f, 1f, 20f, 1f)
                poly(17f, 5f, 17f, 12f, 21f, 12f)
                listOf(1f to 2f, 1f to 12f, 14f to 13f, 20f to 1f, 17f to 5f, 21f to 12f).forEach { (x, y) -> g.drawCircle(x, y, 1.4f, fi) }
            }
            Icon.HV -> {
                poly(10f, 0.5f, 19.5f, 13.5f, 0.5f, 13.5f, close = true)
                poly(11f, 3.5f, 8f, 8f, 10.5f, 8f, 8.5f, 12.5f, 13f, 7f, 10.5f, 7f, 12.5f, 3.5f, close = true, fill = true)
            }
            Icon.PORT -> {
                g.drawRect(1f, 1f, 10f, 13f, st)
                poly(10f, 1f, 15f, 3.5f, 15f, 14.5f, 10f, 13f, close = true)
                poly(16f, 7f, 21.5f, 7f); poly(19f, 4.5f, 21.5f, 7f, 19f, 9.5f)
                g.drawCircle(13f, 8f, 0.9f, fi)
            }
            Icon.JAM -> {
                val w = Path().apply { moveTo(0f, 7f); quadTo(2.5f, 0f, 5f, 7f); quadTo(7.5f, 14f, 10f, 7f) }
                g.drawPath(w, st)
                poly(10f, 7f, 11.5f, 1f, 12.5f, 13f, 13.5f, 3f, 14.5f, 11f, 15.5f, 5f, 16.5f, 9f, 18f, 7f, 22f, 7f)
                poly(3f, 1f, 21f, 13f)
            }
            Icon.TUBE -> {
                g.drawRoundRect(RectF(6f, 0.5f, 16f, 11.5f), 4f, 4f, st)
                poly(6f, 11.5f, 16f, 11.5f)
                for (x in floatArrayOf(8f, 11f, 14f)) poly(x, 11.5f, x, 14f)
                poly(9f, 9f, 9.5f, 4f, 11f, 7f, 12.5f, 4f, 13f, 9f)
                fi.alpha = 77; g.drawCircle(11f, 6f, 3.5f, fi); fi.alpha = 230
            }
            Icon.RADAR -> {
                g.drawCircle(8f, 7f, 6.5f, st)
                g.drawCircle(8f, 7f, 3.5f, st)
                poly(1.5f, 7f, 14.5f, 7f); poly(8f, 0.5f, 8f, 13.5f)
                poly(16f, 11f, 21f, 11f); poly(16f, 8f, 21f, 8f); poly(16f, 5f, 19f, 5f)
            }
            Icon.BOLT -> {
                poly(9f, 0f, 3f, 8f, 8f, 8f, 5f, 14f, 13f, 5f, 8.5f, 5f, 11f, 0f, close = true, fill = true)
                poly(15f, 2f, 13f, 6f, 16f, 6f, 14f, 10f)
                poly(19f, 5f, 17.5f, 8f, 20f, 8f, 18.5f, 11f)
            }
        }
    }

    companion object {
        const val CELL = 72f
        private const val R = 10f

        /** The Park-Miller generator the browser uses for the tile textures. */
        fun seededRandom(seed: Int): () -> Float {
            var s = (seed % 2147483647).toLong()
            if (s <= 0) s += 2147483646
            return {
                s = s * 16807 % 2147483647
                ((s - 1) / 2147483646.0).toFloat()
            }
        }

        fun withAlpha(color: Int, a: Float): Int = (color and 0x00FFFFFF) or ((a.coerceIn(0f, 1f) * 255).toInt() shl 24)

        /** Draws text centred vertically on [y], like textBaseline = 'middle'. */
        fun drawMiddle(c: Canvas, s: String, x: Float, y: Float, p: Paint) {
            val fm = p.fontMetrics
            c.drawText(s, x, y - (fm.ascent + fm.descent) / 2, p)
        }

        /** Shrinks [p] until [s] fits in [w]. */
        fun fitText(p: Paint, s: String, w: Float) {
            val m = p.measureText(s)
            if (m > w) p.textSize *= w / m
        }
    }
}
