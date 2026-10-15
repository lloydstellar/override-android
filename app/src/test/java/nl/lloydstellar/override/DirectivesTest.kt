package nl.lloydstellar.override

import nl.lloydstellar.override.engine.CLASSIC
import nl.lloydstellar.override.engine.Directives
import nl.lloydstellar.override.engine.Engine
import nl.lloydstellar.override.engine.GameState
import nl.lloydstellar.override.engine.MAX_THREAT
import nl.lloydstellar.override.engine.Rng
import nl.lloydstellar.override.engine.Sector
import nl.lloydstellar.override.engine.THREATS
import nl.lloydstellar.override.engine.WEB
import nl.lloydstellar.override.engine.threat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DirectivesTest {

    private fun board(e: Engine) = e.tiles().sortedBy { it.y * 10 + it.x }.joinToString { "${it.x},${it.y}=${it.value}" }

    /** Every player gets the same daily: same rules, same board, same spawns. */
    @Test
    fun dailyIsTheSameForEverybody() {
        val day = LocalDate.of(2026, 11, 3)
        val a = Directives.daily(day)
        val b = Directives.daily(day)
        assertEquals(a.id, b.id)
        assertEquals(a.rules, b.rules)
        assertEquals(a.par, b.par)
        val ea = Engine().also { it.newGame(a.rules, Rng(a.seed!!)) }
        val eb = Engine().also { it.newGame(b.rules, Rng(b.seed!!)) }
        repeat(40) { val d = (0..3).first { ea.copy().move(it) != null }; ea.move(d); eb.move(d) }
        assertEquals(board(ea), board(eb))
    }

    @Test
    fun neighbouringDaysDiffer() {
        val days = (0 until 60).map { Directives.daily(LocalDate.of(2026, 10, 1).plusDays(it.toLong())) }
        assertEquals("ids are unique", 60, days.map { it.id }.toSet().size)
        assertTrue("several twists in two months", days.map { it.name }.toSet().size >= 6)
        assertNotEquals(days[0].seed, days[1].seed)
    }

    /** A year of dailies and weeklies: every one starts on a playable board with sane numbers. */
    @Test
    fun everyDirectiveOfAYearIsPlayable() {
        val start = LocalDate.of(2026, 1, 1)
        val all = (0 until 366).map { Directives.daily(start.plusDays(it.toLong())) } +
            (0 until 53).map { Directives.weekly(start.plusWeeks(it.toLong())) }
        for (s in all) {
            val e = Engine()
            e.newGame(s.rules, Rng(s.seed!!))
            assertFalse("${s.id} starts over", e.state == GameState.OVER)
            assertTrue("${s.id} has tiles", e.tiles().isNotEmpty())
            assertTrue("${s.id} par ${s.par}", s.par in 20..2000)
            assertTrue("${s.id} budget", s.lockBudget >= 0)
            assertTrue("${s.id} target", Integer.bitCount(s.rules.target) == 1 && !s.rules.endless)
            s.rules.deadCells.forEach { (x, y) -> assertTrue(e.isDead(x, y)) }
            if (s.rules.moveLimit > 0) assertTrue("${s.id} limit above par", s.rules.moveLimit > s.par)
            assertEquals(if (s.id.startsWith("W")) Sector.Kind.WEEKLY else Sector.Kind.DAILY, s.kind)
        }
    }

    @Test
    fun aWeekHasOneWeekly() {
        val monday = LocalDate.of(2026, 10, 12)
        val ids = (0 until 7).map { Directives.weekly(monday.plusDays(it.toLong())).id }.toSet()
        assertEquals(1, ids.size)
        assertNotEquals(ids.first(), Directives.weekly(monday.plusDays(7)).id)
    }

    @Test
    fun theThreatLadderOnlyGetsHarder() {
        assertEquals(MAX_THREAT + 2, THREATS.size)
        assertEquals(WEB.traceEmpty, threat(-1).rules.traceEmpty)
        assertEquals(CLASSIC, threat(0).rules)
        for (l in 1..MAX_THREAT) {
            val a = threat(l - 1).rules
            val b = threat(l).rules
            assertTrue("T$l trace", b.traceEmpty >= a.traceEmpty)
            assertTrue("T$l nodes from", b.corruptFrom <= a.corruptFrom)
            assertTrue("T$l nodes max", b.corruptMax >= a.corruptMax)
            assertTrue("T$l locks", b.lockTurns >= a.lockTurns)
            assertTrue("T$l rollback", b.rollbackMax <= a.rollbackMax)
            assertNotEquals("T$l adds something", a, b)
        }
        assertEquals(0, threat(MAX_THREAT).rules.rollbackMax)
    }
}
