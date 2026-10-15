package nl.lloydstellar.override

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import nl.lloydstellar.override.audio.Haptics
import nl.lloydstellar.override.audio.Music
import nl.lloydstellar.override.audio.Sfx
import nl.lloydstellar.override.data.Achievement
import nl.lloydstellar.override.data.FILES
import nl.lloydstellar.override.data.PHOSPHORS
import nl.lloydstellar.override.data.Run
import nl.lloydstellar.override.data.Store
import nl.lloydstellar.override.data.signal
import nl.lloydstellar.override.engine.CLASSIC
import nl.lloydstellar.override.engine.EndReason
import nl.lloydstellar.override.engine.Engine
import nl.lloydstellar.override.engine.GameState
import nl.lloydstellar.override.engine.MoveResult
import nl.lloydstellar.override.engine.Rng
import nl.lloydstellar.override.engine.SECTORS
import nl.lloydstellar.override.engine.Sector
import nl.lloydstellar.override.engine.boardJson
import nl.lloydstellar.override.engine.Directives
import nl.lloydstellar.override.engine.threat
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

enum class Mode { CLASSIC, SECTOR, DAILY, WEEKLY, TUTORIAL }

/**
 * PLAYING and PAUSED are what they say. WON is the Override (Classic: go on
 * or stop; a sector: cleared). OVER is the moment the signal is lost, while
 * the picture collapses; RESULTS follows when the animation is done.
 */
enum class Phase { PLAYING, PAUSED, WON, OVER, RESULTS }

/** A line in the SYS.LOG panel. */
class LogLine(val text: String, val kind: Kind, val at: Long) {
    enum class Kind { INFO, OK, WARN }
}

/** Things the terminal draws once: the GameView picks them up every frame. */
sealed class Fx {
    class Moved(val r: MoveResult) : Fx()
    /** The board changed without a slide (new game, rollback, restore). */
    data object Snap : Fx()
    class Floater(val text: String, val color: Long, val big: Boolean) : Fx()
    data object Win : Fx()
    data object Lost : Fx()
    data object Rollback : Fx()
}

class Result(
    val mode: Mode,
    val sector: Sector?,
    val score: Int,
    val tile: Int,
    val moves: Int,
    val seconds: Int,
    val won: Boolean,
    val reason: EndReason,
    /** Classic: rank among your runs; 0 when outside the top 20. */
    val rank: Int,
    val newBest: Boolean,
    /** Sector: bars earned in this run, and whether that improved the record. */
    val bars: Int,
    val barsImproved: Boolean,
    val locks: Int,
    val nodes: Int,
)

/**
 * The glue between the engine and the app: sound, haptics, the soundtrack,
 * records, achievements, rollbacks, one-time hints and the state the
 * Compose overlays show. The GameView draws from [engine] and [log] and
 * plays what it finds in [fx].
 */
class GameController(private val store: Store) {
    val engine = Engine()
    var mode by mutableStateOf(Mode.CLASSIC); private set
    /** The sector or directive being played, null in Classic. */
    var sector by mutableStateOf<Sector?>(null); private set
    /** Threat level of a Classic session: -1 calm, 0 standard, 1..8. */
    var level by mutableIntStateOf(0); private set
    /** The sector assist: two extra rollback charges, offered after three lost attempts. */
    var assisted by mutableStateOf(false); private set
    /* the day a directive belongs to, to rebuild it from a save */
    private var directiveDate: LocalDate? = null

    var phase by mutableStateOf(Phase.PLAYING); private set
    /* mirrored for Compose; the GameView reads the engine directly */
    var score by mutableIntStateOf(0); private set
    var maxTile by mutableIntStateOf(0); private set
    var charges by mutableIntStateOf(0); private set
    var result by mutableStateOf<Result?>(null); private set
    val toasts = mutableStateListOf<Achievement>()
    /** A one-time hint for new players, or null. */
    var hint by mutableStateOf<String?>(null); private set
    private var hintMoves = 0

    val log = ArrayList<LogLine>()
    val fx = ArrayDeque<Fx>()

    /* the rollback buffer: the board before each of the last moves */
    private val history = ArrayDeque<JSONObject>()
    /* mirrors history.size so Compose sees when a rollback becomes possible */
    private var historySize by mutableIntStateOf(0)
    /* tile values that already paid out a rollback charge in this game */
    private val earned = HashSet<Int>()
    var rollbacksUsed = 0; private set

    private var bestAtStart = 0
    private var elapsedMs = 0L
    private var resumedAt = 0L
    /** A game exists that is worth saving. */
    private var active = false
    /** The game ended but its records are not written yet (a rollback may still undo the end). */
    private var pendingEnd = false

    val seconds: Int get() = ((elapsedMs + if (phase == Phase.PLAYING && resumedAt > 0) SystemClock.elapsedRealtime() - resumedAt else 0) / 1000).toInt()
    val best: Int get() = if (mode == Mode.CLASSIC) maxOf(store.bestFor(level), score) else 0
    val beatingRecord: Boolean get() = mode == Mode.CLASSIC && bestAtStart in 1 until score
    val rules get() = sector?.rules ?: threat(level).rules
    /** The most rollback charges this game can hold. */
    val maxCharges: Int get() = rules.rollbackMax + if (assisted) ASSIST_CHARGES else 0

    /* ---------------- starting and stopping ---------------- */

    fun newClassic(level: Int = store.lastLevel) {
        this.level = level
        store.chooseLevel(level)
        assisted = false
        start(Mode.CLASSIC, null)
    }

    fun newSector(s: Sector, assist: Boolean) {
        assisted = assist
        start(Mode.SECTOR, s)
    }

    /** A Daily or Weekly Directive of [date]. */
    fun newDirective(s: Sector, date: LocalDate) {
        assisted = false
        directiveDate = date
        start(if (s.kind == Sector.Kind.WEEKLY) Mode.WEEKLY else Mode.DAILY, s)
    }

    /* ---------------- the tutorial ---------------- */

    /**
     * 0: fuse two 2s, 1: fuse two 4s into an 8, 2: NEXUS-9 and the trace, read
     * and go. The step moves on when the player makes the fusion; the next
     * board is loaded by the screen once the slide has landed.
     */
    var tutorialStep by mutableIntStateOf(0); private set
    /** The step the board on screen belongs to. */
    private var tutorialBoard = -1

    fun startTutorial() {
        finishPending()
        mode = Mode.TUTORIAL
        sector = null
        history.clear(); earned.clear(); historySize = 0
        charges = 0; rollbacksUsed = 0
        result = null; toasts.clear(); log.clear()
        hint = null
        active = false
        pendingEnd = false
        phase = Phase.PLAYING
        tutorialStep = 0
        tutorialBoard = -1
        loadTutorialBoard()
        Sfx.jackIn()
        updateMusic()
    }

    /** Shows the board of the current step (called by the screen after the slide of the last fusion). */
    fun loadTutorialBoard() {
        if (mode != Mode.TUTORIAL || tutorialBoard == tutorialStep) return
        tutorialBoard = tutorialStep
        engine.restore(TUTORIAL_BOARDS[tutorialStep], TUTORIAL_RULES)
        engine.settle()
        log.clear()
        when (tutorialStep) {
            0 -> logLine("TRAINING LINK ESTABLISHED.", LogLine.Kind.OK)
            1 -> logLine("FUSION CONFIRMED. KEEP GOING.", LogLine.Kind.OK)
            else -> { logLine("TRACE 100%: NEXUS-9 SEALED CELL B2 (5 TURNS)", LogLine.Kind.WARN); Sfx.lock() }
        }
        fx.add(Fx.Snap)
        sync()
    }

    private fun advanceTutorial(r: MoveResult) {
        val goal = if (tutorialStep == 0) 4 else 8
        if (tutorialStep < 2 && r.merges.any { it.value >= goal }) {
            tutorialStep++
            Sfx.unlock()
        }
    }
    fun restart() = start(mode, sector)

    private fun start(mode: Mode, sector: Sector?) {
        finishPending()
        this.mode = mode
        this.sector = sector
        engine.newGame(rules, sector?.seed?.let { Rng(it) } ?: Rng.seeded())
        history.clear(); earned.clear()
        historySize = 0
        charges = minOf(rules.rollbackStart + if (assisted) ASSIST_CHARGES else 0, maxCharges)
        rollbacksUsed = 0
        bestAtStart = if (mode == Mode.CLASSIC) store.bestFor(level) else 0
        elapsedMs = 0
        resumedAt = SystemClock.elapsedRealtime()
        result = null
        toasts.clear()
        log.clear()
        active = true
        pendingEnd = false
        phase = Phase.PLAYING
        if (sector != null) {
            logLine(when (sector.kind) {
                Sector.Kind.SECTOR -> "LINK ESTABLISHED. ${sector.code} ${sector.name} ONLINE."
                Sector.Kind.DAILY -> "LINK ESTABLISHED. DAILY DIRECTIVE."
                Sector.Kind.WEEKLY -> "LINK ESTABLISHED. WEEKLY DIRECTIVE."
            }, LogLine.Kind.OK)
            logLine("TARGET: ${sector.rules.target} BIT.", LogLine.Kind.INFO)
        } else {
            logLine("LINK ESTABLISHED. SECTOR 7 GRID ONLINE.", LogLine.Kind.OK)
            logLine(if (level == 0) "INJECT SIGNALS. TARGET: 2048 BIT." else "${threat(level).name}. TARGET: 2048 BIT.", LogLine.Kind.INFO)
        }
        if (assisted) logLine("ASSIST: ROLLBACK BUFFER PRELOADED [$charges]", LogLine.Kind.OK)
        Sfx.jackIn()
        store.countGame()
        award("first")
        if (store.games >= 25) award("games25")
        hint = if (!store.hintSeen("swipe")) "swipe" else null
        hintMoves = 0
        fx.add(Fx.Snap)
        sync()
        save()
    }

    /** Picks up the saved game of [mode]. Returns false when there is none. */
    fun continueSaved(mode: Mode): Boolean {
        if (mode == Mode.TUTORIAL) return false
        val o = slot(mode) ?: return false
        return try {
            val date = o.optString("date").takeIf { it.isNotEmpty() }?.let { LocalDate.parse(it) }
            val s = when (mode) {
                Mode.SECTOR -> SECTORS.first { it.number == o.getInt("sector") }
                Mode.DAILY -> Directives.daily(date!!)
                Mode.WEEKLY -> Directives.weekly(date!!)
                else -> null
            }
            finishPending()
            this.mode = mode
            this.sector = s
            this.level = o.optInt("level", 0)
            this.assisted = o.optBoolean("assist")
            this.directiveDate = date
            engine.restore(o.getJSONObject("engine"), rules)
            engine.settle()
            history.clear()
            val h = o.optJSONArray("history") ?: JSONArray()
            for (i in 0 until h.length()) history.addLast(h.getJSONObject(i))
            historySize = history.size
            earned.clear()
            val e = o.optJSONArray("earned") ?: JSONArray()
            for (i in 0 until e.length()) earned.add(e.getInt(i))
            charges = o.optInt("charges", rules.rollbackStart)
            rollbacksUsed = o.optInt("rollbacksUsed")
            elapsedMs = o.optLong("elapsed")
            bestAtStart = if (mode == Mode.CLASSIC) store.bestFor(level) else 0
            result = null
            toasts.clear()
            log.clear()
            logLine("SESSION RESTORED FROM LOCAL BUFFER.", LogLine.Kind.OK)
            active = true
            pendingEnd = false
            hint = null
            when (engine.state) {
                GameState.PLAYING -> phase = Phase.PAUSED
                GameState.WON -> { phase = Phase.WON; buildResult() }
                GameState.OVER -> { phase = Phase.RESULTS; pendingEnd = true; buildResult() }
            }
            resumedAt = 0
            fx.add(Fx.Snap)
            sync()
            true
        } catch (e: Exception) {
            android.util.Log.w("Override", "saved game unreadable", e)
            writeSlot(mode, null)
            false
        }
    }

    fun pause() {
        if (phase != Phase.PLAYING) return
        stopClock()
        phase = Phase.PAUSED
        save()
        updateMusic()
    }

    fun resume() {
        if (phase != Phase.PAUSED) return
        phase = Phase.PLAYING
        resumedAt = SystemClock.elapsedRealtime()
        updateMusic()
    }

    /** The app leaves the screen. */
    fun saveIfRunning() {
        if (!active) return
        if (phase == Phase.PLAYING) { stopClock(); phase = Phase.PAUSED }
        save()
        Music.hide()
    }

    /** Back to the menus: the game stays saved, a finished one is written to the records. */
    fun leave() {
        if (phase == Phase.PLAYING) stopClock()
        if (phase == Phase.PLAYING) phase = Phase.PAUSED
        finishPending()
        save()
        active = false
        Music.update(false, store.sound, store.music)
    }

    /** END SESSION: the operator closes the link. */
    fun endSession() {
        if (phase == Phase.OVER || phase == Phase.RESULTS) return
        stopClock()
        engine.abort()
        logLine("SESSION TERMINATED BY OPERATOR.", LogLine.Kind.INFO)
        pendingEnd = true
        finishPending()
        phase = Phase.RESULTS
        updateMusic()
    }

    private fun stopClock() {
        if (resumedAt > 0) elapsedMs += SystemClock.elapsedRealtime() - resumedAt
        resumedAt = 0
    }

    /* ---------------- moves ---------------- */

    /** Called by the GameView with a swipe. Returns what happened, or null if nothing moved. */
    fun move(dir: Int): MoveResult? {
        if (phase != Phase.PLAYING) return null
        if (mode == Mode.TUTORIAL && (tutorialStep != tutorialBoard || tutorialStep >= 2)) return null
        val before = engine.toJson()
        val r = engine.move(dir) ?: return null
        history.addLast(before)
        while (history.size > maxOf(1, maxCharges)) history.removeFirst()
        historySize = history.size
        fx.add(Fx.Moved(r))
        feedback(r)
        if (mode == Mode.TUTORIAL) advanceTutorial(r)
        sync()
        save()
        return r
    }

    private fun feedback(r: MoveResult) {
        if (hint == "swipe") dismissHint()
        Sfx.slide(r.dir)
        r.merges.forEachIndexed { i, m ->
            Sfx.merge(m.value, i)
            if (m.value >= 64) logLine("FUSION  ${m.value.toString().padStart(4, '0')}  ${signal(m.value).name}",
                if (m.value >= 512) LogLine.Kind.OK else LogLine.Kind.INFO)
        }
        when {
            r.fried.isNotEmpty() -> Haptics.emp()
            r.purges.isNotEmpty() -> Haptics.purge()
            r.merges.isNotEmpty() -> Haptics.merge(r.merges.maxOf { it.value })
            else -> Haptics.slide()
        }
        r.purges.forEach { (_, _, v) ->
            Sfx.purge()
            fx.add(Fx.Floater("NODE PURGED  +${v * 2} Hz", 0xFFFF8A9A, false))
            logLine("CORRUPTED NODE PURGED  +${v * 2} Hz", LogLine.Kind.OK)
        }
        r.fried.forEach { _ ->
            Sfx.emp()
            fx.add(Fx.Floater("EMP DISCHARGE  +64 Hz", 0xFFFFE64D, true))
            logLine("EMP CELL FRIED A CORRUPTED NODE  +64 Hz", LogLine.Kind.OK)
        }
        r.spawned?.let {
            if (it.corrupt) {
                Sfx.corrupt(); Haptics.corrupt()
                logLine("CORRUPTED NODE DETECTED  [${it.value}]", LogLine.Kind.WARN)
                showHint("corrupt")
            } else Sfx.spawn()
        }
        if (r.traceAfter >= 80 && r.traceBefore < 80 && r.lock == null) {
            Sfx.traceWarn(); Haptics.traceWarn()
            logLine("WARNING: TRACE LEVEL CRITICAL", LogLine.Kind.WARN)
        }
        if (r.traceAfter >= 50 && r.lock == null) showHint("trace")
        if (r.locksReleased > 0) logLine("NEXUS-9 LOCK RELEASED", LogLine.Kind.INFO)
        if (r.locksTimedOut) logLine("NEXUS-9 LOCKS TIMED OUT", LogLine.Kind.INFO)
        r.lock?.let { l ->
            Sfx.lock(); Haptics.lock()
            val cell = "${'A' + l.x}${l.y + 1}"
            fx.add(Fx.Floater("NEXUS-9 LOCKED CELL $cell", 0xFFFF4D6D, true))
            logLine("TRACE 100%: NEXUS-9 SEALED CELL $cell (${l.turns} TURNS)", LogLine.Kind.WARN)
            showHint("lock")
        }
        if (r.newMax > 0) onNewMax(r.newMax)
        if (engine.countCorrupt() > 0 && engine.tiles().any { !it.corrupt && it.value == 64 }) showHint("emp")

        /* achievements of a single move */
        if (r.purges.isNotEmpty()) award("purge")
        if (r.fried.isNotEmpty()) award("fry")
        if (r.fried.size >= 2) award("double")
        if (r.merges.size >= 4) award("multiplex")
        if (store.nodes + engine.purged + engine.fried >= 50) award("nodes50")
        if (engine.score >= 20000) award("hz20k")
        if (engine.locksSuffered >= 3 && engine.maxTile >= 512) award("siege")

        /* hints go away after a few moves */
        if (hint != null && ++hintMoves >= 6) dismissHint()

        when {
            r.wonNow && engine.state == GameState.WON -> onWin()
            r.ended != EndReason.NONE -> onLost(r.ended)
        }
    }

    private fun onNewMax(v: Int) {
        val d = signal(v)
        if (v >= 16) fx.add(Fx.Floater(d.name, d.col2, v >= 128))
        Music.level = Music.levelFor(v)
        when (v) {
            128 -> award("t128")
            512 -> award("t512")
            1024 -> award("t1024")
            4096 -> { award("t4096"); Sfx.speak("breach", 0.3) }
            8192 -> { award("t8192"); Sfx.speak("purge", 0.3) }
        }
        /* a rollback charge for every new big signal, once per value per game */
        if (v >= 256 && earned.add(v) && charges < maxCharges) {
            charges++
            logLine("ROLLBACK BUFFER CHARGED  [$charges/$maxCharges]", LogLine.Kind.OK)
            showHint("rollback")
        }
        if (store.reachTile(v)) {
            val news = PHOSPHORS.filter { it.req == v }.map { "PHOSPHOR: ${it.name.uppercase()}" } +
                FILES.filter { it.req == v }.map { it.title.replace(" // ", ": ") }
            if (news.isNotEmpty()) {
                Sfx.unlock()
                news.forEach { fx.add(Fx.Floater("DECRYPTED  $it", 0xFF4DFF9A, false)) }
                logLine("DECRYPTED: ${news.joinToString(", ")}", LogLine.Kind.OK)
            }
        }
    }

    private fun onWin() {
        stopClock()
        phase = Phase.WON
        fx.add(Fx.Win)
        Sfx.win(); Haptics.win()
        val s = sector
        logLine(if (s == null) "OVERRIDE CODE ASSEMBLED. CORE OVERLOAD." else "${s.rules.target}-BIT PULSE INJECTED. ${s.code} CORE OFFLINE.", LogLine.Kind.OK)
        if (s == null) {
            store.countOverride(engine.moves)
            store.winThreat(level)
            award("override")
            if (level >= 4) award("threat4")
            if (level >= 8) award("threat8")
            if (rollbacksUsed == 0) award("clean")
            if (engine.locksSuffered <= 3) award("cold")
            if (engine.wonAtMove < 1000) award("fast")
            Sfx.speak("override", 1.4)
            buildResult()
        } else {
            /* a sector is done at its target: write it down now */
            pendingEnd = true
            finishPending()
            Sfx.speak(if (s.number == SECTORS.size) "nexus" else "sector", 1.4)
        }
        updateMusic()
    }

    private fun onLost(reason: EndReason) {
        stopClock()
        phase = Phase.OVER
        fx.add(Fx.Lost)
        Sfx.signalLost(); Haptics.signalLost()
        logLine(if (reason == EndReason.OUT_OF_MOVES) "WINDOW CLOSED. UPLINK LOST." else "SIGNAL LOST. AI DETECTED.", LogLine.Kind.WARN)
        pendingEnd = true
        result = buildResult()
        updateMusic()
    }

    /** The collapse animation is over: show the results. */
    fun showResults() {
        if (phase == Phase.OVER) phase = Phase.RESULTS
    }

    /** After the Override in Classic: keep going towards 4096. */
    fun continueEndless() {
        if (phase != Phase.WON || mode != Mode.CLASSIC) return
        engine.continueEndless()
        if (engine.state == GameState.OVER) { onLost(EndReason.SIGNAL_LOST); return }
        phase = Phase.PLAYING
        resumedAt = SystemClock.elapsedRealtime()
        result = null
        logLine("ENDLESS MODE: GLOBAL PURGE. TARGET 4096.", LogLine.Kind.WARN)
        fx.add(Fx.Floater("GLOBAL PURGE INITIATED", 0xFFFF4DDB, true))
        save()
        updateMusic()
    }

    /* ---------------- rollback ---------------- */

    val canRollback: Boolean
        get() = charges > 0 && historySize > 0 &&
            (phase == Phase.PLAYING || ((phase == Phase.OVER || phase == Phase.RESULTS) && engine.endReason != EndReason.ABORTED))

    /** Undoes the last move, the spawn and the trace with it. Costs one charge. */
    fun rollback(): Boolean {
        if (!canRollback) return false
        val o = history.removeLast()
        historySize = history.size
        engine.restore(o, rules)
        engine.settle()
        charges--
        rollbacksUsed++
        store.countRollback()
        if (phase != Phase.PLAYING) {
            phase = Phase.PLAYING
            pendingEnd = false
            result = null
            resumedAt = SystemClock.elapsedRealtime()
        }
        store.markHint("rollback")
        if (hint == "rollback") hint = null
        Sfx.rollback(); Haptics.rollback()
        logLine("ROLLBACK: LAST INJECTION REVERSED  [$charges LEFT]", LogLine.Kind.OK)
        fx.add(Fx.Rollback)
        Music.level = Music.levelFor(engine.maxTile)
        sync()
        save()
        updateMusic()
        return true
    }

    /* ---------------- records ---------------- */

    /** Writes a finished game to the records, once. */
    private fun finishPending() {
        if (!pendingEnd) return
        pendingEnd = false
        val e = engine
        store.addStats(e.score, e.fusions, e.purged + e.fried, e.locksSuffered, e.moves, seconds)
        var rank = 0
        var bars = 0
        var improved = false
        val s = sector
        if (s == null) {
            rank = store.addRun(Run(e.score, e.maxTile, System.currentTimeMillis(), e.moves, seconds, e.won, level))
        } else if (e.state == GameState.WON) {
            bars = 1 + (if (e.moves <= s.par) 1 else 0) + (if (e.locksSuffered <= s.lockBudget) 1 else 0)
            if (s.kind == Sector.Kind.SECTOR) {
                improved = store.clearSector(s.number, bars, e.moves)
                if (SECTORS.all { store.bars[it.number - 1] > 0 }) award("sectors")
                if (SECTORS.all { store.bars[it.number - 1] == 3 }) award("bars")
            } else {
                improved = store.clearDirective(s.id, bars, e.moves)
                if (store.dailiesCleared >= 10) award("daily10")
                if (store.weekliesCleared >= 4) award("weekly4")
                val d = directiveDate
                if (s.kind == Sector.Kind.DAILY && d != null && store.monthComplete(d)) {
                    award("month")
                    monthDecrypted = d
                }
            }
        } else if (s.kind == Sector.Kind.SECTOR && e.endReason != EndReason.ABORTED) {
            store.failSector(s.number)
        }
        result = buildResult(rank, e.score > bestAtStart && e.score > 0 && s == null, bars, improved)
        writeSlot(mode, null)
        active = false
    }

    private fun buildResult(rank0: Int = 0, newBest0: Boolean = false, bars: Int = 0, improved: Boolean = false): Result {
        val e = engine
        /* before the run is written (a rollback may still undo the end), show where it would land */
        val provisional = pendingEnd && mode == Mode.CLASSIC
        val rank = if (provisional) (store.runs.count { it.score >= e.score } + 1).takeIf { it <= 20 } ?: 0 else rank0
        val newBest = if (provisional) e.score > bestAtStart && e.score > 0 else newBest0
        val r = Result(mode, sector, e.score, e.maxTile, e.moves, seconds, e.won, e.endReason, rank, newBest,
            bars, improved, e.locksSuffered, e.purged + e.fried)
        result = r
        return r
    }

    private fun save() {
        if (!active) return
        val o = JSONObject()
            .put("engine", engine.toJson())
            .put("history", JSONArray().apply { history.forEach { put(it) } })
            .put("earned", JSONArray().apply { earned.forEach { put(it) } })
            .put("charges", charges)
            .put("rollbacksUsed", rollbacksUsed)
            .put("elapsed", elapsedMs)
        o.put("level", level).put("assist", assisted)
        sector?.let { o.put("sector", it.number) }
        directiveDate?.let { if (mode == Mode.DAILY || mode == Mode.WEEKLY) o.put("date", it.toString()) }
        writeSlot(mode, o)
    }

    /* ---------------- small things ---------------- */

    private fun sync() {
        score = engine.score
        maxTile = engine.maxTile
        Music.level = Music.levelFor(engine.maxTile)
        updateMusic()
    }

    fun updateMusic() {
        val playing = (active || mode == Mode.TUTORIAL) && (phase == Phase.PLAYING || (phase == Phase.WON && mode == Mode.CLASSIC))
        Music.update(playing, store.sound, store.music)
    }

    fun logLine(text: String, kind: LogLine.Kind) {
        log.add(LogLine(text, kind, SystemClock.uptimeMillis()))
        while (log.size > 5) log.removeAt(0)
    }

    private fun showHint(id: String) {
        if (mode == Mode.TUTORIAL || store.hintSeen(id) || hint == id) return
        if (hint != null && hint != "swipe") return
        hint = id
        hintMoves = 0
    }

    fun dismissHint() {
        hint?.let { store.markHint(it) }
        hint = null
    }

    fun dismissToast(a: Achievement) { toasts.remove(a) }

    private fun award(id: String) {
        if (mode == Mode.TUTORIAL) return
        store.unlock(id)?.let {
            toasts.add(it)
            Sfx.hiscore()
            Haptics.success()
        }
    }

    /** Set when a daily completes a month; the screen shows the decrypted dispatch once. */
    var monthDecrypted by mutableStateOf<LocalDate?>(null)

    private fun slot(mode: Mode): JSONObject? = when (mode) {
        Mode.CLASSIC -> store.savedClassic
        Mode.SECTOR -> store.savedSector
        Mode.DAILY -> store.savedDaily
        Mode.WEEKLY -> store.savedWeekly
        Mode.TUTORIAL -> null
    }

    private fun writeSlot(mode: Mode, o: JSONObject?) = when (mode) {
        Mode.CLASSIC -> store.saveClassic(o)
        Mode.SECTOR -> store.saveSector(o)
        Mode.DAILY -> store.saveDaily(o)
        Mode.WEEKLY -> store.saveWeekly(o)
        Mode.TUTORIAL -> Unit
    }

    companion object {
        /** Extra rollback charges of the sector assist. */
        const val ASSIST_CHARGES = 2
        /* no trace, no nodes, only 2s: nothing distracts from the slide */
        private val TUTORIAL_RULES = CLASSIC.copy(traceEmpty = 0, fourChance = 0.0, corruptFrom = 1 shl 30, endless = false, target = 1 shl 20)
        private val TUTORIAL_BOARDS = listOf(
            boardJson(listOf(listOf(0, 0, 0, 0), listOf(0, 0, 0, 0), listOf(2, 0, 0, 2), listOf(0, 0, 0, 0))),
            boardJson(listOf(listOf(0, 0, 0, 0), listOf(0, 0, 2, 0), listOf(4, 0, 4, 0), listOf(8, 0, 0, 0)), score = 4, moves = 1),
            boardJson(
                listOf(listOf(2, 4, 8, 2), listOf(0, 0, 16, 4), listOf(0, 2, 32, 8), listOf(0, 0, 4, 2)),
                score = 316, moves = 42, trace = 100, locks = listOf(Triple(1, 1, 5))
            ),
        )
    }
}
