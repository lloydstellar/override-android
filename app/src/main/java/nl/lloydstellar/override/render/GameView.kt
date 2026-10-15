package nl.lloydstellar.override.render

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
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
import android.os.Build
import android.os.SystemClock
import android.view.Choreographer
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import nl.lloydstellar.override.Fx
import nl.lloydstellar.override.GameController
import nl.lloydstellar.override.LogLine
import nl.lloydstellar.override.Mode
import nl.lloydstellar.override.Phase
import nl.lloydstellar.override.R
import nl.lloydstellar.override.audio.Haptics
import nl.lloydstellar.override.audio.Sfx
import nl.lloydstellar.override.data.Phosphor
import nl.lloydstellar.override.data.signal
import nl.lloydstellar.override.engine.Dir
import nl.lloydstellar.override.engine.MoveResult
import nl.lloydstellar.override.engine.Tile
import nl.lloydstellar.override.engine.Sector
import nl.lloydstellar.override.engine.threat
import nl.lloydstellar.override.render.Sprites.Companion.drawMiddle
import nl.lloydstellar.override.render.Sprites.Companion.withAlpha
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * The CRT terminal of override.js on a phone: the header strip, the HUD,
 * the AI Trace bar, the matrix, the signal ladder, the VU meter and the
 * system log, with the browser's tile art, sparks, frequency rings, power
 * surge, static and scanlines.
 *
 * Effects run on the browser's fixed 60 Hz tick; the slide itself is timed
 * in milliseconds, so a 120 Hz screen gets every frame of it. A swipe made
 * while a slide is still running is never dropped: the slide is finished
 * early and the new move starts.
 */
@SuppressLint("ViewConstructor")
class GameView(context: Context, private val game: GameController) : View(context), Choreographer.FrameCallback {

    var topReserve = 0f
        set(v) { if (field != v) { field = v; relayout() } }
    var bottomReserve = 0f
        set(v) { if (field != v) { field = v; relayout() } }
    /** Screen shake, static and flashes (off when the player or the system asks for less motion). */
    var effectsEnabled = true
    /** The tutorial points at the trace bar. */
    var highlightTrace = false
    var phosphor: Phosphor = nl.lloydstellar.override.data.PHOSPHORS[0]
        set(v) { if (field.id != v.id) { field = v; bgKey = "" } }

    private val mono: Typeface = ResourcesCompat.getFont(context, R.font.share_tech_mono) ?: Typeface.MONOSPACE
    private val display: Typeface = ResourcesCompat.getFont(context, R.font.orbitron) ?: Typeface.DEFAULT_BOLD
    private val sprites = Sprites(mono, display)

    private var lay: TerminalLayout? = null
    private var bg: Bitmap? = null
    private var overlay: Bitmap? = null
    private var bgKey = ""

    private val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = mono }
    private val disp = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = display; fontVariationSettings = "'wght' 700" }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rnd = Random(7)

    /* loop */
    private var running = false
    private var lastNanos = 0L
    private var acc = 0.0
    private val step = 1e9 / 60
    private var frameT = 0

    /* animation and effects */
    private val slide = SlideAnim()
    private var impact: MoveResult? = null
    private val pops = HashMap<Int, Long>()
    private val appears = HashMap<Int, Long>()
    private val particles = ArrayList<Particle>()
    private val rings = ArrayList<Ring>()
    private val floaters = ArrayList<Floater>()
    private var surgeDir = -1
    private var surgeT = 0
    private var shake = 0f
    private var flash = 0f
    private var noiseBurst = 0
    private val vu = Vu()
    private var winAt = 0L
    private var lostAt = 0L
    private var rollbackAt = 0L

    /* input */
    private var downX = 0f
    private var downY = 0f
    private var tracking = false
    private var fired = false
    private var pending = -1
    private val slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val swipeMin = max(slop * 2.2f, 20f * resources.displayMetrics.density)

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_POLITE
        listOf(Dir.UP to "Slide up", Dir.RIGHT to "Slide right", Dir.DOWN to "Slide down", Dir.LEFT to "Slide left").forEach { (d, label) ->
            ViewCompat.addAccessibilityAction(this, label) { _, _ -> swipe(d); true }
        }
        describe()
    }

    /* ---------------- layout and baking ---------------- */

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = relayout()

    private fun relayout() {
        if (width <= 0 || height <= 0) return
        lay = TerminalLayout.compute(width, height, topReserve, bottomReserve, game.engine.size)
        bgKey = ""
        invalidate()
    }

    private fun ensureBaked() {
        val l = lay ?: return
        val key = "${width}x$height/${l.n}/${phosphor.id}/${game.mode}/${game.sector?.id}/${game.level}/${l.s}/${l.oy}"
        if (key == bgKey && bg != null) return
        bgKey = key
        bg?.recycle(); overlay?.recycle()
        bg = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bakeBackground(Canvas(it), l) }
        overlay = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bakeOverlay(Canvas(it), l) }
    }

    private fun col(c: Long) = c.toInt()
    private val main get() = col(phosphor.main)

    private fun bakeBackground(g: Canvas, l: TerminalLayout) {
        val th = phosphor
        p.shader = RadialGradient(width / 2f, height * 0.42f, max(width, height) * 0.75f,
            col(th.bg2), col(th.bg), Shader.TileMode.CLAMP)
        g.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p)
        p.shader = null
        /* faint phosphor dot grid, every 6 units like the browser */
        p.color = withAlpha(main, 0.035f)
        val d = 6f * l.s
        var y = 4f
        while (y < height) { var x = 4f; while (x < width) { g.drawRect(x, y, x + l.s * 0.6f, y + l.s * 0.6f, p); x += d }; y += d }

        g.save()
        g.translate(l.ox, l.oy)
        g.scale(l.s, l.s)

        /* header strip */
        val h = l.header
        p.color = withAlpha(main, 0.06f)
        g.drawRect(h, p)
        stroke.strokeWidth = 1f; stroke.color = withAlpha(main, 0.25f)
        g.drawLine(h.left, h.bottom + 0.5f, h.right, h.bottom + 0.5f, stroke)
        txt.color = col(th.mid); txt.textSize = 7.5f; txt.textAlign = Paint.Align.LEFT; txt.letterSpacing = 0.05f
        val s = game.sector
        val title = when {
            s == null -> "NEXUS-9 GRID // SECTOR 7 // " + if (game.level == 0) "MK-II" else threat(game.level).short
            s.kind == Sector.Kind.SECTOR -> "${s.code} // ${s.name} // TARGET ${s.rules.target}"
            else -> "${s.name} // ${s.rules.target}"
        }
        Sprites.fitText(txt, title, h.width() * 0.56f)
        drawMiddle(g, title, h.left + 6f, h.centerY(), txt)

        /* HUD labels */
        val u = l.hud
        txt.textSize = 9f; txt.color = col(th.mid)
        hudColumns(u).forEachIndexed { i, x -> g.drawText(hudLabel(i), x, u.top + 10f, txt) }
        stroke.color = withAlpha(main, 0.14f)
        g.drawLine(u.left, u.bottom + 0.5f, u.right, u.bottom + 0.5f, stroke)

        /* trace bar housing */
        val t = l.trace
        g.drawText("AI TRACE", t.left, t.top + 9f, txt)
        val bar = traceBar(t)
        p.color = Color.argb(128, 0, 0, 0)
        g.drawRoundRect(bar, 3f, 3f, p)
        stroke.color = withAlpha(main, 0.28f)
        g.drawRoundRect(bar, 3f, 3f, stroke)

        /* the matrix plate and its slots */
        val gr = l.grid
        p.color = Color.argb(82, 0, 0, 0)
        g.drawRoundRect(gr, 8f, 8f, p)
        stroke.color = withAlpha(main, 0.22f)
        g.drawRoundRect(RectF(gr.left + 0.5f, gr.top + 0.5f, gr.right - 0.5f, gr.bottom - 0.5f), 8f, 8f, stroke)
        for (x in 0 until l.n) for (y in 0 until l.n) {
            if (game.engine.isDead(x, y)) drawDeadSlot(g, l, l.cellX(x.toFloat()), l.cellY(y.toFloat()))
            else drawSlot(g, l, l.cellX(x.toFloat()), l.cellY(y.toFloat()))
        }

        /* signal ladder housing */
        val ld = l.ladder
        p.color = Color.argb(128, 0, 0, 0)
        g.drawRoundRect(ld, 4f, 4f, p)
        stroke.color = withAlpha(main, 0.22f)
        g.drawRoundRect(ld, 4f, 4f, stroke)
        txt.textSize = 6.5f; txt.color = col(th.mid)
        drawMiddle(g, "SIG", ld.left + 5f, ld.centerY(), txt)

        /* bottom panels: VU and SYS.LOG */
        for (r in listOf(l.vu, l.log)) {
            p.color = Color.argb(115, 0, 0, 0)
            g.drawRoundRect(r, 6f, 6f, p)
            stroke.color = withAlpha(main, 0.22f)
            g.drawRoundRect(RectF(r.left + 0.5f, r.top + 0.5f, r.right - 0.5f, r.bottom - 0.5f), 6f, 6f, stroke)
        }
        txt.textSize = 7.5f
        g.drawText("SYS.LOG", l.log.left + 8f, l.log.top + 12f, txt)
        g.drawText("VU  INJECTION LEVEL", l.vu.left + 8f, l.vu.top + 12f, txt)
        drawVuFace(g, l)
        g.restore()
    }

    private fun hudColumns(u: RectF): FloatArray {
        val w = u.width()
        return floatArrayOf(u.left, u.left + w * 0.375f, u.left + w * 0.61f, u.left + w * 0.83f)
    }

    private fun hudLabel(i: Int): String {
        val s = game.sector
        return if (s == null) arrayOf("FREQUENCY", "BEST", "INJECTIONS", "UPTIME")[i]
        else arrayOf("FREQUENCY", "TARGET", "INJECTIONS", if (s.rules.moveLimit > 0) "LIMIT" else "PAR")[i]
    }

    private fun traceBar(t: RectF) = RectF(t.left + 64f, t.top, t.right, t.bottom)

    private fun drawSlot(g: Canvas, l: TerminalLayout, px: Float, py: Float) {
        val c = l.cell
        val r = RectF(px, py, px + c, py + c)
        p.shader = LinearGradient(0f, py, 0f, py + c, Color.argb(199, 0, 0, 0), Color.argb(115, 0, 0, 0), Shader.TileMode.CLAMP)
        g.drawRoundRect(r, 7f, 7f, p)
        p.shader = null
        stroke.strokeWidth = 1f
        stroke.color = withAlpha(main, 0.16f)
        g.drawRoundRect(r, 7f, 7f, stroke)
        stroke.color = Color.argb(153, 0, 0, 0)
        g.drawLine(px + 6f, py + 1.5f, px + c - 6f, py + 1.5f, stroke)
        stroke.color = withAlpha(main, 0.22f)
        g.drawLine(px + 6f, py + c - 1.5f, px + c - 6f, py + c - 1.5f, stroke)
        p.color = withAlpha(main, 0.3f)
        for ((nx, ny) in listOf(5f to 5f, c - 6f to 5f, 5f to c - 6f, c - 6f to c - 6f)) g.drawRect(px + nx, py + ny, px + nx + 1.5f, py + ny + 1.5f, p)
    }

    /** A burned-out cell: black glass with a crack and a dead label. */
    private fun drawDeadSlot(g: Canvas, l: TerminalLayout, px: Float, py: Float) {
        val c = l.cell
        val r = RectF(px, py, px + c, py + c)
        p.color = Color.argb(235, 4, 4, 4)
        g.drawRoundRect(r, 7f, 7f, p)
        stroke.strokeWidth = 1f; stroke.color = withAlpha(main, 0.10f)
        g.drawRoundRect(r, 7f, 7f, stroke)
        stroke.color = Color.argb(70, 255, 255, 255); stroke.strokeWidth = 0.8f
        val crack = Path().apply {
            moveTo(px + c * 0.18f, py + c * 0.12f); lineTo(px + c * 0.42f, py + c * 0.38f); lineTo(px + c * 0.36f, py + c * 0.55f)
            lineTo(px + c * 0.62f, py + c * 0.74f); lineTo(px + c * 0.84f, py + c * 0.9f)
            moveTo(px + c * 0.42f, py + c * 0.38f); lineTo(px + c * 0.7f, py + c * 0.3f)
        }
        g.drawPath(crack, stroke)
        txt.textSize = 7f; txt.color = withAlpha(main, 0.35f); txt.textAlign = Paint.Align.CENTER
        drawMiddle(g, "DEAD", px + c / 2, py + c - 9f, txt)
        txt.textAlign = Paint.Align.LEFT
    }

    /* VU face: black dial, phosphor scale, red zone past 0 dB */
    private fun vuCenter(l: TerminalLayout) = l.vu.centerX() to l.vu.bottom - 16f
    private fun vuRadius(l: TerminalLayout) = min(l.vu.width() * 0.46f, 78f)
    private val vuA0 = -PI * 0.86
    private val vuA1 = -PI * 0.14
    private fun vuAngle(t: Float) = vuA0 + (vuA1 - vuA0) * t

    private fun drawVuFace(g: Canvas, l: TerminalLayout) {
        val (cx, cy) = vuCenter(l)
        val r = vuRadius(l)
        g.save()
        g.clipRect(l.vu.left + 2f, l.vu.top + 16f, l.vu.right - 2f, l.vu.bottom - 2f)
        val oval = RectF(cx - r, cy - r, cx + r, cy + r)
        fun deg(a: Double) = Math.toDegrees(a).toFloat()
        stroke.color = withAlpha(main, 0.5f); stroke.strokeWidth = 1.5f
        g.drawArc(oval, deg(vuA0), deg(vuAngle(0.72f) - vuA0), false, stroke)
        stroke.color = 0xFFFF4D4D.toInt(); stroke.strokeWidth = 3f
        g.drawArc(oval, deg(vuAngle(0.72f)), deg(vuA1 - vuAngle(0.72f)), false, stroke)
        val marks = listOf("-20" to 0f, "-10" to 0.2f, "-7" to 0.32f, "-5" to 0.42f, "-3" to 0.54f, "0" to 0.72f, "+3" to 1f)
        txt.textSize = 6.5f; txt.textAlign = Paint.Align.CENTER
        stroke.strokeWidth = 1f
        for ((label, t) in marks) {
            val a = vuAngle(t)
            val hot = t >= 0.72f
            stroke.color = if (hot) 0xFFFF6B6B.toInt() else main
            g.drawLine(cx + cos(a).toFloat() * (r - 6), cy + sin(a).toFloat() * (r - 6), cx + cos(a).toFloat() * (r + 1), cy + sin(a).toFloat() * (r + 1), stroke)
            txt.color = if (hot) 0xFFFF6B6B.toInt() else col(phosphor.mid)
            drawMiddle(g, label, cx + cos(a).toFloat() * (r - 14), cy + sin(a).toFloat() * (r - 14), txt)
        }
        stroke.color = withAlpha(main, 0.35f)
        for (i in 0..20) {
            val a = vuAngle(i / 20f)
            g.drawLine(cx + cos(a).toFloat() * (r - 3), cy + sin(a).toFloat() * (r - 3), cx + cos(a).toFloat() * (r + 1), cy + sin(a).toFloat() * (r + 1), stroke)
        }
        disp.textSize = 9f; disp.color = col(phosphor.mid); disp.textAlign = Paint.Align.CENTER
        drawMiddle(g, "VU", cx, cy - 34f, disp)
        txt.textSize = 6.5f
        drawMiddle(g, "dB", cx, cy - 24f, txt)
        txt.textAlign = Paint.Align.LEFT
        g.restore()
    }

    private fun bakeOverlay(g: Canvas, l: TerminalLayout) {
        /* scanlines: one dark line every 3 units */
        p.color = Color.argb(46, 0, 0, 0)
        val step = 3f * l.s
        var y = 0f
        while (y < height) { g.drawRect(0f, y, width.toFloat(), y + l.s * 0.9f, p); y += step }
        /* vignette */
        p.shader = RadialGradient(width / 2f, height / 2f, max(width, height) * 0.72f,
            intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(128, 0, 0, 0)), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        g.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p)
        /* the glass: a soft highlight top left */
        p.shader = RadialGradient(width * 0.22f, height * 0.1f, width * 0.7f,
            intArrayOf(Color.argb(18, 255, 255, 255), Color.argb(4, 255, 255, 255), Color.TRANSPARENT), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        g.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p)
        p.shader = null
    }

    /* ---------------- loop ---------------- */

    fun startLoop() {
        if (running) return
        running = true
        lastNanos = 0L
        Choreographer.getInstance().postFrameCallback(this)
    }

    fun stopLoop() {
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        if (lastNanos == 0L) lastNanos = frameTimeNanos
        acc += min((frameTimeNanos - lastNanos).toDouble(), 1e8)
        lastNanos = frameTimeNanos
        drainFx()
        val now = SystemClock.uptimeMillis()
        if (pending >= 0 && slide.progress(now) >= 0.5f) { val d = pending; pending = -1; doMove(d) }
        if (impact != null && slide.progress(now) >= 1f) fireImpact()
        while (acc >= step) { tick(); acc -= step }
        if (Build.VERSION.SDK_INT >= 35) {
            requestedFrameRate = if (slide.active || particles.isNotEmpty() || winAt > 0 && now - winAt < 1500)
                REQUESTED_FRAME_RATE_CATEGORY_HIGH else REQUESTED_FRAME_RATE_CATEGORY_NORMAL
        }
        invalidate()
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun drainFx() {
        while (true) {
            when (val f = game.fx.removeFirstOrNull() ?: break) {
                is Fx.Moved -> startSlide(f.r)
                is Fx.Snap -> { snap(); describe() }
                is Fx.Floater -> addFloater(f.text, f.color.toInt(), f.big)
                is Fx.Win -> { winAt = SystemClock.uptimeMillis(); if (effectsEnabled) { shake = 10f }; flash = 1f }
                is Fx.Lost -> { lostAt = SystemClock.uptimeMillis(); if (effectsEnabled) { shake = 14f; noiseBurst = 90 } }
                is Fx.Rollback -> { snap(); rollbackAt = SystemClock.uptimeMillis(); if (effectsEnabled) noiseBurst = 16; describe() }
            }
        }
    }

    private fun snap() {
        slide.active = false
        impact = null
        pops.clear(); appears.clear()
        lostAt = 0L
        if (game.phase != Phase.WON) winAt = 0L
        relayoutIfSizeChanged()
    }

    private fun relayoutIfSizeChanged() {
        if (lay?.n != game.engine.size) relayout() else bgKey = ""
    }

    private fun startSlide(r: MoveResult) {
        fireImpact()
        val now = SystemClock.uptimeMillis()
        slide.start = now
        slide.ghosts = r.ghosts.toList()
        slide.active = true
        impact = r
        r.merges.forEach { pops[it.id] = now + SlideAnim.SLIDE_MS.toLong() }
        r.spawned?.let { appears[it.id] = now + (SlideAnim.SLIDE_MS * 0.5f).toLong() }
        surgeDir = r.dir; surgeT = 0
        vu.level = min(1f, 0.45f + r.merges.size * 0.18f + r.purges.size * 0.1f)
        if (r.spawned?.corrupt == true && effectsEnabled) noiseBurst = max(noiseBurst, 14)
        if (r.lock != null && effectsEnabled) { shake = 9f; noiseBurst = 20 }
        describe()
    }

    /** Sparks and rings where the slide lands. */
    private fun fireImpact() {
        val r = impact ?: return
        impact = null
        val l = lay ?: return
        r.merges.forEach { m ->
            val c = signal(m.value).col2.toInt()
            sparks(l, m.x, m.y, c, if (m.value >= 128) 22 else 12)
            rings.add(Ring(l.cellX(m.x.toFloat()) + l.cell / 2, l.cellY(m.y.toFloat()) + l.cell / 2, 10f, 26f, c))
        }
        r.purges.forEach { (x, y, _) ->
            sparks(l, x, y, 0xFFFF4D6D.toInt(), 18)
            rings.add(Ring(l.cellX(x.toFloat()) + l.cell / 2, l.cellY(y.toFloat()) + l.cell / 2, 10f, 30f, 0xFFFF4D6D.toInt()))
        }
        r.fried.forEach { t ->
            sparks(l, t.x, t.y, 0xFFFFE64D.toInt(), 30)
            rings.add(Ring(l.cellX(t.x.toFloat()) + l.cell / 2, l.cellY(t.y.toFloat()) + l.cell / 2, 14f, 36f, 0xFFFFE64D.toInt()))
            flash = if (effectsEnabled) 0.8f else 0.3f
        }
    }

    private fun sparks(l: TerminalLayout, cx: Int, cy: Int, color: Int, n: Int) {
        val x0 = l.cellX(cx.toFloat()) + l.cell / 2
        val y0 = l.cellY(cy.toFloat()) + l.cell / 2
        repeat(n) {
            val a = rnd.nextDouble() * PI * 2
            val sp = 1.2 + rnd.nextDouble() * 3.2
            particles.add(Particle(x0, y0, (cos(a) * sp).toFloat(), (sin(a) * sp).toFloat(), 18f + rnd.nextFloat() * 22f,
                if (rnd.nextFloat() < 0.3f) Color.WHITE else color))
        }
    }

    private fun addFloater(text: String, color: Int, big: Boolean) {
        val l = lay ?: return
        var y = l.grid.top + l.grid.height() * (if (big) 0.45f else 0.62f)
        while (floaters.any { abs(it.y - y) < 12f && it.life > 30 }) y += 14f
        floaters.add(Floater(text, color, y, if (big) 80f else 110f, big, if (big) -0.35f else -0.25f))
    }

    /* the browser's update(), one 60 Hz tick */
    private fun tick() {
        frameT++
        if (shake > 0) shake *= 0.82f
        if (flash > 0) flash *= 0.88f
        if (noiseBurst > 0) noiseBurst--
        if (surgeDir >= 0) { surgeT++; if (surgeT > 14) surgeDir = -1 }
        val it = particles.iterator()
        while (it.hasNext()) {
            val q = it.next()
            q.x += q.vx; q.y += q.vy; q.vx *= 0.94f; q.vy = q.vy * 0.94f + 0.06f; q.life--
            if (q.life <= 0) it.remove()
        }
        rings.removeAll { r -> r.r += 2.6f; r.life--; r.life <= 0 }
        floaters.removeAll { f -> f.y += f.dy; f.life--; f.life <= 0 }

        /* meters */
        vu.level *= 0.90f
        val target = vu.level + if (game.phase == Phase.PLAYING) 0.04f + 0.03f * sin(frameT * 0.05f) else 0f
        vu.vel += (target - vu.needle) * 0.22f
        vu.vel *= 0.68f
        vu.needle = (vu.needle + vu.vel).coerceIn(0f, 1f)
        val seg = (vu.needle * 20).roundToInt() - 1
        if (seg >= vu.peak) { vu.peak = seg; vu.peakHold = 40 } else if (vu.peakHold > 0) vu.peakHold-- else vu.peak = -1
    }

    /* ---------------- drawing ---------------- */

    override fun onDraw(canvas: Canvas) {
        val l = lay ?: return
        ensureBaked()
        val now = SystemClock.uptimeMillis()
        canvas.drawBitmap(bg!!, 0f, 0f, null)
        canvas.save()
        if (effectsEnabled && shake > 0.3f) canvas.translate((rnd.nextFloat() - 0.5f) * shake * l.s, (rnd.nextFloat() - 0.5f) * shake * l.s)
        canvas.translate(l.ox, l.oy)
        canvas.scale(l.s, l.s)
        drawHeader(canvas, l)
        drawHud(canvas, l)
        drawTrace(canvas, l)
        drawLadder(canvas, l)
        drawVu(canvas, l)
        drawLog(canvas, l, now)
        drawLocks(canvas, l)
        drawTiles(canvas, l, now)
        drawEffects(canvas, l)
        drawEndings(canvas, l, now)
        canvas.restore()
        if (effectsEnabled) drawNoise(canvas)
        canvas.drawBitmap(overlay!!, 0f, 0f, null)
    }

    private fun drawHeader(c: Canvas, l: TerminalLayout) {
        val h = l.header
        val m = game.engine.maxTile
        txt.textSize = 7.5f; txt.textAlign = Paint.Align.RIGHT
        if (m >= 2) {
            val d = signal(m)
            txt.color = d.col2.toInt()
            drawMiddle(c, "MAX SIGNAL ${m.toString().padStart(4, '0')}  ${d.name}", h.right - 16f, h.centerY(), txt)
        }
        txt.textAlign = Paint.Align.LEFT
        /* link LED: green while live, red when the signal is lost, amber otherwise */
        p.color = when (game.phase) {
            Phase.PLAYING, Phase.WON -> 0xFF39FF6A.toInt()
            Phase.OVER, Phase.RESULTS -> 0xFFFF4D4D.toInt()
            Phase.PAUSED -> if (frameT % 50 > 30) Color.argb(64, 255, 176, 0) else 0xFFFFB000.toInt()
        }
        c.drawCircle(h.right - 6f, h.centerY(), 2.4f, p)
    }

    private fun drawHud(c: Canvas, l: TerminalLayout) {
        val u = l.hud
        val e = game.engine
        val cols = hudColumns(u)
        val y = u.top + 34f
        val th = phosphor
        disp.textAlign = Paint.Align.LEFT
        glowText(c, "${min(e.score, 999999).toString().padStart(6, '0')} Hz", cols[0], y, col(th.bright), 17f, 6f)
        val s = game.sector
        val second = if (s == null) max(game.best, e.score).toString().padStart(6, '0') else s.rules.target.toString().padStart(4, '0')
        val record = game.beatingRecord
        dispText(c, second, cols[1], y, if (record) 0xFFFFB000.toInt() else main, 15f)
        if (record) { txt.textSize = 6.5f; txt.color = 0xFFFFB000.toInt(); c.drawText("RECORD", cols[1] + 52f, u.top + 10f, txt) }
        dispText(c, e.moves.toString().padStart(4, '0'), cols[2], y, main, 15f)
        val fourth = when {
            s == null -> fmtTime(game.seconds)
            s.rules.moveLimit > 0 -> (s.rules.moveLimit - e.moves).coerceAtLeast(0).toString().padStart(4, '0')
            else -> s.par.toString().padStart(4, '0')
        }
        val warnLimit = s != null && s.rules.moveLimit > 0 && s.rules.moveLimit - e.moves <= 30
        val overPar = s != null && s.rules.moveLimit == 0 && e.moves > s.par
        dispText(c, fourth, cols[3], y, when {
            warnLimit -> if (frameT % 30 < 15) 0xFFFF4D6D.toInt() else 0xFFFFB000.toInt()
            overPar -> col(th.dim)
            else -> main
        }, 15f)
    }

    private fun drawTrace(c: Canvas, l: TerminalLayout) {
        val t = l.trace
        val trace = game.engine.trace
        val warn = trace >= 80
        val color = if (warn) (if (frameT % 30 < 15) 0xFFFF4D6D.toInt() else 0xFFFFB000.toInt()) else if (trace >= 50) 0xFFFFB000.toInt() else main
        dispText(c, "${trace.toString().padStart(3, '0')}%", t.left, t.bottom - 1f, color, 10f)
        val bar = traceBar(t)
        val segs = 31
        val inner = RectF(bar.left + 3f, bar.top + 3f, bar.right - 3f, bar.bottom - 3f)
        val sw = (inner.width() - (segs - 1)) / segs
        val lit = (trace / 100f * segs).roundToInt()
        for (i in 0 until segs) {
            val f = i / (segs - 1f)
            val cc = if (f < 0.5f) main else if (f < 0.8f) 0xFFFFB000.toInt() else 0xFFFF4D6D.toInt()
            p.color = if (i < lit) cc else withAlpha(cc, 0.12f)
            val x = inner.left + i * (sw + 1)
            c.drawRect(x, inner.top, x + sw, inner.bottom, p)
        }
        if (warn && frameT % 30 < 15) { p.color = Color.argb(20, 255, 77, 109); c.drawRoundRect(bar, 3f, 3f, p) }
        if (highlightTrace) {
            /* a slow amber pulse around the whole trace row */
            val a = 0.45f + 0.45f * sin(frameT * 0.12f)
            stroke.color = withAlpha(0xFFFFB000.toInt(), a); stroke.strokeWidth = 2f
            c.drawRoundRect(RectF(t.left - 5f, t.top - 6f, t.right + 4f, t.bottom + 5f), 5f, 5f, stroke)
        }
    }

    /* the signal ladder of the browser, lying down under the matrix */
    private fun drawLadder(c: Canvas, l: TerminalLayout) {
        val r = l.ladder
        val n = 20
        val x0 = r.left + 20f
        val segW = (r.right - 4f - x0 - (n - 1) * 2f) / n
        val litN = (vu.needle * n).roundToInt()
        for (i in 0 until n) {
            val cc = if (i < 12) 0xFF39FF6A.toInt() else if (i < 17) 0xFFFFB000.toInt() else 0xFFFF4D4D.toInt()
            val on = i < litN || (i == vu.peak && vu.peakHold > 0)
            p.color = if (on) cc else withAlpha(cc, 0.12f)
            val x = x0 + i * (segW + 2f)
            c.drawRect(x, r.top + 3f, x + segW, r.bottom - 3f, p)
            if (on && i >= litN - 1) {
                p.alpha = 128
                c.drawBitmap(sprites.glow(cc), null, RectF(x - 8f, r.top - 6f, x + segW + 8f, r.bottom + 6f), p)
                p.alpha = 255
            }
        }
    }

    private fun drawVu(c: Canvas, l: TerminalLayout) {
        val (cx, cy) = vuCenter(l)
        val r = vuRadius(l)
        c.save()
        c.clipRect(l.vu.left + 2f, l.vu.top + 16f, l.vu.right - 2f, l.vu.bottom - 2f)
        val a = vuAngle(vu.needle.coerceIn(0f, 1f))
        stroke.color = col(phosphor.bright); stroke.strokeWidth = 1.6f
        stroke.setShadowLayer(6f, 0f, 0f, main)
        c.drawLine(cx, cy, cx + cos(a).toFloat() * (r + 2), cy + sin(a).toFloat() * (r + 2), stroke)
        stroke.clearShadowLayer()
        stroke.color = 0xFFFF4D4D.toInt(); stroke.strokeWidth = 2f
        c.drawLine(cx + cos(a).toFloat() * (r - 10), cy + sin(a).toFloat() * (r - 10), cx + cos(a).toFloat() * (r + 2), cy + sin(a).toFloat() * (r + 2), stroke)
        p.color = 0xFF1C2026.toInt()
        c.drawCircle(cx, cy, 7f, p)
        stroke.color = col(phosphor.dim); stroke.strokeWidth = 1f
        c.drawCircle(cx, cy, 7f, stroke)
        c.restore()
        /* peak LED */
        val hot = vu.needle > 0.72f
        p.color = if (hot) 0xFFFF4D4D.toInt() else Color.argb(46, 255, 77, 77)
        c.drawCircle(l.vu.right - 12f, l.vu.top + 11f, 2.6f, p)
    }

    private fun drawLog(c: Canvas, l: TerminalLayout, now: Long) {
        val log = game.log
        val r = l.log
        val y0 = r.top + 30f
        val lh = 18f
        txt.textSize = 8.5f; txt.textAlign = Paint.Align.LEFT
        val maxW = r.width() - 16f
        val start = max(0, log.size - 5)
        for (i in start until log.size) {
            val line = log[i]
            val row = i - start
            val age = log.size - 1 - i
            txt.color = when (line.kind) {
                LogLine.Kind.WARN -> 0xFFFF6B7A.toInt()
                LogLine.Kind.OK -> col(phosphor.bright)
                LogLine.Kind.INFO -> main
            }
            txt.alpha = (255 * max(0.35f, 1 - age * 0.16f)).toInt()
            /* a fresh line types itself out */
            val typed = ((now - line.at) / 300f).coerceIn(0f, 1f)
            var s = "> " + line.text.substring(0, (line.text.length * typed).toInt())
            /* long lines wrap into a second row would push the log out of its box: shorten them */
            while (txt.measureText(s) > maxW && s.length > 4) s = s.dropLast(1)
            c.drawText(s, r.left + 8f, y0 + row * lh, txt)
        }
        txt.alpha = 255
        if (frameT % 40 < 24 && log.isNotEmpty()) {
            val lastRow = log.size - start - 1
            val last = "> " + log.last().text
            val w = min(txt.measureText(last) + 3f, maxW)
            p.color = main
            c.drawRect(r.left + 8f + w, y0 + lastRow * lh - 7f, r.left + 13f + w, y0 + lastRow * lh + 1f, p)
        }
    }

    private fun drawLocks(c: Canvas, l: TerminalLayout) {
        for (lock in game.engine.locks) {
            val px = l.cellX(lock.x.toFloat())
            val py = l.cellY(lock.y.toFloat())
            val cs = l.cell
            val rect = RectF(px, py, px + cs, py + cs)
            val blink = frameT % 40 < 28
            c.save()
            c.clipPath(Path().apply { addRoundRect(rect, 7f, 7f, Path.Direction.CW) })
            p.color = if (blink) Color.argb(56, 255, 40, 70) else Color.argb(31, 255, 40, 70)
            c.drawRect(rect, p)
            stroke.color = Color.argb(89, 255, 77, 109); stroke.strokeWidth = 3f
            var k = -cs
            while (k < cs * 2) { c.drawLine(px + k, py + cs, px + k + cs, py, stroke); k += 14f }
            c.restore()
            stroke.color = 0xFFFF4D6D.toInt(); stroke.strokeWidth = 2f
            c.drawRoundRect(RectF(px + 1, py + 1, px + cs - 1, py + cs - 1), 7f, 7f, stroke)
            /* padlock */
            val cx = px + cs / 2
            val cy = py + cs / 2 + 2
            p.color = 0xFFFF4D6D.toInt()
            c.drawRoundRect(RectF(cx - 11, cy - 4, cx + 11, cy + 13), 3f, 3f, p)
            stroke.color = 0xFFFF8A9A.toInt(); stroke.strokeWidth = 3.5f
            c.drawArc(RectF(cx - 7, cy - 13, cx + 7, cy + 1), 180f, 180f, false, stroke)
            p.color = 0xFF2A0008.toInt()
            c.drawCircle(cx, cy + 3, 2.6f, p)
            c.drawRect(cx - 1.2f, cy + 3, cx + 1.2f, cy + 8, p)
            txt.textSize = 7.5f; txt.color = 0xFFFFD6D9.toInt(); txt.textAlign = Paint.Align.CENTER
            c.drawText("NEXUS-9 LOCK ${lock.turns}", cx, py + cs - 6f, txt)
            txt.textAlign = Paint.Align.LEFT
        }
    }

    private fun easeOut(t: Float) = 1 - (1 - t).pow(3)

    private fun drawTiles(c: Canvas, l: TerminalLayout, now: Long) {
        val prog = slide.progress(now)
        if (prog >= 1f && slide.active) slide.active = false
        val k = easeOut(prog)
        if (prog < 1f) for (g in slide.ghosts) {
            drawTileAt(c, l, g, g.px + (g.x - g.px) * k, g.py + (g.y - g.py) * k, 1f, 1f)
        }
        game.engine.eachTile { t ->
            if (t.mergedFrom != null && prog < 1f && slide.active) return@eachTile
            val gx = if (slide.active) t.px + (t.x - t.px) * k else t.x.toFloat()
            val gy = if (slide.active) t.py + (t.y - t.py) * k else t.y.toFloat()
            var scale = 1f
            var alpha = 1f
            appears[t.id]?.let { at ->
                val a = (now - at) / SlideAnim.APPEAR_MS
                if (a < 0) return@eachTile
                if (a >= 1) appears.remove(t.id) else { scale = 0.2f + 0.8f * easeOut(a); alpha = 0.6f + 0.4f * a }
            }
            pops[t.id]?.let { at ->
                val a = (now - at) / SlideAnim.POP_MS
                if (a >= 1) pops.remove(t.id) else if (a >= 0) scale = 1 + 0.22f * sin(a * PI.toFloat())
            }
            drawTileAt(c, l, t, gx, gy, scale, alpha)
        }
    }

    private fun drawTileAt(c: Canvas, l: TerminalLayout, t: Tile, gx: Float, gy: Float, scale: Float, alpha: Float) {
        val size = l.cell * scale
        val px = l.cellX(gx) + (l.cell - size) / 2
        val py = l.cellY(gy) + (l.cell - size) / 2
        val dst = RectF(px, py, px + size, py + size)
        val spritePx = (l.cell * l.s).roundToInt().coerceAtLeast(8)
        p.alpha = (alpha * 255).toInt()
        if (t.corrupt) {
            val fl = 0.7f + 0.3f * sin(frameT * 0.25f)
            p.alpha = (alpha * fl * 255).toInt()
            c.drawBitmap(sprites.glow(0xFFFF2A44.toInt()), null, RectF(px - 12, py - 12, px + size + 12, py + size + 12), p)
            p.alpha = (alpha * 255).toInt()
            c.drawBitmap(sprites.corrupt(spritePx), null, dst, p)
            glowText(c, t.value.toString(), px + size * 0.62f, py + size * 0.58f + 8f * scale * l.cell / 72f,
                0xFFFFD6D9.toInt(), 24f * scale * l.cell / 72f, 6f, Paint.Align.CENTER)
            if (frameT % 7 == 0 && effectsEnabled) { p.color = Color.argb(46, 255, 255, 255); c.drawRect(px, py + rnd.nextFloat() * size, px + size, py + rnd.nextFloat() * size + 2f, p) }
            p.alpha = 255
            return
        }
        val d = signal(t.value)
        if (t.value >= 64) {
            p.alpha = (alpha * (if (t.value >= 2048) 0.9f else 0.55f) * 255).toInt()
            val gs = if (t.value >= 1024) 20f else 12f
            c.drawBitmap(sprites.glow(d.col2.toInt()), null, RectF(px - gs, py - gs, px + size + gs, py + size + gs), p)
            p.alpha = (alpha * 255).toInt()
        }
        c.drawBitmap(sprites.tile(t.value, spritePx), null, dst, p)
        /* animated details */
        val s = scale * l.cell / 72f
        when {
            t.value == 4 -> {
                val on = (frameT / 24 + t.id) % 2 == 0
                p.color = if (on) 0xFFD8FFE4.toInt() else Color.argb(64, 216, 255, 228)
                c.drawCircle(px + 14 * s, py + 13 * s, 2.8f * s, p)
                if (on) { p.alpha = 204; c.drawBitmap(sprites.glow(0xFFD8FFE4.toInt()), null, RectF(px + 4 * s, py + 3 * s, px + 24 * s, py + 23 * s), p) }
            }
            t.value == 16 -> {
                val na = PI * (1.15 + 0.7 * (0.5 + 0.5 * sin(frameT * 0.08 + t.id)))
                stroke.color = 0xFFFFF3CC.toInt(); stroke.strokeWidth = 1.2f * s
                c.drawLine(px + 17 * s, py + 18 * s, px + 17 * s + cos(na).toFloat() * 8 * s, py + 18 * s + sin(na).toFloat() * 8 * s, stroke)
            }
            t.value == 512 -> {
                val f = 0.5f + 0.5f * sin(frameT * 0.3f + t.id * 2)
                p.alpha = ((0.3f + f * 0.5f) * alpha * 255).toInt()
                c.drawBitmap(sprites.glow(0xFFFFB86B.toInt()), null, RectF(px + 8 * s, py + s, px + 28 * s, py + 21 * s), p)
            }
            t.value == 1024 -> {
                val ra = ((frameT * 0.07 + t.id) % (PI * 2)).toFloat()
                stroke.color = 0xFFE4ECFF.toInt(); stroke.strokeWidth = 1.3f * s
                val cx = px + 15 * s; val cy = py + 13 * s
                c.drawLine(cx, cy, cx + cos(ra) * 6.5f * s, cy + sin(ra) * 6.5f * s, stroke)
                p.color = Color.argb(89, 228, 236, 255)
                c.drawArc(RectF(cx - 6.5f * s, cy - 6.5f * s, cx + 6.5f * s, cy + 6.5f * s), Math.toDegrees(ra - 0.9).toFloat(), 51.6f, true, p)
            }
            t.value >= 2048 -> if (frameT % 5 == 0 || pops.containsKey(t.id)) {
                stroke.color = if (t.value == 2048) Color.WHITE else d.col2.toInt(); stroke.strokeWidth = 1.5f
                stroke.setShadowLayer(8f, 0f, 0f, stroke.color)
                repeat(3) {
                    var bx = px + rnd.nextFloat() * size; var by = py + rnd.nextFloat() * size
                    val path = Path().apply { moveTo(bx, by) }
                    repeat(4) { bx += (rnd.nextFloat() - 0.5f) * 16; by += (rnd.nextFloat() - 0.5f) * 16; path.lineTo(bx, by) }
                    c.drawPath(path, stroke)
                }
                stroke.clearShadowLayer()
            }
        }
        p.alpha = 255
    }

    private fun drawEffects(c: Canvas, l: TerminalLayout) {
        val gr = l.grid
        c.save()
        c.clipRect(gr)
        /* power surge across the matrix */
        if (surgeDir >= 0) {
            val pr = surgeT / 14f
            val band = 46f
            val a = withAlpha(main, 0.26f * (1 - pr))
            val z = withAlpha(main, 0f)
            if (surgeDir == Dir.UP || surgeDir == Dir.DOWN) {
                val y = if (surgeDir == Dir.UP) gr.bottom - pr * (gr.height() + band) else gr.top - band + pr * (gr.height() + band)
                p.shader = LinearGradient(0f, y, 0f, y + band, intArrayOf(z, a, z), null, Shader.TileMode.CLAMP)
                c.drawRect(gr.left, y, gr.right, y + band, p)
            } else {
                val x = if (surgeDir == Dir.LEFT) gr.right - pr * (gr.width() + band) else gr.left - band + pr * (gr.width() + band)
                p.shader = LinearGradient(x, 0f, x + band, 0f, intArrayOf(z, a, z), null, Shader.TileMode.CLAMP)
                c.drawRect(x, gr.top, x + band, gr.bottom, p)
            }
            p.shader = null
        }
        /* frequency rings on fusions, with a little waveform riding them */
        for (r in rings) {
            val a = r.life / 30f
            stroke.color = withAlpha(r.color, a * 0.9f); stroke.strokeWidth = 2.5f * a + 0.5f
            c.drawCircle(r.x, r.y, r.r, stroke)
            stroke.color = withAlpha(Color.WHITE, a * 0.6f); stroke.strokeWidth = 1f
            val path = Path()
            for (i in 0..40) {
                val ang = i / 40.0 * PI * 2
                val rr = r.r + sin(ang * 8 + frameT * 0.5).toFloat() * 3 * a
                val xx = r.x + cos(ang).toFloat() * rr
                val yy = r.y + sin(ang).toFloat() * rr
                if (i == 0) path.moveTo(xx, yy) else path.lineTo(xx, yy)
            }
            c.drawPath(path, stroke)
        }
        /* sparks */
        for (q in particles) {
            p.color = q.color
            p.alpha = (255 * (q.life / 20f).coerceIn(0f, 1f)).toInt()
            c.drawRect(q.x - 1, q.y - 1, q.x + 1.4f, q.y + 1.4f, p)
        }
        p.alpha = 255
        if (flash > 0.02f) { p.color = withAlpha(Color.WHITE, flash * 0.18f); c.drawRect(gr, p) }
        c.restore()
        /* floaters */
        for (f in floaters) {
            val a = min(1f, f.life / 30f)
            disp.textAlign = Paint.Align.CENTER
            disp.alpha = (a * 255).toInt()
            glowText(c, f.text, gr.centerX(), f.y, f.color, if (f.big) 12f else 9f, 10f, Paint.Align.CENTER, a)
        }
        disp.alpha = 255
    }

    /**
     * The Override and the lost signal. The browser strobes at 10 Hz; here
     * the screen pulses twice in a second, under the three flashes per second
     * that photosensitive players can take.
     */
    private fun drawEndings(c: Canvas, l: TerminalLayout, now: Long) {
        val gr = l.grid
        if (winAt > 0) {
            val t = now - winAt
            if (t < 1300) {
                val pulse = if (t < 450) 1f - t / 450f else if (t in 500..950) 0.6f * (1f - (t - 500) / 450f) else 0f
                if (pulse > 0 && effectsEnabled) {
                    p.color = withAlpha(Color.WHITE, 0.75f * pulse)
                    c.drawRect(-l.ox / l.s, -l.oy / l.s, (width - l.ox) / l.s, (height - l.oy) / l.s, p)
                }
                if (t > 350) glowText(c, if (game.mode == Mode.SECTOR) "SECTOR CLEAR" else "OVERRIDE", gr.centerX(), gr.centerY() + 10f, Color.WHITE,
                    if (game.mode == Mode.SECTOR) 24f else 30f, 20f, Paint.Align.CENTER)
            }
        }
        if (lostAt > 0) {
            val t = now - lostAt
            /* the picture collapses into static */
            val density = if (t < 650) t / 650f else max(0.18f, 1 - (t - 650) / 1800f)
            val n = ((if (effectsEnabled) 260 else 60) * density).toInt()
            c.save()
            c.clipRect(gr)
            repeat(n) {
                val v = rnd.nextInt(200)
                p.color = Color.argb((64 + rnd.nextInt(100)), v, v, v)
                val x = gr.left + rnd.nextFloat() * gr.width()
                val y = gr.top + rnd.nextFloat() * gr.height()
                c.drawRect(x, y, x + 2 + rnd.nextFloat() * 10, y + 1 + rnd.nextFloat() * 3, p)
            }
            c.restore()
            if (t in 300..1600 && (t / 330) % 2 == 0L) {
                glowText(c, if (game.engine.endReason == nl.lloydstellar.override.engine.EndReason.OUT_OF_MOVES) "WINDOW CLOSED" else "SIGNAL LOST",
                    gr.centerX(), gr.centerY(), 0xFFFF2A44.toInt(), 26f, 18f, Paint.Align.CENTER)
            }
            if (t > 1500 && game.phase == Phase.OVER) game.showResults()
        }
        if (rollbackAt > 0) {
            val t = now - rollbackAt
            if (t < 380) {
                /* the tape rewinds: a band of light runs up the matrix */
                val y = gr.bottom - (t / 380f) * (gr.height() + 40f)
                p.shader = LinearGradient(0f, y, 0f, y + 40f, intArrayOf(withAlpha(main, 0f), withAlpha(main, 0.35f), withAlpha(main, 0f)), null, Shader.TileMode.CLAMP)
                c.save(); c.clipRect(gr); c.drawRect(gr.left, y, gr.right, y + 40f, p); c.restore()
                p.shader = null
            } else rollbackAt = 0
        }
    }

    /* interference: stray lines and a slow band of light */
    private fun drawNoise(c: Canvas) {
        val n = if (noiseBurst > 0) 3 + noiseBurst / 8 else if (rnd.nextFloat() < 0.06f) 1 else 0
        repeat(n) {
            val y = rnd.nextFloat() * height
            p.color = Color.argb((10 + rnd.nextInt(26)), 255, 255, 255)
            c.drawRect(0f, y, width.toFloat(), y + 1 + rnd.nextFloat() * 6f, p)
        }
        if (rnd.nextFloat() < 0.012f) {
            p.color = withAlpha(main, 0.05f)
            val by = rnd.nextFloat() * height * 0.9f
            c.drawRect(0f, by, width.toFloat(), by + height * (0.04f + rnd.nextFloat() * 0.06f), p)
        }
    }

    private fun dispText(c: Canvas, s: String, x: Float, y: Float, color: Int, size: Float) {
        disp.textAlign = Paint.Align.LEFT
        disp.textSize = size; disp.color = color
        c.drawText(s, x, y, disp)
    }

    private fun glowText(c: Canvas, s: String, x: Float, y: Float, color: Int, size: Float, glow: Float,
                         align: Paint.Align = Paint.Align.LEFT, alpha: Float = 1f) {
        disp.textAlign = align
        disp.textSize = size
        disp.color = color
        disp.alpha = (alpha * 255).toInt()
        disp.setShadowLayer(glow, 0f, 0f, withAlpha(color, alpha))
        c.drawText(s, x, y, disp)
        disp.clearShadowLayer()
        disp.alpha = 255
    }

    private fun fmtTime(sec: Int) = "%02d:%02d".format(java.util.Locale.ENGLISH, (sec / 60) % 100, sec % 60)

    /* ---------------- input ---------------- */

    /** A swipe, a key or an accessibility action. Never dropped while a slide is running. */
    fun swipe(dir: Int) {
        if (game.phase != Phase.PLAYING) return
        if (slide.progress(SystemClock.uptimeMillis()) < 0.5f) { pending = dir; return }
        doMove(dir)
    }

    private fun doMove(dir: Int) {
        slide.active = false
        val r = game.move(dir)
        if (r == null) {
            Sfx.blocked()
            Haptics.blocked(this)
            if (effectsEnabled) shake = max(shake, 2f)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = e.x; downY = e.y; tracking = true; fired = false }
            MotionEvent.ACTION_MOVE -> if (tracking && !fired) {
                val dx = e.x - downX; val dy = e.y - downY
                val ax = abs(dx); val ay = abs(dy)
                /* commit as soon as the swipe is clearly long and clearly straight */
                if (max(ax, ay) >= swipeMin && max(ax, ay) >= 1.4f * min(ax, ay)) {
                    fired = true
                    swipe(if (ax > ay) (if (dx > 0) Dir.RIGHT else Dir.LEFT) else (if (dy > 0) Dir.DOWN else Dir.UP))
                }
            }
            MotionEvent.ACTION_UP -> {
                if (tracking && !fired) {
                    val dx = e.x - downX; val dy = e.y - downY
                    if (max(abs(dx), abs(dy)) >= swipeMin * 0.8f) {
                        swipe(if (abs(dx) > abs(dy)) (if (dx > 0) Dir.RIGHT else Dir.LEFT) else (if (dy > 0) Dir.DOWN else Dir.UP))
                    } else performClick()
                }
                tracking = false
            }
            MotionEvent.ACTION_CANCEL -> tracking = false
        }
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val dir = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_K -> Dir.UP
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_L -> Dir.RIGHT
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_J -> Dir.DOWN
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_H -> Dir.LEFT
            else -> -1
        }
        if (dir >= 0) { if (event.repeatCount == 0) swipe(dir); return true }
        when (keyCode) {
            KeyEvent.KEYCODE_U, KeyEvent.KEYCODE_Z -> { game.rollback(); return true }
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_SPACE -> if (game.phase == Phase.WON) { game.continueEndless(); return true }
            KeyEvent.KEYCODE_P -> { if (game.phase == Phase.PLAYING) game.pause() else game.resume(); return true }
        }
        return super.onKeyDown(keyCode, event)
    }

    /* ---------------- accessibility ---------------- */

    /** The board in words, for TalkBack: one row per sentence. */
    private fun describe() {
        val e = game.engine
        val sb = StringBuilder("Matrix, ${e.score} hertz, ${e.moves} moves, trace ${e.trace} percent. ")
        for (y in 0 until e.size) {
            sb.append("Row ${y + 1}: ")
            sb.append((0 until e.size).joinToString(", ") { x ->
                val t = e.tileAt(x, y)
                when {
                    e.isDead(x, y) -> "dead"
                    t == null && e.lockAt(x, y) != null -> "locked"
                    t == null -> "empty"
                    t.corrupt -> "${t.value} corrupted"
                    e.lockAt(x, y) != null -> "${t.value} locked"
                    else -> t.value.toString()
                }
            })
            sb.append(". ")
        }
        contentDescription = sb.toString()
        stateDescription = "${e.score} hertz, highest signal ${e.maxTile}"
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopLoop()
    }

    companion object {
        /** False when the system's "remove animations" is on. */
        fun systemAnimations() = ValueAnimator.areAnimatorsEnabled()
    }
}
