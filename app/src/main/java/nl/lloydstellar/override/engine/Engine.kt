package nl.lloydstellar.override.engine

import org.json.JSONArray
import org.json.JSONObject

/** Directions as in override.js: 0 up, 1 right, 2 down, 3 left. */
object Dir {
    const val UP = 0
    const val RIGHT = 1
    const val DOWN = 2
    const val LEFT = 3
    val dx = intArrayOf(0, 1, 0, -1)
    val dy = intArrayOf(-1, 0, 1, 0)
}

enum class GameState { PLAYING, WON, OVER }

/** Why a game ended. */
enum class EndReason { NONE, SIGNAL_LOST, OUT_OF_MOVES, ABORTED }

/**
 * What a tile is. SIGNAL and NODE are the browser game's; the rest is new in
 * the app and only spawns when [Rules.specials] allows it.
 */
enum class Kind {
    /** An ordinary signal: equal ones fuse. */
    SIGNAL,
    /** Corrupted Node: never fuses upward; an equal signal purges both, a 64 next to it fries it. */
    NODE,
    /** Trace Beacon: like a node, but when its counter runs out it transmits (trace to 100 %) and becomes a node. */
    BEACON,
    /** Worm: like a node, and every few moves it turns the newest signal into a node. */
    WORM,
    /** Cased Signal: a signal in a lead case; equal signals crack the case first, the last hit fuses (double score). */
    CASED,
    /** Patch Cable: fuses with any signal up to [Engine.PATCH_MAX] and doubles it. */
    PATCH,
    /** Surge Capacitor: slides but never fuses; at 0 it discharges and clears the small stuff in the four cells around it. */
    CAPACITOR,
    /** Static Wall: does not slide; every fusion next to it wears it down. */
    WALL;

    /** Cleared by an equal signal or an EMP Cell, and feeds the trace while on the board. */
    val hostile get() = this == NODE || this == BEACON || this == WORM
}

class Tile(val id: Int, var x: Int, var y: Int, val value: Int, val kind: Kind = Kind.SIGNAL, var counter: Int = 0) {
    constructor(id: Int, x: Int, y: Int, value: Int, corrupt: Boolean) : this(id, x, y, value, if (corrupt) Kind.NODE else Kind.SIGNAL)

    val corrupt get() = kind == Kind.NODE
    val signal get() = kind == Kind.SIGNAL
    /* where the tile was before the last move, for the slide animation */
    var px = x
    var py = y
    /* the two tiles this one was fused from in the last move */
    var mergedFrom: Pair<Tile, Tile>? = null
    /* spawned in the last move */
    var fresh = false
    /* a case cracked by this move: one hit per move */
    var hit = false
}

class Lock(val x: Int, val y: Int, var turns: Int) {
    /** A pin lock holds a signal in place. */
    var pinned = false
}

/** What one move did, for the animation, the sound and the achievements. */
class MoveResult(val dir: Int) {
    /** Tiles that slid into a fusion or a purge and disappear when the slide ends. */
    val ghosts = ArrayList<Tile>()
    val merges = ArrayList<Tile>()
    /** Cells where a Corrupted Node and an equal signal cancelled out, with the value. */
    val purges = ArrayList<Triple<Int, Int, Int>>()
    /** What each purge took out: a node, a beacon or a worm. */
    val purgedKinds = ArrayList<Kind>()
    /** Corrupted Nodes that an EMP Cell fried, and where that EMP Cell (the 64) sits. */
    val fried = ArrayList<Tile>()
    val friedBy = ArrayList<Pair<Int, Int>>()
    /** Cased Signals whose case cracked (the case is still on). */
    val cracks = ArrayList<Tile>()
    /** Fusions that went through a Patch Cable. */
    val patched = ArrayList<Tile>()
    /** Surge Capacitors that discharged: where, and the cells they cleared. */
    val discharges = ArrayList<Pair<Pair<Int, Int>, List<Pair<Int, Int>>>>()
    /** Trace Beacons that transmitted (they are Corrupted Nodes now). */
    val transmitted = ArrayList<Tile>()
    /** A worm turned this signal into a Corrupted Node. */
    var infected: Tile? = null
    /** Static Walls worn down (still standing) and broken (gone). */
    val wallsHit = ArrayList<Tile>()
    val wallsBroken = ArrayList<Tile>()
    /** The spawn was a supply drop. */
    var supply = false
    /** Locks that fusions next to them cracked open. */
    var locksCracked = 0
    var spawned: Tile? = null
    var lock: Lock? = null
    var locksReleased = 0
    var locksTimedOut = false
    var traceBefore = 0
    var traceAfter = 0
    var scoreGained = 0
    /** The highest tile went up to this value in this move, or 0. */
    var newMax = 0
    var wonNow = false
    var ended = EndReason.NONE
    val fusions get() = merges.size + purges.size + cracks.size
}

/**
 * The rules of 2048 Override. The move, the traversal order, the farthest
 * position search and the one-fusion-per-tile rule follow Gabriele Cirulli's
 * MIT-licensed 2048 (GameManager.move), by way of the browser game's
 * override.js, line for line. New in override.js and kept here: Corrupted
 * Nodes, the EMP Cell, the AI Trace Level and NEXUS-9 locks. New in the app:
 * [Rules] for the sectors (other sizes, dead cells, a move limit), the
 * special tiles of [Kind], growing and crackable locks, pin locks, and a
 * seeded [Rng] so a game can be saved and rolled back exactly. None of that
 * draws a random number unless its rule is switched on, so with [WEB] the
 * engine plays the browser game number for number.
 *
 * Plain Kotlin without Android code, so the parity test runs on the JVM.
 */
class Engine(rules: Rules = CLASSIC) {
    var rules = rules; private set
    val size get() = rules.size

    /* grid[x][y], like the browser */
    private var grid: Array<Array<Tile?>> = emptyGrid()
    private var dead: Array<BooleanArray> = Array(size) { BooleanArray(size) }
    val locks = ArrayList<Lock>()
    var rng = Rng(1); private set

    var state = GameState.PLAYING; private set
    var endReason = EndReason.NONE; private set
    var score = 0; private set
    var moves = 0; private set
    var trace = 0; private set
    /** Highest normal tile ever on the board in this game. */
    var maxTile = 0; private set
    var won = false; private set
    var keepPlaying = false; private set
    private var nextId = 1

    /* per game counters for records and achievements */
    var fusions = 0; private set
    var purged = 0; private set
    var fried = 0; private set
    var locksSuffered = 0; private set
    var peakTrace = 0; private set
    /** Moves made at the moment the target was reached, or 0. */
    var wonAtMove = 0; private set

    private fun emptyGrid() = Array(rules.size) { arrayOfNulls<Tile>(rules.size) }

    fun newGame(rules: Rules, rng: Rng) {
        this.rules = rules
        this.rng = rng
        grid = emptyGrid()
        dead = Array(size) { BooleanArray(size) }
        rules.deadCells.forEach { (x, y) -> dead[x][y] = true }
        locks.clear()
        score = 0; moves = 0; trace = 0; maxTile = 0
        won = false; keepPlaying = false
        state = GameState.PLAYING; endReason = EndReason.NONE
        fusions = 0; purged = 0; fried = 0; locksSuffered = 0; peakTrace = 0; wonAtMove = 0
        nextId = 1
        rules.startTiles.forEach { place(Tile(nextId++, it.x, it.y, it.value, it.corrupt)) }
        spawnTile()
        spawnTile()
        maxTile = highestTile()
    }

    /* ---------------- queries ---------------- */

    fun within(x: Int, y: Int) = x in 0 until size && y in 0 until size
    fun tileAt(x: Int, y: Int): Tile? = if (within(x, y)) grid[x][y] else null
    fun isDead(x: Int, y: Int) = within(x, y) && dead[x][y]
    fun lockAt(x: Int, y: Int): Lock? = locks.firstOrNull { it.x == x && it.y == y }
    private fun blocked(x: Int, y: Int) = dead[x][y] || lockAt(x, y) != null
    private fun cellFree(x: Int, y: Int) = within(x, y) && grid[x][y] == null && !blocked(x, y)

    fun freeCells(): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>()
        for (x in 0 until size) for (y in 0 until size) if (cellFree(x, y)) out.add(x to y)
        return out
    }

    inline fun eachTile(fn: (Tile) -> Unit) {
        for (x in 0 until size) for (y in 0 until size) tileAt(x, y)?.let(fn)
    }

    fun tiles(): List<Tile> = ArrayList<Tile>().also { l -> eachTile { l.add(it) } }
    fun countCorrupt(): Int { var n = 0; eachTile { if (it.corrupt) n++ }; return n }
    /** Nodes, beacons and worms: everything that feeds the trace. */
    fun countHostile(): Int { var n = 0; eachTile { if (it.kind.hostile) n++ }; return n }
    fun highestTile(): Int { var m = 0; eachTile { if (it.signal && it.value > m) m = it.value }; return m }
    private fun count(kind: Kind): Int { var n = 0; eachTile { if (it.kind == kind) n++ }; return n }
    private fun countSpecials(): Int { var n = 0; eachTile { if (!it.signal && !it.corrupt) n++ }; return n }

    private fun place(t: Tile) { grid[t.x][t.y] = t }

    /* the last spawn was a supply drop */
    private var supplied = false

    /* ---------------- spawning: 90 % a 2, 10 % a 4, as in the original ---------------- */

    private fun spawnTile(): Tile? {
        val cells = freeCells()
        if (cells.isEmpty()) return null
        val (cx, cy) = cells[rng.below(cells.size)]
        var value = if (rng.next() < 1 - rules.fourChance) 2 else 4
        var corrupt = false
        /* Corrupted Nodes: rare once the board has reached corruptFrom, never more than corruptMax */
        if (maxTile >= rules.corruptFrom && countCorrupt() < rules.corruptMax && rng.next() < rules.corruptChance) {
            corrupt = true
            value = rules.corruptValues[rng.below(rules.corruptValues.size)]
        }
        val t = if (corrupt) Tile(nextId++, cx, cy, value, Kind.NODE)
        else if (rules.specials.isNotEmpty()) spawnSpecial(cx, cy, value) else Tile(nextId++, cx, cy, value)
        t.fresh = true
        place(t)
        return t
    }

    /** One draw decides whether the spawn is a special tile instead of a plain signal. */
    private fun spawnSpecial(cx: Int, cy: Int, value: Int): Tile {
        val roll = rng.next()
        var acc = 0.0
        val full = countSpecials() >= rules.specialsMax
        for (s in rules.specials) {
            if (maxTile < s.from) continue
            /* the supply drop is an ordinary signal, so it does not count as a special */
            if (s.kind != Special.SUPPLY && (full || count(kindOf(s.kind)) > 0)) continue
            acc += s.chance
            if (roll >= acc) continue
            return when (s.kind) {
                Special.SUPPLY -> {
                    /* a copy of a signal next to it, so it can fuse right away; no neighbour, no drop */
                    val v = supplyValue(cx, cy)
                    if (v == 0) return Tile(nextId++, cx, cy, value)
                    supplied = true
                    Tile(nextId++, cx, cy, v, Kind.SIGNAL)
                }
                Special.CAPACITOR -> Tile(nextId++, cx, cy, 0, Kind.CAPACITOR, CAPACITOR_FUSE)
                Special.CASED -> Tile(nextId++, cx, cy, casedValue(), Kind.CASED, CASE_LAYERS)
                Special.BEACON -> Tile(nextId++, cx, cy, rules.corruptValues[rng.below(rules.corruptValues.size)], Kind.BEACON, BEACON_COUNT)
                Special.PATCH -> Tile(nextId++, cx, cy, 0, Kind.PATCH)
                Special.WALL -> Tile(nextId++, cx, cy, 0, Kind.WALL, WALL_STRENGTH)
                Special.WORM -> Tile(nextId++, cx, cy, rules.corruptValues[rng.below(rules.corruptValues.size)], Kind.WORM, WORM_CYCLE)
            }
        }
        return Tile(nextId++, cx, cy, value)
    }

    private fun kindOf(s: Special) = when (s) {
        Special.SUPPLY -> Kind.SIGNAL
        Special.CAPACITOR -> Kind.CAPACITOR
        Special.CASED -> Kind.CASED
        Special.BEACON -> Kind.BEACON
        Special.PATCH -> Kind.PATCH
        Special.WALL -> Kind.WALL
        Special.WORM -> Kind.WORM
    }

    /** A supply drop copies the biggest signal next to its cell, up to [SUPPLY_MAX]; 0 when there is none. */
    private fun supplyValue(x: Int, y: Int): Int {
        var v = 0
        for ((nx, ny) in neighbours(x, y)) grid[nx][ny]?.let { if (it.signal && it.value in (v + 1)..SUPPLY_MAX && lockAt(nx, ny) == null) v = it.value }
        return if (v >= 8) v else 0
    }
    /** A cased signal holds a thirty-second of the highest signal, at least a 4. */
    private fun casedValue() = Integer.highestOneBit(maxOf(4, highestTile() / 32))

    /* ---------------- what happens when two tiles meet ---------------- */

    private enum class Meet { NONE, MERGE, PURGE, CRACK, PATCH }

    /** What happens when [t] slides into [next]. Symmetric for the kinds that care. */
    private fun meet(t: Tile, next: Tile): Meet {
        if (t.signal && next.signal) return if (t.value == next.value) Meet.MERGE else Meet.NONE
        /* the browser's rule: equal values, at least one Corrupted Node (or beacon, or worm) */
        if ((t.kind.hostile || next.kind.hostile) && (t.signal || t.kind.hostile) && (next.signal || next.kind.hostile)) {
            return if (t.value == next.value) Meet.PURGE else Meet.NONE
        }
        if ((t.kind == Kind.CASED && next.signal || t.signal && next.kind == Kind.CASED) && t.value == next.value) return Meet.CRACK
        if (t.kind == Kind.PATCH && next.signal && next.value <= PATCH_MAX || t.signal && t.value <= PATCH_MAX && next.kind == Kind.PATCH) return Meet.PATCH
        return Meet.NONE
    }

    /* ---------------- the move ---------------- */

    /** Slides every tile towards [dir]. Returns null when nothing moved. */
    fun move(dir: Int): MoveResult? {
        if (state != GameState.PLAYING) return null
        val vx = Dir.dx[dir]
        val vy = Dir.dy[dir]
        val xs = if (vx == 1) (size - 1 downTo 0) else (0 until size)
        val ys = if (vy == 1) (size - 1 downTo 0) else (0 until size)
        val r = MoveResult(dir)
        eachTile { it.px = it.x; it.py = it.y; it.mergedFrom = null; it.fresh = false; it.hit = false }
        var moved = false

        for (x in xs) for (y in ys) {
            val t = grid[x][y] ?: continue
            if (blocked(x, y) || t.kind == Kind.WALL) continue
            /* farthest free position, and the cell just after it */
            var fx: Int; var fy: Int
            var nx = x; var ny = y
            do { fx = nx; fy = ny; nx = fx + vx; ny = fy + vy } while (cellFree(nx, ny))
            val next = tileAt(nx, ny)
            val how = if (next != null && next.mergedFrom == null && !blocked(nx, ny)) meet(t, next) else Meet.NONE
            when (how) {
                Meet.PURGE -> {
                    /* equal values: the Corrupted Node and the signal cancel each other out */
                    grid[x][y] = null
                    grid[nx][ny] = null
                    t.x = nx; t.y = ny
                    r.ghosts.add(t); r.ghosts.add(next!!)
                    r.purges.add(Triple(nx, ny, t.value))
                    r.purgedKinds.add(if (t.kind.hostile) t.kind else next.kind)
                    score += t.value * 2
                    r.scoreGained += t.value * 2
                    moved = true
                }
                Meet.MERGE, Meet.PATCH -> {
                    val value = if (how == Meet.PATCH) maxOf(t.value, next!!.value) * 2 else t.value * 2
                    val m = Tile(nextId++, nx, ny, value)
                    m.mergedFrom = t to next!!
                    m.px = x; m.py = y
                    grid[x][y] = null
                    grid[nx][ny] = m
                    t.x = nx; t.y = ny
                    next.px = next.x; next.py = next.y
                    r.ghosts.add(t); r.ghosts.add(next)
                    r.merges.add(m)
                    if (how == Meet.PATCH) r.patched.add(m)
                    score += m.value
                    r.scoreGained += m.value
                    if (m.value == rules.target && !won) { won = true; wonAtMove = moves + 1; r.wonNow = true }
                    moved = true
                }
                Meet.CRACK -> {
                    val case = if (t.kind == Kind.CASED) t else next!!
                    if (case.hit) {
                        /* one hit per move: it stops against the case */
                        if (slideTo(t, x, y, fx, fy)) moved = true
                    } else if (case.counter > 1) {
                        /* the case cracks, the signal stops against it */
                        case.counter--
                        case.hit = true
                        r.cracks.add(case)
                        if (slideTo(t, x, y, fx, fy)) moved = true
                        moved = true
                    } else {
                        /* the last layer: they fuse, and it counts double */
                        val m = Tile(nextId++, nx, ny, t.value * 2)
                        m.mergedFrom = t to next!!
                        m.px = x; m.py = y
                        grid[x][y] = null
                        grid[nx][ny] = m
                        t.x = nx; t.y = ny
                        next.px = next.x; next.py = next.y
                        r.ghosts.add(t); r.ghosts.add(next)
                        r.merges.add(m)
                        score += m.value * 2
                        r.scoreGained += m.value * 2
                        if (m.value == rules.target && !won) { won = true; wonAtMove = moves + 1; r.wonNow = true }
                        moved = true
                    }
                }
                Meet.NONE -> if (slideTo(t, x, y, fx, fy)) moved = true
            }
        }
        if (!moved) return null

        moves++
        fusions += r.merges.size
        purged += r.purges.size

        /* EMP Cells (64) fry any Corrupted Node that ends up next to them */
        eachTile { t ->
            if (!t.kind.hostile) return@eachTile
            val nb = arrayOf(tileAt(t.x + 1, t.y), tileAt(t.x - 1, t.y), tileAt(t.x, t.y + 1), tileAt(t.x, t.y - 1))
            val emp = nb.firstOrNull { it != null && it.signal && it.value == 64 }
            if (emp != null) { r.fried.add(t); r.friedBy.add(emp.x to emp.y) }
        }
        r.fried.forEach { t ->
            grid[t.x][t.y] = null
            score += 64
            r.scoreGained += 64
        }
        fried += r.fried.size

        /* fusions wear down Static Walls and crack locks right next to them */
        val hits = r.merges.map { it.x to it.y } + r.purges.map { it.first to it.second } + r.cracks.map { it.x to it.y }
        if (hits.isNotEmpty()) wearDown(hits, r)

        /* AI Trace Level */
        r.traceBefore = trace
        if (r.fusions == 0) trace += rules.traceEmpty + countHostile() * rules.tracePerNode
        else trace -= r.fusions + (if (r.merges.any { it.value >= rules.bigFusion }) rules.traceBigBonus else 0)
        trace = trace.coerceIn(0, 100)
        peakTrace = maxOf(peakTrace, trace)

        /* locks count down per move */
        val it = locks.iterator()
        while (it.hasNext()) {
            val l = it.next()
            l.turns--
            if (l.turns <= 0) { it.remove(); r.locksReleased++ }
        }

        /* the special tiles' counters */
        val infect = tickSpecials(r)

        supplied = false
        r.spawned = spawnTile()
        r.supply = supplied

        /* a worm whose cycle ran out turns the new signal into a Corrupted Node */
        if (infect) r.spawned?.let { s ->
            if (s.signal) {
                val n = Tile(nextId++, s.x, s.y, s.value, Kind.NODE)
                n.fresh = true
                place(n)
                r.spawned = n
                r.infected = n
            }
        }

        if (trace >= 100) r.lock = nexusLock()
        r.traceAfter = trace

        val hi = highestTile()
        if (hi > maxTile) { maxTile = hi; r.newMax = hi }

        if (won && !keepPlaying) {
            state = GameState.WON
        } else if (!movesAvailable()) {
            if (locks.isNotEmpty()) {
                /* a lock may never be the only reason the grid is stuck */
                locks.clear()
                r.locksTimedOut = true
                if (!movesAvailable()) end(EndReason.SIGNAL_LOST, r)
            } else end(EndReason.SIGNAL_LOST, r)
        } else if (rules.moveLimit > 0 && moves >= rules.moveLimit && !won) {
            end(EndReason.OUT_OF_MOVES, r)
        }
        return r
    }

    private fun slideTo(t: Tile, x: Int, y: Int, fx: Int, fy: Int): Boolean {
        if (fx == x && fy == y) return false
        grid[x][y] = null
        grid[fx][fy] = t
        t.x = fx; t.y = fy
        return true
    }

    private fun neighbours(x: Int, y: Int) = listOf(x + 1 to y, x - 1 to y, x to y + 1, x to y - 1).filter { (a, b) -> within(a, b) }

    /** Walls next to a fusion lose a layer; locks next to one lose [Rules.lockCrack] moves. */
    private fun wearDown(hits: List<Pair<Int, Int>>, r: MoveResult) {
        val walls = LinkedHashSet<Tile>()
        val cracked = LinkedHashSet<Lock>()
        for ((hx, hy) in hits) for ((nx, ny) in neighbours(hx, hy)) {
            tileAt(nx, ny)?.let { if (it.kind == Kind.WALL) walls.add(it) }
            if (rules.lockCrack > 0) lockAt(nx, ny)?.let { cracked.add(it) }
        }
        for (w in walls) {
            w.counter--
            if (w.counter <= 0) { grid[w.x][w.y] = null; r.wallsBroken.add(w) } else r.wallsHit.add(w)
        }
        for (l in cracked) {
            l.turns -= rules.lockCrack
            if (l.turns <= 0) { locks.remove(l); r.locksCracked++ }
        }
    }

    /** Counts down capacitors, beacons and worms. Returns true when a worm infects this move. */
    private fun tickSpecials(r: MoveResult): Boolean {
        var infect = false
        val specials = tiles().filter { it.kind == Kind.CAPACITOR || it.kind == Kind.BEACON || it.kind == Kind.WORM }
        for (t in specials) {
            if (grid[t.x][t.y] !== t) continue
            t.counter--
            if (t.counter > 0) continue
            when (t.kind) {
                Kind.CAPACITOR -> {
                    /* the discharge clears the four cells around it; big signals ride it out */
                    grid[t.x][t.y] = null
                    val cleared = ArrayList<Pair<Int, Int>>()
                    for ((nx, ny) in neighbours(t.x, t.y)) {
                        val n = grid[nx][ny] ?: continue
                        if (n.signal && n.value > CAPACITOR_CLEARS || n.kind == Kind.CASED && n.value > CAPACITOR_CLEARS) continue
                        if (n.kind == Kind.WALL) {
                            n.counter--
                            if (n.counter <= 0) { grid[nx][ny] = null; r.wallsBroken.add(n) } else r.wallsHit.add(n)
                        } else {
                            grid[nx][ny] = null
                            locks.removeAll { it.x == nx && it.y == ny && it.pinned }
                        }
                        cleared.add(nx to ny)
                    }
                    r.discharges.add((t.x to t.y) to cleared)
                }
                Kind.BEACON -> {
                    /* it gives the player away, and stays behind as a Corrupted Node */
                    trace = 100
                    val n = Tile(t.id, t.x, t.y, t.value, Kind.NODE)
                    place(n)
                    r.transmitted.add(n)
                }
                Kind.WORM -> {
                    t.counter = WORM_CYCLE
                    infect = true
                }
                else -> {}
            }
        }
        return infect
    }

    private fun end(reason: EndReason, r: MoveResult?) {
        state = GameState.OVER
        endReason = reason
        r?.ended = reason
    }

    /** NEXUS-9 found you: one cell is sealed for [Rules.lockTurns] moves, longer every time if it learns. */
    private fun nexusLock(): Lock? {
        val cells = freeCells()
        /* a pin lock picks a signal instead of an empty cell */
        val pin = rules.pinChance > 0 && rng.next() < rules.pinChance
        val pinnable = if (pin) tiles().filter { it.signal && lockAt(it.x, it.y) == null } else emptyList()
        val c: Pair<Int, Int> = when {
            pinnable.isNotEmpty() -> pinnable[rng.below(pinnable.size)].let { it.x to it.y }
            cells.isNotEmpty() -> cells[rng.below(cells.size)]
            else -> {
                val all = ArrayList<Pair<Int, Int>>()
                eachTile { t -> if (lockAt(t.x, t.y) == null) all.add(t.x to t.y) }
                if (all.isEmpty()) return null
                all[rng.below(all.size)]
            }
        }
        val turns = minOf(maxOf(rules.lockMax, rules.lockTurns), rules.lockTurns + locksSuffered * rules.lockGrowth)
        val l = Lock(c.first, c.second, turns)
        l.pinned = tileAt(c.first, c.second) != null
        locks.add(l)
        trace = rules.traceAfterLock
        locksSuffered++
        return l
    }

    fun movesAvailable(): Boolean {
        if (freeCells().isNotEmpty()) return true
        for (x in 0 until size) for (y in 0 until size) {
            val t = grid[x][y] ?: continue
            if (lockAt(x, y) != null) continue
            val r = tileAt(x + 1, y)
            val d = tileAt(x, y + 1)
            if (r != null && lockAt(x + 1, y) == null && (meet(t, r) != Meet.NONE || meet(r, t) != Meet.NONE)) return true
            if (d != null && lockAt(x, y + 1) == null && (meet(t, d) != Meet.NONE || meet(d, t) != Meet.NONE)) return true
        }
        return false
    }

    /** After the Override: keep going towards 4096 and 8192. */
    fun continueEndless() {
        if (state != GameState.WON) return
        keepPlaying = true
        state = GameState.PLAYING
        if (!movesAvailable()) end(EndReason.SIGNAL_LOST, null)
    }

    /** The operator ends the session. */
    fun abort() {
        if (state == GameState.OVER) return
        end(EndReason.ABORTED, null)
    }

    /** Clears the animation state, so a restored board does not slide in. */
    fun settle() = eachTile { it.px = it.x; it.py = it.y; it.mergedFrom = null; it.fresh = false }

    /* ---------------- for the tutorial ---------------- */

    /** Changes the rules in the middle of a game, keeping the board (same size and dead cells). */
    fun retune(r: Rules) {
        require(r.size == rules.size)
        rules = r
    }

    /** Puts a signal in a free cell; it pops in like a spawn. */
    fun inject(x: Int, y: Int, value: Int): Tile? {
        if (!cellFree(x, y)) return null
        val t = Tile(nextId++, x, y, value)
        t.fresh = true
        place(t)
        if (value > maxTile) maxTile = value
        return t
    }

    /** Takes a signal off the board. */
    fun clearCell(x: Int, y: Int) {
        if (within(x, y)) grid[x][y] = null
    }

    /** A deep copy, for looking ahead (the hint bot and the balance simulation). */
    fun copy(): Engine {
        val e = Engine(rules)
        e.grid = Array(size) { x -> Array(size) { y -> grid[x][y]?.let { Tile(it.id, it.x, it.y, it.value, it.kind, it.counter) } } }
        e.dead = Array(size) { dead[it].copyOf() }
        locks.forEach { e.locks.add(Lock(it.x, it.y, it.turns).also { c -> c.pinned = it.pinned }) }
        e.rng = Rng(rng.state)
        e.state = state; e.endReason = endReason
        e.score = score; e.moves = moves; e.trace = trace; e.maxTile = maxTile
        e.won = won; e.keepPlaying = keepPlaying; e.nextId = nextId
        e.fusions = fusions; e.purged = purged; e.fried = fried
        e.locksSuffered = locksSuffered; e.peakTrace = peakTrace; e.wonAtMove = wonAtMove
        return e
    }

    /* ---------------- save and rollback ---------------- */

    fun toJson(): JSONObject {
        val cells = JSONArray()
        eachTile {
            val c = JSONArray().put(it.x).put(it.y).put(it.value).put(if (it.corrupt) 1 else 0)
            if (it.kind != Kind.SIGNAL && it.kind != Kind.NODE) c.put(it.kind.name).put(it.counter)
            cells.put(c)
        }
        val ls = JSONArray()
        locks.forEach { ls.put(JSONArray().put(it.x).put(it.y).put(it.turns).apply { if (it.pinned) put(1) }) }
        return JSONObject()
            .put("cells", cells).put("locks", ls)
            .put("score", score).put("moves", moves).put("trace", trace).put("max", maxTile)
            .put("won", won).put("keep", keepPlaying).put("state", state.name).put("end", endReason.name)
            .put("rng", rng.state).put("id", nextId)
            .put("fusions", fusions).put("purged", purged).put("fried", fried)
            .put("locksSuffered", locksSuffered).put("peakTrace", peakTrace).put("wonAt", wonAtMove)
    }

    /** Restores a saved game; [rules] must be the rules it was played with. */
    fun restore(o: JSONObject, rules: Rules) {
        this.rules = rules
        grid = emptyGrid()
        dead = Array(size) { BooleanArray(size) }
        rules.deadCells.forEach { (x, y) -> dead[x][y] = true }
        var id = 1
        val cells = o.getJSONArray("cells")
        for (i in 0 until cells.length()) {
            val c = cells.getJSONArray(i)
            val x = c.getInt(0); val y = c.getInt(1)
            if (!within(x, y)) continue
            val kind = if (c.length() > 4) Kind.valueOf(c.getString(4)) else if (c.getInt(3) == 1) Kind.NODE else Kind.SIGNAL
            place(Tile(id++, x, y, c.getInt(2), kind, if (c.length() > 5) c.getInt(5) else 0))
        }
        nextId = maxOf(o.optInt("id", 1), id)
        locks.clear()
        val ls = o.getJSONArray("locks")
        for (i in 0 until ls.length()) {
            val l = ls.getJSONArray(i)
            locks.add(Lock(l.getInt(0), l.getInt(1), l.getInt(2)).also { it.pinned = l.length() > 3 && l.getInt(3) == 1 })
        }
        score = o.getInt("score"); moves = o.getInt("moves"); trace = o.getInt("trace")
        maxTile = o.optInt("max", highestTile())
        won = o.getBoolean("won"); keepPlaying = o.getBoolean("keep")
        state = GameState.valueOf(o.optString("state", GameState.PLAYING.name))
        endReason = EndReason.valueOf(o.optString("end", EndReason.NONE.name))
        rng = Rng(o.getInt("rng"))
        fusions = o.optInt("fusions"); purged = o.optInt("purged"); fried = o.optInt("fried")
        locksSuffered = o.optInt("locksSuffered"); peakTrace = o.optInt("peakTrace"); wonAtMove = o.optInt("wonAt")
    }

    companion object {
        /** The biggest signal a Surge Capacitor's discharge wipes out. */
        const val CAPACITOR_CLEARS = 64
        /** The biggest signal a supply drop copies. */
        const val SUPPLY_MAX = 128
        /** Moves before a Surge Capacitor discharges. */
        const val CAPACITOR_FUSE = 6
        /** Hits a Cased Signal takes: the first cracks the case, the last fuses. */
        const val CASE_LAYERS = 2
        /** Moves before a Trace Beacon transmits. */
        const val BEACON_COUNT = 15
        /** The biggest signal a Patch Cable can double: no shortcut to the Override. */
        const val PATCH_MAX = 256
        /** Layers of a Static Wall. */
        const val WALL_STRENGTH = 2
        /** Moves between two infections of a Worm. */
        const val WORM_CYCLE = 8
    }
}
