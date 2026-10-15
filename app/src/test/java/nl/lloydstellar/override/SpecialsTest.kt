package nl.lloydstellar.override

import nl.lloydstellar.override.engine.CLASSIC
import nl.lloydstellar.override.engine.Dir
import nl.lloydstellar.override.engine.Engine
import nl.lloydstellar.override.engine.Kind
import nl.lloydstellar.override.engine.Rng
import nl.lloydstellar.override.engine.Rules
import nl.lloydstellar.override.engine.Special
import nl.lloydstellar.override.engine.SpecialSpawn
import nl.lloydstellar.override.engine.WEB
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The app's special tiles and the new lock rules, each on a hand-made board. */
class SpecialsTest {

    private class Cell(val x: Int, val y: Int, val value: Int, val kind: Kind = Kind.SIGNAL, val counter: Int = 0)

    private fun board(rules: Rules, vararg cells: Cell, trace: Int = 0, locks: List<IntArray> = emptyList(), suffered: Int = 0): Engine {
        val cs = JSONArray()
        cells.forEach {
            val c = JSONArray().put(it.x).put(it.y).put(it.value).put(if (it.kind == Kind.NODE) 1 else 0)
            if (it.kind != Kind.SIGNAL && it.kind != Kind.NODE) c.put(it.kind.name).put(it.counter)
            cs.put(c)
        }
        val ls = JSONArray()
        locks.forEach { ls.put(JSONArray().put(it[0]).put(it[1]).put(it[2])) }
        val o = JSONObject().put("cells", cs).put("locks", ls).put("score", 0).put("moves", 0).put("trace", trace)
            .put("won", false).put("keep", false).put("rng", 1234).put("locksSuffered", suffered)
        return Engine().also { it.restore(o, rules) }
    }

    private fun none(e: Engine, value: Int) = e.tiles().none { it.signal && it.value == value }

    @Test
    fun aPatchCableDoublesAnySignal() {
        val e = board(CLASSIC, Cell(0, 0, 0, Kind.PATCH), Cell(2, 0, 32))
        val r = e.move(Dir.LEFT)!!
        assertEquals(64, e.tileAt(0, 0)!!.value)
        assertEquals(1, r.patched.size)
        val f = board(CLASSIC, Cell(0, 0, 0, Kind.PATCH), Cell(2, 0, 512))
        f.move(Dir.LEFT)
        assertEquals("too big for the cable", 512, f.tileAt(1, 0)!!.value)
    }

    @Test
    fun aCasedSignalCracksFirstAndFusesOnTheLastHit() {
        val e = board(CLASSIC, Cell(0, 0, 8, Kind.CASED, 2), Cell(3, 0, 8))
        val r1 = e.move(Dir.LEFT)!!
        assertEquals(1, r1.cracks.size)
        assertEquals(Kind.CASED, e.tileAt(0, 0)!!.kind)
        assertEquals(1, e.tileAt(0, 0)!!.counter)
        assertEquals(8, e.tileAt(1, 0)!!.value)
        val before = e.score
        val r2 = e.move(Dir.LEFT)!!
        assertEquals(16, e.tileAt(0, 0)!!.value)
        assertTrue(e.tileAt(0, 0)!!.signal)
        assertEquals("double score", 32, e.score - before)
        assertEquals(1, r2.merges.size)
    }

    @Test
    fun anUnequalSignalJustStopsAgainstACase() {
        val e = board(CLASSIC, Cell(0, 0, 8, Kind.CASED, 2), Cell(3, 0, 4))
        val r = e.move(Dir.LEFT)!!
        assertEquals(0, r.cracks.size)
        assertEquals(2, e.tileAt(0, 0)!!.counter)
    }

    @Test
    fun aStaticWallStaysAndWearsDown() {
        val e = board(CLASSIC, Cell(3, 0, 0, Kind.WALL, 3), Cell(0, 0, 2))
        e.move(Dir.RIGHT)!!
        assertEquals(Kind.WALL, e.tileAt(3, 0)!!.kind)
        assertEquals(2, e.tileAt(2, 0)!!.value)

        val f = board(CLASSIC, Cell(0, 1, 0, Kind.WALL, 1), Cell(1, 0, 4), Cell(3, 0, 4))
        val r = f.move(Dir.LEFT)!!
        assertEquals(8, f.tileAt(0, 0)!!.value)
        assertEquals(1, r.wallsBroken.size)
        assertTrue(f.tileAt(0, 1) == null || f.tileAt(0, 1)!!.kind != Kind.WALL)
    }

    @Test
    fun aSurgeCapacitorClearsItsNeighbours() {
        val e = board(CLASSIC, Cell(1, 1, 0, Kind.CAPACITOR, 1), Cell(0, 1, 8), Cell(1, 0, 16), Cell(0, 0, 4), Cell(2, 1, 128), Cell(1, 2, 0, Kind.WALL, 2), Cell(3, 3, 2))
        val r = e.move(Dir.LEFT)!!
        assertEquals(1, r.discharges.size)
        assertTrue(none(e, 8) && none(e, 16))
        assertEquals("big signals ride it out", 128, e.tileAt(2, 1)?.value)
        assertEquals(1, e.tileAt(1, 2)!!.counter)
        assertTrue(e.tiles().none { it.kind == Kind.CAPACITOR })
    }

    @Test
    fun aSupplyDropCopiesItsNeighbour() {
        val rules = CLASSIC.copy(specials = listOf(SpecialSpawn(Special.SUPPLY, 1.0, 2)))
        var drops = 0
        for (seed in 1..40) {
            val e = Engine().also { it.newGame(rules, Rng(seed)) }
            repeat(30) {
                val r = (0..3).firstNotNullOfOrNull { d -> e.move(d) } ?: return@repeat
                val s = r.spawned ?: return@repeat
                if (r.supply) {
                    drops++
                    assertTrue(s.value in 8..Engine.SUPPLY_MAX)
                    assertTrue(listOf(s.x + 1 to s.y, s.x - 1 to s.y, s.x to s.y + 1, s.x to s.y - 1).any { (x, y) -> e.tileAt(x, y)?.let { it.signal && it.value == s.value && it !== s } == true })
                }
            }
        }
        assertTrue(drops > 0)
    }

    @Test
    fun aTraceBeaconTransmitsAndStaysAsANode() {
        val e = board(CLASSIC, Cell(0, 0, 4, Kind.BEACON, 1), Cell(3, 3, 2))
        val r = e.move(Dir.LEFT)!!
        assertEquals(1, r.transmitted.size)
        assertNotNull("trace hit 100, so NEXUS-9 locks", r.lock)
        assertTrue(e.tileAt(0, 0)!!.corrupt)
    }

    @Test
    fun aWormInfectsTheNewSpawn() {
        val e = board(CLASSIC, Cell(0, 0, 4, Kind.WORM, 1), Cell(3, 3, 2))
        val r = e.move(Dir.LEFT)!!
        val inf = r.infected
        assertNotNull(inf)
        assertTrue(inf!!.corrupt)
        assertEquals(Engine.WORM_CYCLE, e.tileAt(0, 0)!!.counter)
    }

    @Test
    fun anEqualSignalPurgesBeaconsAndWorms() {
        val e = board(CLASSIC, Cell(0, 0, 4, Kind.BEACON, 9), Cell(2, 0, 4), Cell(0, 1, 2, Kind.WORM, 5), Cell(3, 1, 2))
        val r = e.move(Dir.LEFT)!!
        assertEquals(2, r.purges.size)
        assertEquals(0, e.countHostile() - (if (r.spawned?.corrupt == true) 1 else 0))
    }

    @Test
    fun nexusLearnsButStopsAtTheMaximum() {
        val rules = CLASSIC.copy(lockGrowth = 1)
        val e = board(rules, Cell(0, 0, 2), trace = 99, suffered = 3)
        assertEquals(rules.lockTurns + 3, e.move(Dir.RIGHT)!!.lock!!.turns)
        val f = board(rules, Cell(0, 0, 2), trace = 99, suffered = 20)
        assertEquals(rules.lockMax, f.move(Dir.RIGHT)!!.lock!!.turns)
        val g = board(CLASSIC.copy(lockGrowth = 0), Cell(0, 0, 2), trace = 99, suffered = 20)
        assertEquals(CLASSIC.lockTurns, g.move(Dir.RIGHT)!!.lock!!.turns)
    }

    @Test
    fun aFusionNextToALockCracksIt() {
        val rules = CLASSIC.copy(lockCrack = 2)
        val e = board(rules, Cell(0, 1, 4), Cell(0, 3, 4), locks = listOf(intArrayOf(1, 0, 5)))
        e.move(Dir.UP)!!
        assertEquals(8, e.tileAt(0, 0)!!.value)
        assertEquals(5 - 2 - 1, e.lockAt(1, 0)!!.turns)
        val f = board(rules, Cell(0, 1, 4), Cell(0, 3, 4), locks = listOf(intArrayOf(1, 0, 2)))
        assertEquals(1, f.move(Dir.UP)!!.locksCracked)
        assertNull(f.lockAt(1, 0))
    }

    @Test
    fun aPinLockHoldsASignal() {
        val rules = CLASSIC.copy(pinChance = 1.0)
        val e = board(rules, Cell(0, 0, 2), Cell(0, 1, 8), trace = 99)
        val l = e.move(Dir.RIGHT)!!.lock!!
        assertTrue(l.pinned)
        assertNotNull(e.tileAt(l.x, l.y))
        val t = e.tileAt(l.x, l.y)!!
        e.move(if (l.x == 0) Dir.RIGHT else Dir.LEFT)
        assertEquals("a pinned signal does not move", t, e.tileAt(l.x, l.y))
    }

    @Test
    fun saveAndRestoreKeepsKindsAndCounters() {
        val e = board(CLASSIC, Cell(0, 0, 8, Kind.CASED, 2), Cell(1, 0, 0, Kind.WALL, 3), Cell(2, 2, 4, Kind.WORM, 6), Cell(3, 3, 0, Kind.CAPACITOR, 4))
        val f = Engine().also { it.restore(JSONObject(e.toJson().toString()), CLASSIC) }
        assertEquals(e.toJson().toString(), f.toJson().toString())
        assertEquals(Kind.WORM, f.tileAt(2, 2)!!.kind)
        assertEquals(6, f.tileAt(2, 2)!!.counter)
    }

    /** Lots of specials allowed: never two of a kind, never more than the maximum, and none without the rule. */
    @Test
    fun specialsKeepToTheirLimits() {
        val all = Special.entries.filter { it != Special.SUPPLY }.map { SpecialSpawn(it, 0.08, 2) }
        val rules = CLASSIC.copy(specials = all + SpecialSpawn(Special.SUPPLY, 0.05, 2), specialsMax = 2)
        var seen = 0
        for (seed in 1..60) {
            val e = Engine().also { it.newGame(rules, Rng(seed)) }
            val bot = Bot(Bot.Skill.entries.first(), seed.toLong())
            while (e.move(bot.choose(e) ?: break) != null) {
                val sp = e.tiles().filter { !it.signal && !it.corrupt }
                assertTrue(sp.size <= 2)
                assertEquals(sp.size, sp.map { it.kind }.toSet().size)
                seen += sp.size
            }
        }
        assertTrue(seen > 0)
        for (rs in listOf(WEB, CLASSIC)) for (seed in 1..20) {
            val e = Engine().also { it.newGame(rs, Rng(seed)) }
            val bot = Bot(Bot.Skill.entries.first(), seed.toLong())
            while (e.move(bot.choose(e) ?: break) != null) assertTrue(e.tiles().all { it.signal || it.corrupt })
        }
    }
}
