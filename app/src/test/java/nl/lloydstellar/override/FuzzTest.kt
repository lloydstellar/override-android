package nl.lloydstellar.override

import nl.lloydstellar.override.engine.CLASSIC
import nl.lloydstellar.override.engine.Directives
import nl.lloydstellar.override.engine.Engine
import nl.lloydstellar.override.engine.GameState
import nl.lloydstellar.override.engine.Kind
import nl.lloydstellar.override.engine.Rng
import nl.lloydstellar.override.engine.Rules
import nl.lloydstellar.override.engine.SECTORS
import nl.lloydstellar.override.engine.Special
import nl.lloydstellar.override.engine.SpecialSpawn
import nl.lloydstellar.override.engine.THREATS
import nl.lloydstellar.override.engine.classicRules
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate

/**
 * Plays thousands of games with random moves on every kind of rules, special
 * blocks at a much higher rate than in the real game, and checks after every
 * move that the board still makes sense, that a save restores exactly, and
 * that the game never gets stuck without ending.
 */
class FuzzTest {

    private val all = Special.entries.map { SpecialSpawn(it, 0.06, 2) }

    private val rulesets: List<Pair<String, Rules>> = buildList {
        add("crowded" to CLASSIC.copy(specials = all, specialsMax = 3, pinChance = 0.5, traceEmpty = 12, corruptFrom = 2, corruptChance = 0.15, corruptMax = 3))
        add("crowded 3x3" to CLASSIC.copy(size = 3, specials = all, pinChance = 0.5, traceEmpty = 15))
        add("crowded 5x5 dead" to CLASSIC.copy(size = 5, deadCells = listOf(1 to 2, 3 to 1), specials = all, specialsMax = 3, pinChance = 0.5, traceEmpty = 10))
        add("walls" to CLASSIC.copy(specials = listOf(SpecialSpawn(Special.WALL, 0.2, 2)), specialsMax = 3, pinChance = 1.0, traceEmpty = 20))
        THREATS.forEach { add("${it.short} new" to classicRules(it.level, 0)); add("${it.short} vet" to classicRules(it.level, 8192)) }
        SECTORS.forEach { add(it.code to it.rules) }
        (0 until 40).forEach { val d = Directives.daily(LocalDate.of(2026, 10, 1).plusDays(it.toLong())); add(d.id to d.rules) }
        (0 until 10).forEach { val w = Directives.weekly(LocalDate.of(2026, 10, 5).plusWeeks(it.toLong())); add(w.id to w.rules) }
    }

    private fun check(e: Engine, where: String) {
        val r = e.rules
        var specials = 0
        val kinds = HashMap<Kind, Int>()
        for (x in 0 until e.size) for (y in 0 until e.size) {
            val t = e.tileAt(x, y) ?: continue
            assertTrue("$where: tile knows its cell", t.x == x && t.y == y)
            assertTrue("$where: no tile on a dead cell", !e.isDead(x, y))
            when (t.kind) {
                Kind.SIGNAL, Kind.NODE, Kind.CASED, Kind.BEACON, Kind.WORM ->
                    assertTrue("$where: ${t.kind} value ${t.value}", t.value >= 2 && Integer.bitCount(t.value) == 1)
                else -> assertEquals("$where: ${t.kind} has no value", 0, t.value)
            }
            val max = when (t.kind) {
                Kind.CAPACITOR -> Engine.CAPACITOR_FUSE
                Kind.BEACON -> Engine.BEACON_COUNT
                Kind.WORM -> Engine.WORM_CYCLE
                Kind.CASED -> Engine.CASE_LAYERS
                Kind.WALL -> Engine.WALL_STRENGTH
                else -> 0
            }
            if (max > 0) assertTrue("$where: ${t.kind} counter ${t.counter}", t.counter in 1..max)
            if (!t.signal && !t.corrupt) { specials++; kinds.merge(t.kind, 1, Int::plus) }
        }
        assertTrue("$where: $specials specials", specials <= r.specialsMax)
        kinds.forEach { (k, n) -> assertEquals("$where: one $k at a time", 1, n) }
        assertTrue("$where: trace ${e.trace}", e.trace in 0..100)
        assertEquals("$where: locks on distinct cells", e.locks.size, e.locks.map { it.x to it.y }.toSet().size)
        e.locks.forEach { l ->
            assertTrue("$where: lock turns ${l.turns}", l.turns in 1..maxOf(r.lockMax, r.lockTurns))
            assertTrue("$where: lock on the board", e.within(l.x, l.y) && !e.isDead(l.x, l.y))
            assertEquals("$where: a pin holds a tile", l.pinned, e.tileAt(l.x, l.y) != null)
        }
        /* the promise of movesAvailable: some swipe changes the board */
        if (e.state != GameState.PLAYING) return
        val canMove = (0..3).any { e.copy().move(it) != null }
        assertEquals("$where: movesAvailable", canMove, e.movesAvailable())
        assertTrue("$where: playing but stuck", canMove)
    }

    @Test
    fun randomGamesStayConsistent() {
        var moves = 0
        for ((name, rules) in rulesets) for (seed in 1..12) {
            val e = Engine()
            e.newGame(rules.copy(endless = true), Rng(seed * 7919 + name.hashCode()))
            val pick = Rng(seed)
            check(e, "$name/$seed start")
            var n = 0
            while (e.state != GameState.OVER && n < 1500) {
                if (e.state == GameState.WON) e.continueEndless()
                if (e.state != GameState.PLAYING) break
                val where = "$name/$seed move $n"
                /* a save taken now must replay the next move exactly */
                val saved = JSONObject(e.toJson().toString())
                val dirs = (0..3).filter { e.copy().move(it) != null }
                if (dirs.isEmpty()) fail("$where: no move but still playing")
                val d = dirs[pick.below(dirs.size)]
                assertNotNull(e.move(d))
                val twin = Engine().also { it.restore(saved, e.rules) }
                twin.move(d)
                assertEquals("$where: restore replays", e.toJson().toString(), twin.toJson().toString().replace("\"id\":${twin.toJson().getInt("id")}", "\"id\":${e.toJson().getInt("id")}"))
                check(e, where)
                n++; moves++
            }
        }
        println("fuzz: $moves moves on ${rulesets.size} rule sets")
    }
}
