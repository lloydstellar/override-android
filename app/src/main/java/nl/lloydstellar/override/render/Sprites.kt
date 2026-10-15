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
import nl.lloydstellar.override.engine.Kind
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

    /** The artwork of a special tile; [variant] 1 is a cracked case. Numbers and counters are drawn live on top. */
    @Synchronized
    fun special(kind: Kind, size: Int, variant: Int = 0): Bitmap =
        specials.getOrPut(size.toLong() shl 32 or (kind.ordinal * 4L + variant)) { bakeSpecial(kind, variant, size) }

    private val specials = HashMap<Long, Bitmap>()
    private val previews = HashMap<String, Bitmap>()

    /**
     * A still picture for the manual, with a sample number on it: [id] is a
     * [Kind] name, or LOCK or PIN for the two kinds of NEXUS-9 lock.
     */
    @Synchronized
    fun preview(id: String, size: Int): Bitmap = previews.getOrPut("$id/$size") { bakePreview(id, size) }

    private fun bakePreview(id: String, size: Int): Bitmap {
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val g = Canvas(b)
        val dst = android.graphics.Rect(0, 0, size, size)
        g.scale(size / CELL, size / CELL)
        val cell = RectF(0f, 0f, CELL, CELL)
        val big = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = display; fontVariationSettings = "'wght' 700"; textAlign = Paint.Align.CENTER }
        val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = mono; isFakeBoldText = true; textAlign = Paint.Align.CENTER }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val st = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        fun bmp(bm: Bitmap) { g.save(); g.scale(CELL / size, CELL / size); g.drawBitmap(bm, null, dst, null); g.restore() }
        fun counter(n: Int, edge: Int, text: Int) {
            fill.color = 0xFF14060A.toInt(); g.drawCircle(CELL - 13f, 13f, 9f, fill)
            st.color = edge; st.strokeWidth = 1.5f; g.drawCircle(CELL - 13f, 13f, 9f, st)
            small.color = text; small.textSize = 9f; drawMiddle(g, n.toString(), CELL - 13f, 13f, small)
        }
        when (id) {
            "UNKNOWN" -> {
                /* a block you have not met: no picture, no name */
                fill.color = 0xFF101614.toInt()
                g.drawRoundRect(cell, R, R, fill)
                st.color = Color.argb(110, 160, 180, 170); st.strokeWidth = 1.5f
                st.pathEffect = android.graphics.DashPathEffect(floatArrayOf(4f, 4f), 0f)
                g.drawRoundRect(RectF(1.5f, 1.5f, CELL - 1.5f, CELL - 1.5f), R, R, st)
                st.pathEffect = null
                big.color = Color.argb(150, 160, 180, 170); big.textSize = 30f
                g.drawText("?", CELL / 2, CELL / 2 + 11f, big)
            }
            "LOCK", "PIN" -> {
                if (id == "PIN") bmp(tile(128, size))
                fill.color = if (id == "PIN") Color.argb(70, 255, 40, 70) else Color.argb(56, 255, 40, 70)
                g.drawRoundRect(cell, 7f, 7f, fill)
                if (id == "LOCK") {
                    g.save(); g.clipPath(Path().apply { addRoundRect(cell, 7f, 7f, Path.Direction.CW) })
                    st.color = Color.argb(89, 255, 77, 109); st.strokeWidth = 3f
                    var k = -CELL
                    while (k < CELL * 2) { g.drawLine(k, CELL, k + CELL, 0f, st); k += 14f }
                    g.restore()
                }
                st.color = 0xFFFF4D6D.toInt(); st.strokeWidth = 2.5f
                g.drawRoundRect(RectF(1f, 1f, CELL - 1f, CELL - 1f), 7f, 7f, st)
                val (cx, cy, r) = if (id == "LOCK") Triple(CELL / 2, CELL / 2 + 2f, 1f) else Triple(CELL - 13f, 15f, 0.55f)
                if (id == "PIN") { fill.color = 0xFF2A0008.toInt(); g.drawCircle(cx, cy - 2f, 11f, fill) }
                fill.color = 0xFFFF4D6D.toInt()
                g.drawRoundRect(RectF(cx - 11 * r, cy - 4 * r, cx + 11 * r, cy + 13 * r), 3f * r, 3f * r, fill)
                st.color = 0xFFFF8A9A.toInt(); st.strokeWidth = 3.5f * r
                g.drawArc(RectF(cx - 7 * r, cy - 13 * r, cx + 7 * r, cy + 1 * r), 180f, 180f, false, st)
                fill.color = 0xFF2A0008.toInt()
                if (id == "PIN") g.drawRect(RectF(4f, CELL - 16f, CELL - 4f, CELL - 4f), fill)
                small.color = 0xFFFFD6D9.toInt(); small.textSize = 7.5f
                g.drawText(if (id == "PIN") "PINNED 5" else "NEXUS-9 LOCK 5", CELL / 2, CELL - 7f, small)
            }
            "NODE" -> {
                bmp(corrupt(size))
                big.color = 0xFFFFD6D9.toInt(); big.textSize = 24f; g.drawText("4", CELL * 0.62f, CELL * 0.58f + 8f, big)
            }
            "SIGNAL" -> {
                /* the supply drop: an ordinary signal with a crate badge */
                bmp(tile(32, size))
                fill.color = 0xFF0A2412.toInt(); g.drawCircle(CELL - 13f, 13f, 9f, fill)
                st.color = 0xFF4DFF9A.toInt(); st.strokeWidth = 1.4f
                g.drawRect(CELL - 18f, 9f, CELL - 8f, 18f, st); g.drawLine(CELL - 18f, 12.5f, CELL - 8f, 12.5f, st)
            }
            else -> {
                val kind = Kind.valueOf(id)
                val look = look(kind)
                if (kind == Kind.CASED) { bmp(tile(16, size)); bmp(special(Kind.CASED, size)) } else bmp(special(kind, size))
                big.color = look.text.toInt()
                when (kind) {
                    Kind.CAPACITOR -> {
                        st.color = withAlpha(look.edge.toInt(), 0.25f); st.strokeWidth = 3f
                        g.drawCircle(CELL / 2, CELL * 0.47f, 17f, st)
                        st.color = look.text.toInt()
                        g.drawArc(RectF(CELL / 2 - 17f, CELL * 0.47f - 17f, CELL / 2 + 17f, CELL * 0.47f + 17f), -90f, 240f, false, st)
                        big.textSize = 22f; g.drawText("4", CELL / 2, CELL * 0.47f + 8f, big)
                    }
                    Kind.BEACON -> { big.textSize = 24f; g.drawText("4", CELL / 2, CELL * 0.62f + 8f, big); counter(15, look.edge.toInt(), look.text.toInt()) }
                    Kind.WORM -> { big.textSize = 24f; g.drawText("2", CELL / 2, CELL * 0.62f + 8f, big); counter(8, look.edge.toInt(), look.text.toInt()) }
                    Kind.PATCH -> { big.color = 0xFFFFD27A.toInt(); big.textSize = 22f; g.drawText("x2", CELL / 2, CELL * 0.46f + 8f, big) }
                    Kind.WALL -> {
                        fill.color = look.text.toInt()
                        for (i in 0 until 2) { val bx = CELL / 2 + (i - 0.5f) * 12f; g.drawRect(bx - 4.5f, CELL * 0.42f, bx + 4.5f, CELL * 0.58f, fill) }
                    }
                    else -> {}
                }
            }
        }
        return b
    }

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

    /* ---------------- the app's special tiles ---------------- */

    /** Colours of a special tile: body gradient, edge, label text. */
    class Look(val col2: Long, val col: Long, val edge: Long, val text: Long, val name: String)

    private fun bakeSpecial(kind: Kind, variant: Int, size: Int): Bitmap {
        val (bmp, g) = canvas(size)
        if (kind == Kind.CASED) { bakeCase(g, variant == 1); return bmp }
        val look = look(kind)
        val body = RectF(0f, 0f, CELL, CELL)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, CELL, CELL, look.col2.toInt(), look.col.toInt(), Shader.TileMode.CLAMP)
        g.drawRoundRect(body, R, R, p)
        p.shader = null
        val st = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        g.save()
        g.clipPath(Path().apply { addRoundRect(body, R, R, Path.Direction.CW) })
        val rnd = seededRandom(kind.ordinal * 977 + 31)
        when (kind) {
            Kind.CAPACITOR -> {
                /* warning stripes along the top */
                p.color = Color.argb(46, 127, 216, 255)
                var x = -20f
                while (x < CELL) { g.drawPath(Path().apply { moveTo(x, 0f); lineTo(x + 8f, 0f); lineTo(x + 2f, 6f); lineTo(x - 6f, 6f); close() }, p); x += 12f }
            }
            Kind.BEACON -> {
                /* radio rings */
                st.color = Color.argb(46, 255, 160, 110); st.strokeWidth = 1.2f
                for (r in 1..4) g.drawCircle(CELL * 0.26f, CELL * 0.2f, r * 9f, st)
            }
            Kind.WORM -> {
                val t = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = mono; textSize = 5.5f; color = Color.argb(60, 255, 120, 220) }
                for (row in 0 until 7) {
                    val line = buildString { repeat(11) { append(if (rnd() < 0.5) '0' else '1') } }
                    g.drawText(line, 3f + (row % 2) * 3f, 3f + row * 8.6f - t.ascent(), t)
                }
            }
            Kind.PATCH -> {
                /* the cable, from jack to jack */
                st.color = 0xFFFFB347.toInt(); st.strokeWidth = 3.2f
                g.drawPath(Path().apply { moveTo(12f, 47f); cubicTo(12f, 38f, 60f, 38f, 60f, 47f) }, st)
                p.color = 0xFF9AA4AE.toInt()
                for (x in floatArrayOf(12f, 60f)) { g.drawRect(x - 3.5f, 46f, x + 3.5f, 53f, p); g.drawRect(x - 1.5f, 53f, x + 1.5f, 57f, p) }
            }
            Kind.WALL -> {
                /* static */
                for (i in 0 until 260) {
                    val v = (40 + rnd() * 120).toInt()
                    p.color = Color.argb((60 + rnd() * 120).toInt(), v, v + 8, v + 14)
                    val x0 = (rnd() * 36).toInt() * 2f; val y0 = (rnd() * 28).toInt() * 2f
                    g.drawRect(x0, y0, x0 + 2f, y0 + 2f, p)
                }
                p.color = Color.argb(40, 255, 255, 255)
                for (y in 0 until 7) g.drawRect(0f, 4f + y * 8f, CELL, 5f + y * 8f, p)
            }
            else -> {}
        }
        g.restore()
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f; color = look.edge.toInt() }
        g.drawRoundRect(RectF(1f, 1f, CELL - 1f, CELL - 1f), R, R, edge)
        p.color = Color.argb(107, 0, 0, 0)
        g.drawRect(0f, CELL - 15f, CELL, CELL, p)
        val t = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = mono; textSize = 6.4f; isFakeBoldText = true; textAlign = Paint.Align.CENTER; color = look.text.toInt()
        }
        fitText(t, look.name, CELL - 6f)
        drawMiddle(g, look.name, CELL / 2, CELL - 7.5f, t)
        /* the glyph, top left */
        g.save()
        g.translate(7f, 6f)
        st.color = look.text.toInt(); st.strokeWidth = 1.2f
        val fi = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = look.text.toInt() }
        when (kind) {
            Kind.CAPACITOR -> {
                g.drawLine(0f, 7f, 8f, 7f, st); g.drawLine(8f, 1f, 8f, 13f, st)
                g.drawArc(RectF(11f, 1f, 19f, 13f), 120f, 120f, false, st)
                g.drawLine(12.5f, 7f, 21f, 7f, st)
            }
            Kind.BEACON -> {
                g.drawLine(4f, 14f, 8f, 2f, st); g.drawLine(12f, 14f, 8f, 2f, st); g.drawLine(5.3f, 10f, 10.7f, 10f, st)
                g.drawCircle(8f, 2f, 1.6f, fi)
                g.drawArc(RectF(12f, -2f, 20f, 6f), -60f, 120f, false, st)
                g.drawArc(RectF(10f, -5f, 24f, 9f), -50f, 100f, false, st)
            }
            Kind.WORM -> {
                for (i in 0 until 5) g.drawCircle(2.5f + i * 4.2f, 7f + sin(i * 1.3f) * 3f, if (i == 4) 2.4f else 1.8f, fi)
            }
            Kind.PATCH -> {
                g.drawRect(1f, 4f, 6f, 12f, st); g.drawRect(15f, 4f, 20f, 12f, st)
                g.drawPath(Path().apply { moveTo(3.5f, 4f); cubicTo(3.5f, -2f, 17.5f, -2f, 17.5f, 4f) }, st)
            }
            Kind.WALL -> {
                for (row in 0 until 3) {
                    val off = if (row % 2 == 0) 0f else 3.5f
                    var x = off
                    while (x < 20f) { g.drawRect(x, row * 4.6f, minOf(x + 6f, 21f), row * 4.6f + 3.6f, st); x += 7f }
                }
            }
            else -> {}
        }
        g.restore()
        return bmp
    }

    /** The lead case that goes over a Cased Signal's tile; [cracked] after the first hit. */
    private fun bakeCase(g: Canvas, cracked: Boolean) {
        val outer = RectF(0f, 0f, CELL, CELL)
        val frame = Path().apply {
            addRoundRect(outer, R, R, Path.Direction.CW)
            addRoundRect(RectF(8f, 8f, CELL - 8f, CELL - 16f), 4f, 4f, Path.Direction.CCW)
        }
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, CELL, CELL, 0xFF9AA4AE.toInt(), 0xFF3A4249.toInt(), Shader.TileMode.CLAMP)
        g.drawPath(frame, p)
        p.shader = null
        /* the label strip of the case */
        p.color = 0xFF2A3036.toInt()
        g.drawRect(0f, CELL - 16f, CELL, CELL, p)
        g.save(); g.clipPath(Path().apply { addRoundRect(outer, R, R, Path.Direction.CW) })
        g.drawRect(0f, CELL - 16f, CELL, CELL, p)
        g.restore()
        val t = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = mono; textSize = 6.4f; isFakeBoldText = true; textAlign = Paint.Align.CENTER; color = 0xFFD8DEE4.toInt()
        }
        val name = if (cracked) "CASE CRACKED" else "CASED SIGNAL"
        fitText(t, name, CELL - 6f)
        drawMiddle(g, name, CELL / 2, CELL - 7.5f, t)
        /* rivets */
        p.color = 0xFFC8D0D6.toInt()
        for ((x, y) in listOf(4f to 4f, CELL - 4f to 4f, 4f to CELL - 20f, CELL - 4f to CELL - 20f)) g.drawCircle(x, y, 1.8f, p)
        val st = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f; color = 0xFF1C2126.toInt() }
        g.drawRoundRect(RectF(8f, 8f, CELL - 8f, CELL - 16f), 4f, 4f, st)
        st.color = Color.argb(140, 0, 0, 0); st.strokeWidth = 1f
        g.drawRoundRect(RectF(0.5f, 0.5f, CELL - 0.5f, CELL - 0.5f), R, R, st)
        if (cracked) {
            st.color = 0xFF0A0C0E.toInt(); st.strokeWidth = 1.6f; st.strokeJoin = Paint.Join.ROUND
            g.drawPath(Path().apply { moveTo(0f, 22f); lineTo(5f, 25f); lineTo(3f, 30f); lineTo(8f, 33f) }, st)
            g.drawPath(Path().apply { moveTo(CELL, 14f); lineTo(CELL - 4f, 18f); lineTo(CELL - 2f, 23f); lineTo(CELL - 8f, 26f) }, st)
            g.drawPath(Path().apply { moveTo(30f, 0f); lineTo(33f, 4f); lineTo(29f, 8f) }, st)
            st.color = Color.argb(120, 255, 255, 255); st.strokeWidth = 0.8f
            g.drawPath(Path().apply { moveTo(1f, 23f); lineTo(6f, 26f); lineTo(4f, 31f) }, st)
        }
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

        fun look(kind: Kind) = when (kind) {
            Kind.CAPACITOR -> Look(0xFF145A73, 0xFF0B2E3A, 0xFF7FD8FF, 0xFFBFF4FF, "SURGE CAPACITOR")
            Kind.BEACON -> Look(0xFF9A3000, 0xFF3D0F00, 0xFFFF7A3D, 0xFFFFD2B8, "TRACE BEACON")
            Kind.WORM -> Look(0xFF7A1458, 0xFF2A0626, 0xFFFF5AC8, 0xFFFFC8EE, "WORM")
            Kind.PATCH -> Look(0xFF2E3A52, 0xFF141A26, 0xFFFFB347, 0xFFFFD27A, "PATCH CABLE")
            Kind.WALL -> Look(0xFF50575E, 0xFF22262A, 0xFF9AA4AE, 0xFFD8DEE4, "STATIC WALL")
            else -> Look(0xFF9AA4AE, 0xFF3A4249, 0xFF9AA4AE, 0xFFD8DEE4, "CASED SIGNAL")
        }
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
