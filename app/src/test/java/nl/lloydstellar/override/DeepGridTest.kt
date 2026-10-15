package nl.lloydstellar.override

import nl.lloydstellar.override.engine.DeepGrid
import nl.lloydstellar.override.engine.Directives
import nl.lloydstellar.override.engine.Sector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DeepGridTest {
    private val range = DeepGrid.FIRST until DeepGrid.FIRST + 400

    @Test
    fun sectorsComeFromTheirNumber() {
        for (n in range step 7) {
            val a = DeepGrid.sector(n)
            val b = DeepGrid.sector(n)
            assertEquals(a.short, b.short)
            assertEquals(a.rules, b.rules)
            assertEquals(a.par, b.par)
            assertEquals("S$n", a.id)
            assertEquals(n, a.number)
            assertEquals(Sector.Kind.DEEP, a.kind)
            /* random spawns, like the campaign */
            assertNull(a.seed)
        }
    }

    @Test
    fun twistsFitTogether() {
        for (n in range) {
            val s = DeepGrid.sector(n)
            val r = s.rules
            assertTrue(r.size in 4..5)
            assertTrue("$n ${s.short}", r.deadCells.size <= 2)
            assertTrue(r.deadCells.distinct().size == r.deadCells.size)
            assertTrue(r.target == 1024 || r.target == 2048)
            assertTrue(s.par > 0)
            assertTrue(!r.endless)
            val names = s.short.split(" + ")
            assertEquals(names.size, names.distinct().size)
            assertTrue("$n ${s.short}", !(("ROGUE CODE" in names) && ("SWARM" in names)))
        }
    }

    @Test
    fun itGetsHarderWithDepth() {
        assertEquals(1024, DeepGrid.sector(13).rules.target)
        assertEquals(0.0, DeepGrid.sector(13).rules.pinChance, 0.0)
        assertTrue(DeepGrid.sector(18).rules.pinChance > 0)
        assertEquals(2048, DeepGrid.sector(28).rules.target)
        assertEquals(2048, DeepGrid.sector(300).rules.target)
    }

    @Test
    fun namesDoNotRepeatSoon() {
        val names = (DeepGrid.FIRST until DeepGrid.FIRST + 256).map { DeepGrid.name(it) }
        assertEquals(names.size, names.distinct().size)
        assertNotEquals(DeepGrid.name(13), DeepGrid.name(14))
    }

    @Test
    fun directivesKeepTheirRules() {
        /* the Deep Grid shares the twists of the directives; opening them up must not change a single directive */
        val d = Directives.daily(LocalDate.of(2026, 10, 15))
        assertEquals("DAILY // DEAD PIXEL", d.name)
        assertEquals("WEEKLY // LONG LOCKS + WORM SIGN", Directives.weekly(LocalDate.of(2026, 10, 15)).name)
    }
}
