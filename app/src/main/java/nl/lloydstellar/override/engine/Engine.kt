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

class Tile(val id: Int, var x: Int, var y: Int, val value: Int, val corrupt: Boolean) {
    /* where the tile was before the last move, for the slide animation */
    var px = x
    var py = y
    /* the two tiles this one was fused from in the last move */
    var mergedFrom: Pair<Tile, Tile>? = null
    /* spawned in the last move */
    var fresh = false
}

class Lock(val x: Int, val y: Int, var turns: Int)

/** What one move did, for the animation, the sound and the achievements. */
class MoveResult(val dir: Int) {
    /** Tiles that slid into a fusion or a purge and disappear when the slide ends. */
    val ghosts = ArrayList<Tile>()
    val merges = ArrayList<Tile>()
    /** Cells where a Corrupted Node and an equal signal cancelled out, with the value. */
    val purges = ArrayList<Triple<Int, Int, Int>>()
    /** Corrupted Nodes that an EMP Cell fried, and where that EMP Cell (the 64) sits. */
    val fried = ArrayList<Tile>()
    val friedBy = ArrayList<Pair<Int, Int>>()
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
    val fusions get() = merges.size + purges.size
}

/**
 * The rules of 2048 Override. The move, the traversal order, the farthest
 * position search and the one-fusion-per-tile rule follow Gabriele Cirulli's
 * MIT-licensed 2048 (GameManager.move), by way of the browser game's
 * override.js, line for line. New in override.js and kept here: Corrupted
 * Nodes, the EMP Cell, the AI Trace Level and NEXUS-9 locks. New in the app:
 * [Rules] for the sectors (other sizes, dead cells, a move limit) and a
 * seeded [Rng] so a game can be saved and rolled back exactly.
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
    fun highestTile(): Int { var m = 0; eachTile { if (!it.corrupt && it.value > m) m = it.value }; return m }

    private fun place(t: Tile) { grid[t.x][t.y] = t }

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
        val t = Tile(nextId++, cx, cy, value, corrupt)
        t.fresh = true
        place(t)
        return t
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
        eachTile { it.px = it.x; it.py = it.y; it.mergedFrom = null; it.fresh = false }
        var moved = false

        for (x in xs) for (y in ys) {
            val t = grid[x][y] ?: continue
            if (blocked(x, y)) continue
            /* farthest free position, and the cell just after it */
            var fx: Int; var fy: Int
            var nx = x; var ny = y
            do { fx = nx; fy = ny; nx = fx + vx; ny = fy + vy } while (cellFree(nx, ny))
            val next = tileAt(nx, ny)
            if (next != null && next.value == t.value && next.mergedFrom == null && !blocked(nx, ny)) {
                if (t.corrupt || next.corrupt) {
                    /* equal values: the Corrupted Node and the signal cancel each other out */
                    grid[x][y] = null
                    grid[nx][ny] = null
                    t.x = nx; t.y = ny
                    r.ghosts.add(t); r.ghosts.add(next)
                    r.purges.add(Triple(nx, ny, t.value))
                    score += t.value * 2
                    r.scoreGained += t.value * 2
                } else {
                    val m = Tile(nextId++, nx, ny, t.value * 2, false)
                    m.mergedFrom = t to next
                    m.px = x; m.py = y
                    grid[x][y] = null
                    grid[nx][ny] = m
                    t.x = nx; t.y = ny
                    next.px = next.x; next.py = next.y
                    r.ghosts.add(t); r.ghosts.add(next)
                    r.merges.add(m)
                    score += m.value
                    r.scoreGained += m.value
                    if (m.value == rules.target && !won) { won = true; wonAtMove = moves + 1; r.wonNow = true }
                }
                moved = true
            } else if (fx != x || fy != y) {
                grid[x][y] = null
                grid[fx][fy] = t
                t.x = fx; t.y = fy
                moved = true
            }
        }
        if (!moved) return null

        moves++
        fusions += r.merges.size
        purged += r.purges.size

        /* EMP Cells (64) fry any Corrupted Node that ends up next to them */
        eachTile { t ->
            if (!t.corrupt) return@eachTile
            val nb = arrayOf(tileAt(t.x + 1, t.y), tileAt(t.x - 1, t.y), tileAt(t.x, t.y + 1), tileAt(t.x, t.y - 1))
            val emp = nb.firstOrNull { it != null && !it.corrupt && it.value == 64 }
            if (emp != null) { r.fried.add(t); r.friedBy.add(emp.x to emp.y) }
        }
        r.fried.forEach { t ->
            grid[t.x][t.y] = null
            score += 64
            r.scoreGained += 64
        }
        fried += r.fried.size

        /* AI Trace Level */
        r.traceBefore = trace
        if (r.fusions == 0) trace += rules.traceEmpty + countCorrupt() * rules.tracePerNode
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

        r.spawned = spawnTile()

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

    private fun end(reason: EndReason, r: MoveResult?) {
        state = GameState.OVER
        endReason = reason
        r?.ended = reason
    }

    /** NEXUS-9 found you: one cell is sealed for [Rules.lockTurns] moves. */
    private fun nexusLock(): Lock? {
        val cells = freeCells()
        val c: Pair<Int, Int> = if (cells.isNotEmpty()) cells[rng.below(cells.size)] else {
            val all = ArrayList<Pair<Int, Int>>()
            eachTile { t -> if (lockAt(t.x, t.y) == null) all.add(t.x to t.y) }
            if (all.isEmpty()) return null
            all[rng.below(all.size)]
        }
        val l = Lock(c.first, c.second, rules.lockTurns)
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
            if (r != null && r.value == t.value && lockAt(x + 1, y) == null) return true
            if (d != null && d.value == t.value && lockAt(x, y + 1) == null) return true
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
        val t = Tile(nextId++, x, y, value, false)
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
        e.grid = Array(size) { x -> Array(size) { y -> grid[x][y]?.let { Tile(it.id, it.x, it.y, it.value, it.corrupt) } } }
        e.dead = Array(size) { dead[it].copyOf() }
        locks.forEach { e.locks.add(Lock(it.x, it.y, it.turns)) }
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
        eachTile { cells.put(JSONArray().put(it.x).put(it.y).put(it.value).put(if (it.corrupt) 1 else 0)) }
        val ls = JSONArray()
        locks.forEach { ls.put(JSONArray().put(it.x).put(it.y).put(it.turns)) }
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
            if (within(x, y)) place(Tile(id++, x, y, c.getInt(2), c.getInt(3) == 1))
        }
        nextId = maxOf(o.optInt("id", 1), id)
        locks.clear()
        val ls = o.getJSONArray("locks")
        for (i in 0 until ls.length()) {
            val l = ls.getJSONArray(i)
            locks.add(Lock(l.getInt(0), l.getInt(1), l.getInt(2)))
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
}
