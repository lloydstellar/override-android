package nl.lloydstellar.override

import nl.lloydstellar.override.data.Store
import nl.lloydstellar.override.engine.Kind
import nl.lloydstellar.override.engine.boardJson
import org.json.JSONArray
import org.json.JSONObject

/**
 * Debug builds only: boards for testing on a device and for the store
 * screenshots, loaded with
 *
 *   adb shell am start -n nl.lloydstellar.override.debug/nl.lloydstellar.override.MainActivity --es demo late
 *
 * Release builds never call this (MainActivity checks BuildConfig.DEBUG,
 * and R8 drops it).
 */
object Demo {
    /* cells as rows, top to bottom; 0 = empty, negative = Corrupted Node */
    private val BOARDS = mapOf(
        "late" to Board(
            listOf(
                listOf(2, 4, 0, 2),
                listOf(8, 16, -8, 4),
                listOf(64, 32, 16, 8),
                listOf(1024, 512, 256, 128),
            ), score = 18764, moves = 911, trace = 58, locks = listOf(Triple(2, 0, 3)), max = 1024
        ),
        "win" to Board(
            listOf(
                listOf(4, 2, 0, 0),
                listOf(16, 8, 4, 2),
                listOf(128, 64, 32, 8),
                listOf(1024, 1024, 256, 128),
            ), score = 19920, moves = 978, trace = 12, max = 1024
        ),
        "lost" to Board(
            listOf(
                listOf(2, 4, 2, 4),
                listOf(4, 2, 8, 2),
                listOf(16, 32, 64, 128),
                listOf(512, 256, 128, 0),
            ), score = 6188, moves = 482, trace = 74, max = 512
        ),
        "nodes" to Board(
            listOf(
                listOf(2, 0, 4, 0),
                listOf(0, -4, 0, 2),
                listOf(8, 64, -2, 16),
                listOf(256, 128, 32, 16),
            ), score = 3420, moves = 288, trace = 31, max = 256
        ),
        "s1win" to Board(
            listOf(
                listOf(2, 0, 0, 0),
                listOf(4, 2, 0, 0),
                listOf(16, 8, 4, 2),
                listOf(128, 128, 32, 8),
            ), score = 1630, moves = 141, trace = 4, max = 128, sector = 1
        ),
        "endless" to Board(
            listOf(
                listOf(4, 8, 2, 0),
                listOf(32, 16, 8, 4),
                listOf(256, 128, 64, 16),
                listOf(2048, 1024, 512, 512),
            ), score = 37104, moves = 1660, trace = 6, max = 2048, won = true, keep = true
        ),
        /* every special tile at once; the zeros under them are filled in from [Board.specials] */
        "specials" to Board(
            listOf(
                listOf(0, 2, 0, 0),
                listOf(8, 0, 0, 4),
                listOf(32, 0, 0, 0),
                listOf(512, 256, 128, 64),
            ), score = 9310, moves = 640, trace = 44, max = 4096,
            specials = listOf(
                Special(0, 0, Kind.CAPACITOR, 0, 2), Special(2, 0, Kind.BEACON, 4, 3),
                Special(1, 1, Kind.CASED, 16, 2), Special(2, 1, Kind.PATCH, 0, 0),
                Special(1, 2, Kind.WALL, 0, 2), Special(2, 2, Kind.WORM, 2, 5), Special(3, 2, Kind.CASED, 8, 1),
            ),
            pins = listOf(Triple(1, 3, 4)),
        ),
    )

    private class Special(val x: Int, val y: Int, val kind: Kind, val value: Int, val counter: Int)

    private class Board(
        val rows: List<List<Int>>, val score: Int, val moves: Int, val trace: Int,
        val locks: List<Triple<Int, Int, Int>> = emptyList(), val max: Int, val won: Boolean = false, val keep: Boolean = false,
        val sector: Int = 0,
        val specials: List<Special> = emptyList(),
        val pins: List<Triple<Int, Int, Int>> = emptyList(),
    )

    /** Puts a demo board into its save slot; returns the mode to continue, or null. */
    fun load(name: String, store: Store): Mode? {
        if (name == "unlock") {
            store.reachTile(4096)
            return null
        }
        if (name == "fails") {
            /* three lost attempts at sector 1, so its briefing offers the assist */
            repeat(3) { store.failSector(1) }
            return null
        }
        if (name == "progress") {
            /* a campaign under way, for the screenshots of the sector map */
            listOf(3, 3, 2, 3, 1, 2).forEachIndexed { i, bars -> store.clearSector(i + 1, bars, 150 + i * 40) }
            store.reachTile(1024)
            return null
        }
        val b = BOARDS[name] ?: return null
        val engine = boardJson(b.rows, b.score, b.moves, b.trace, b.locks, b.won, b.keep)
        b.specials.forEach { engine.getJSONArray("cells").put(JSONArray().put(it.x).put(it.y).put(it.value).put(0).put(it.kind.name).put(it.counter)) }
        b.pins.forEach { (x, y, t) -> engine.getJSONArray("locks").put(JSONArray().put(x).put(y).put(t).put(1)) }
        store.reachTile(b.max)
        val save = JSONObject().put("engine", engine).put("history", JSONArray()).put("earned", JSONArray())
            .put("charges", 2).put("rollbacksUsed", 0).put("elapsed", b.moves * 1400L).put("unlocked", b.max)
        return if (b.sector > 0) {
            store.saveSector(save.put("sector", b.sector))
            Mode.SECTOR
        } else {
            store.saveClassic(save)
            Mode.CLASSIC
        }
    }
}
