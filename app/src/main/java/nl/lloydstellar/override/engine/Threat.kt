package nl.lloydstellar.override.engine

/**
 * How hard Classic is. CALM is for playing without pressure; STANDARD is
 * Classic as it is; THREAT 1 to 8 is a ladder for puzzlers, where every rung
 * adds one rule to all the ones below it, and opens after an Override on the
 * rung before. Records are kept per level.
 */
class Threat(
    /** -1 calm, 0 standard, 1..8 the ladder */
    val level: Int,
    val name: String,
    /** What this level adds, in one line. */
    val adds: String,
    val rules: Rules,
) {
    val short get() = when (level) {
        -1 -> "CALM"
        0 -> "STD"
        else -> "T$level"
    }
}

val THREATS: List<Threat> = buildList {
    add(Threat(-1, "CALM", "The rules of the browser game: NEXUS-9 hardly ever locks a cell. Three rollbacks to start, five at most.",
        WEB.copy(rollbackStart = 3, rollbackMax = 5)))
    add(Threat(0, "STANDARD", "Classic: the trace bites, red nodes from the 32 on, one rollback to start.", CLASSIC))
    /* each rung adds one rule to everything above it */
    val rungs: List<Triple<String, String, (Rules) -> Rules>> = listOf(
        Triple("EARLY NODES", "Corrupted Nodes from the 16 on.") { r -> r.copy(corruptFrom = 16) },
        Triple("HEAVY TRAFFIC", "Up to three Corrupted Nodes at once, and more often.") { r -> r.copy(corruptMax = 3, corruptChance = 0.09) },
        Triple("HOT TRACE", "Idle moves raise the trace by 6\u00A0% instead of 5\u00A0%.") { r -> r.copy(traceEmpty = 6) },
        Triple("THIN BUFFER", "The rollback buffer holds one charge.") { r -> r.copy(rollbackMax = 1) },
        Triple("LONG LOCKS", "NEXUS-9 locks a cell for seven moves.") { r -> r.copy(lockTurns = 7) },
        Triple("BIG NODES", "Corrupted Nodes can be worth 16.") { r -> r.copy(corruptValues = listOf(2, 4, 8, 16)) },
        Triple("NO ROLLBACK", "No rollback at all.") { r -> r.copy(rollbackStart = 0, rollbackMax = 0) },
        Triple("FULL ALERT", "Idle moves add 7\u00A0%, locks hold nine moves, and up to four nodes swarm the matrix.") { r ->
            r.copy(traceEmpty = 7, traceAfterLock = 40, lockTurns = 9, corruptMax = 4, corruptChance = 0.12)
        },
    )
    var rules = CLASSIC
    rungs.forEachIndexed { i, (name, adds, apply) ->
        rules = apply(rules)
        add(Threat(i + 1, "THREAT ${i + 1} // $name", adds, rules))
    }
}

fun threat(level: Int): Threat = THREATS.firstOrNull { it.level == level } ?: THREATS[1]

const val MAX_THREAT = 8

/**
 * The special tiles of Classic. Each one shows up the first time in a game
 * that reaches [unlock]; once you have built that tile in any game it comes
 * earlier, from [early] on. CALM only gets the [friendly] ones.
 */
class SpecialInfo(
    val kind: Special,
    val name: String,
    val unlock: Int,
    val early: Int,
    val chance: Double,
    val friendly: Boolean,
)

val SPECIALS = listOf(
    SpecialInfo(Special.SUPPLY, "SUPPLY DROP", 128, 64, 0.012, true),
    SpecialInfo(Special.CAPACITOR, "SURGE CAPACITOR", 256, 32, 0.010, true),
    SpecialInfo(Special.CASED, "CASED SIGNAL", 512, 128, 0.010, false),
    SpecialInfo(Special.BEACON, "TRACE BEACON", 1024, 128, 0.006, false),
    SpecialInfo(Special.PATCH, "PATCH CABLE", 1024, 256, 0.005, true),
    SpecialInfo(Special.WALL, "STATIC WALL", 2048, 256, 0.005, false),
    SpecialInfo(Special.WORM, "WORM", 4096, 512, 0.004, false),
)

fun specialInfo(kind: Special) = SPECIALS.first { it.kind == kind }

/** Pin locks start once you have built a 512 in any game. */
const val PIN_UNLOCK = 512
const val PIN_CHANCE = 0.25

/** The rules of Classic at [level] for a player whose highest tile ever is [best]. */
fun classicRules(level: Int, best: Int): Rules {
    val base = threat(level).rules
    val calm = level < 0
    val specials = SPECIALS.filter { !calm || it.friendly }
        .map { SpecialSpawn(it.kind, it.chance, if (best >= it.unlock) it.early else it.unlock) }
    return base.copy(specials = specials, pinChance = if (!calm && best >= PIN_UNLOCK) PIN_CHANCE else 0.0)
}
