package nl.lloydstellar.override.engine

/** A tile that is on the board when a sector starts. */
data class StartTile(val x: Int, val y: Int, val value: Int, val corrupt: Boolean = false)

/**
 * Everything that can differ between Classic and a sector. The defaults are
 * the numbers of the browser game (override.js); [CLASSIC] uses them all.
 */
data class Rules(
    val size: Int = 4,
    /** The Override: making this tile wins. */
    val target: Int = 2048,
    /** After the target you may keep going (Global Purge mode). */
    val endless: Boolean = true,
    /** Chance that a spawn is a 4 instead of a 2. */
    val fourChance: Double = 0.1,
    /** Corrupted Nodes can spawn once the highest tile has reached this. */
    val corruptFrom: Int = 32,
    val corruptChance: Double = 0.07,
    val corruptMax: Int = 2,
    val corruptValues: List<Int> = listOf(2, 4, 8),
    /** AI Trace: added by a move without a fusion ... */
    val traceEmpty: Int = 2,
    /** ... plus this much for every Corrupted Node on the board. */
    val tracePerNode: Int = 1,
    /** A fusion of [bigFusion] or more takes this much extra off the trace. */
    val traceBigBonus: Int = 2,
    val bigFusion: Int = 128,
    /** At 100 % NEXUS-9 seals a cell for this many moves ... */
    val lockTurns: Int = 5,
    /** ... and the trace falls back to this. */
    val traceAfterLock: Int = 35,
    /** Burned-out cells: walls for the whole game. */
    val deadCells: List<Pair<Int, Int>> = emptyList(),
    /** 0 = no limit; otherwise the target must fall within this many moves. */
    val moveLimit: Int = 0,
    val startTiles: List<StartTile> = emptyList(),
)

val CLASSIC = Rules()
