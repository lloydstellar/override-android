package nl.lloydstellar.override.engine

import java.time.LocalDate
import java.time.temporal.IsoFields
import kotlin.math.roundToInt

/**
 * The Daily and Weekly Directives: generated from the date, so every player
 * gets the same twist, the same board and the same spawns on the same day,
 * without a server. A daily is short (a 256 with one twist, a few minutes);
 * a weekly asks for a 1024 with two twists at once.
 *
 * Par and the lock limit come from the twists' factors, which
 * DirectiveBalanceSim checks against the bots on the real directives of the
 * coming months.
 */
object Directives {

    private class Twist(
        val id: String,
        val name: String,
        val line: String,
        /** Moves to the target compared with plain Classic. */
        val factor: Double,
        /** Locks per move compared with plain Classic. */
        val lockFactor: Double,
        val size: Int = 0,
        /** The daily's target when this twist makes 256 too much for a few minutes of casual play. */
        val dailyTarget: Int = 256,
        val apply: (Rules, Rng) -> Rules,
    )

    private val TWISTS = listOf(
        Twist("hot", "HOT TRACE", "Idle moves raise the trace by 9 %.", 1.05, 4.0) { r, _ -> r.copy(traceEmpty = 9) },
        Twist("rogue", "ROGUE CODE", "Corrupted Nodes from the first move.", 1.45, 1.8) { r, _ -> r.copy(corruptFrom = 2, corruptChance = 0.14) },
        Twist("swarm", "SWARM", "Up to four Corrupted Nodes at once.", 2.05, 3.0) { r, _ -> r.copy(corruptFrom = 2, corruptChance = 0.25, corruptMax = 4) },
        Twist("pixel", "DEAD PIXEL", "One cell is burned out.", 1.1, 1.3) { r, g -> r.copy(deadCells = deadCells(r.size, 1, g)) },
        Twist("blackout", "BLACKOUT", "Two cells are burned out.", 1.25, 1.6, dailyTarget = 128) { r, g -> r.copy(deadCells = deadCells(r.size, 2, g)) },
        Twist("locks", "LONG LOCKS", "Locks hold for nine moves.", 1.0, 1.4) { r, _ -> r.copy(lockTurns = 9, traceAfterLock = 45) },
        Twist("fours", "HEAVY SPAWNS", "One spawn in three is a 4.", 0.85, 1.0) { r, _ -> r.copy(fourChance = 0.33) },
        Twist("emp", "EMP DROP", "An EMP Cell in a corner, and red nodes from the start.", 1.2, 2.3) { r, g ->
            val corners = listOf(0 to 0, r.size - 1 to 0, 0 to r.size - 1, r.size - 1 to r.size - 1)
            val (x, y) = corners[g.below(4)]
            r.copy(startTiles = listOf(StartTile(x, y, 64)), corruptFrom = 2, corruptChance = 0.16, corruptMax = 3)
        },
        Twist("wide", "WIDE BAND", "A 5 x 5 matrix.", 1.25, 1.0, size = 5) { r, _ -> r.copy(size = 5) },
        Twist("narrow", "NARROW BAND", "A 3 x 3 matrix.", 1.0, 0.0, size = 3, dailyTarget = 64) { r, _ -> r.copy(size = 3) },
        Twist("burst", "BURST WINDOW", "A move limit.", 1.0, 1.0) { r, _ -> r },
    )

    /** Median moves of a good player to each target in plain Classic (SectorBalanceSim). */
    private fun baseMoves(target: Int, size: Int) = when {
        size == 3 -> if (target <= 64) 38 else 70
        target <= 128 -> 75
        target <= 256 -> 150
        target <= 512 -> 305
        else -> 560
    }

    /* about one lock per 230 moves in Classic for a good player */
    private const val MOVES_PER_LOCK = 230.0

    private val PLACES = listOf(
        "A relay in the old harbour district keeps flickering. NEXUS-9 routes half the city through it.",
        "The water plant on the river went quiet last night. Its controllers still answer to the AI.",
        "A tram depot full of sleeping drones. Their charging grid runs on a single core.",
        "The radio mast on the heath is broadcasting again, but not for us.",
        "Under the station there is a server hall nobody built. It hums at night.",
        "The library's old card catalogue room hides a junction box with a network port.",
        "A billboard on the ring road started showing our faces. Time to cut its feed.",
        "The hospital generator is clean. The building management system is not.",
        "Somebody found a live fibre under the market square. It leads to a core.",
        "The windmill park feeds the grid of three sectors. One controller decides where it goes.",
        "An office tower with every light on and nobody inside.",
        "The film studio lot still has its tape archive. NEXUS-9 wants it erased.",
    )

    fun daily(date: LocalDate): Sector {
        val seed = mix(date.toEpochDay().toInt() * 31 + 7)
        val g = Rng(seed)
        val twist = TWISTS[g.below(TWISTS.size)]
        val place = PLACES[g.below(PLACES.size)]
        val target = twist.dailyTarget
        return build(
            Sector.Kind.DAILY, "D$date", "DAILY // ${twist.name}", listOf(twist), target, place,
            Rng(mix(seed + 1)), mix(seed + 2)
        )
    }

    fun weekly(date: LocalDate): Sector {
        val year = date.get(IsoFields.WEEK_BASED_YEAR)
        val week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        val seed = mix(year * 100 + week + 900_000)
        val g = Rng(seed)
        /* two twists that go together: one matrix size at most, never the 3 x 3, never two kinds of dead cells */
        val pool = TWISTS.filter { it.id != "narrow" }
        val a = pool[g.below(pool.size)]
        var b: Twist
        do {
            b = pool[g.below(pool.size)]
        } while (b == a || (a.size != 0 && b.size != 0) || setOf(a.id, b.id) == setOf("pixel", "blackout") ||
            setOf(a.id, b.id) == setOf("rogue", "swarm"))
        val twists = listOf(a, b).sortedBy { if (it.size != 0) 0 else 1 }
        val place = PLACES[g.below(PLACES.size)]
        return build(
            Sector.Kind.WEEKLY, "W$year-${week.toString().padStart(2, '0')}", "WEEKLY // ${a.name} + ${b.name}",
            twists, 1024, place, Rng(mix(seed + 1)), mix(seed + 2)
        )
    }

    private fun build(kind: Sector.Kind, id: String, name: String, twists: List<Twist>, target: Int, place: String, g: Rng, seed: Int): Sector {
        var rules = CLASSIC.copy(target = target, endless = false)
        twists.forEach { rules = it.apply(rules, g) }
        val moves = baseMoves(target, rules.size) * twists.fold(1.0) { f, t -> f * t.factor }
        val par = (moves * 1.04 / 5).roundToInt() * 5
        if (twists.any { it.id == "burst" }) rules = rules.copy(moveLimit = (par * 1.3 / 5).roundToInt() * 5)
        val lockFactor = twists.fold(1.0) { f, t -> f * t.lockFactor }
        val budget = (par / MOVES_PER_LOCK * lockFactor).toInt()
        val goal = if (rules.moveLimit > 0) "Build a $target within ${rules.moveLimit} moves." else "Build a $target."
        return Sector(
            number = 0, name = name,
            twist = twists.joinToString(" ") { it.line },
            briefing = listOf(place, twists.joinToString(" ") { it.line } + " " + goal),
            rules = rules, par = par, lockBudget = budget, kind = kind, id = id, seed = seed,
        )
    }

    /** Burned-out cells, never in a corner and never two side by side. */
    private fun deadCells(size: Int, n: Int, g: Rng): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>()
        while (out.size < n) {
            val c = g.below(size) to g.below(size)
            val corner = (c.first == 0 || c.first == size - 1) && (c.second == 0 || c.second == size - 1)
            val touches = out.any { kotlin.math.abs(it.first - c.first) + kotlin.math.abs(it.second - c.second) <= 1 }
            if (!corner && !touches) out.add(c)
        }
        return out
    }

    /** A 32-bit hash, so neighbouring dates give unrelated seeds. */
    private fun mix(x: Int): Int {
        var h = x * -0x61c88647
        h = h xor (h ushr 16)
        h *= 0x45d9f3b
        h = h xor (h ushr 16)
        return h
    }

    /** The id of the daily of [date], for the store and the calendar. */
    fun dailyId(date: LocalDate) = "D$date"
}
