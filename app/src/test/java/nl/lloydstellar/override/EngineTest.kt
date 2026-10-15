package nl.lloydstellar.override

import nl.lloydstellar.override.engine.CLASSIC
import nl.lloydstellar.override.engine.Dir
import nl.lloydstellar.override.engine.EndReason
import nl.lloydstellar.override.engine.Engine
import nl.lloydstellar.override.engine.GameState
import nl.lloydstellar.override.engine.Rng
import nl.lloydstellar.override.engine.SECTORS
import nl.lloydstellar.override.engine.StartTile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineTest {

    private fun board(e: Engine): String {
        val sb = StringBuilder()
        for (y in 0 until e.size) {
            for (x in 0 until e.size) {
                val t = e.tileAt(x, y)
                sb.append(if (e.isDead(x, y)) "#" else t?.let { (if (it.corrupt) "!" else "") + it.value } ?: ".").append(' ')
            }
            sb.append('\n')
        }
        return sb.toString()
    }

    /** Undoing a move and making it again spawns the same tile: a rollback cannot fish for spawns. */
    @Test
    fun rollbackRestoresTheExactFuture() {
        val e = Engine()
        e.newGame(CLASSIC, Rng(42))
        repeat(30) { listOf(Dir.DOWN, Dir.LEFT, Dir.RIGHT, Dir.UP).firstNotNullOfOrNull { d -> e.move(d) } }
        val saved = e.toJson()
        val dir = (0..3).first { e.copy().move(it) != null }
        e.move(dir)
        val after = board(e)
        e.restore(saved, CLASSIC)
        assertNotNull(e.move(dir))
        assertEquals(after, board(e))
    }

    @Test
    fun saveAndRestoreKeepsEverything() {
        val e = Engine()
        e.newGame(SECTORS[3].rules, Rng(7))
        repeat(40) { listOf(Dir.DOWN, Dir.LEFT, Dir.RIGHT, Dir.UP).firstNotNullOfOrNull { d -> e.move(d) } }
        val o = e.toJson()
        val f = Engine()
        f.restore(org.json.JSONObject(o.toString()), SECTORS[3].rules)
        assertEquals(board(e), board(f))
        assertEquals(e.score, f.score)
        assertEquals(e.trace, f.trace)
        assertEquals(e.moves, f.moves)
        assertEquals(e.rng.state, f.rng.state)
        assertEquals(e.toJson().toString(), f.toJson().toString())
    }

    @Test
    fun deadCellsStopTiles() {
        val rules = CLASSIC.copy(deadCells = listOf(1 to 0), startTiles = listOf(StartTile(3, 0, 8)))
        val e = Engine()
        /* clear the random spawns away from row 0 by trying seeds until row 0 holds only the start tile */
        var seed = 1
        do {
            e.newGame(rules, Rng(seed++))
        } while ((0 until 4).count { e.tileAt(it, 0) != null } != 1)
        e.move(Dir.LEFT)
        assertEquals(8, e.tileAt(2, 0)?.value)
        assertNull(e.tileAt(1, 0))
        assertTrue(e.isDead(1, 0))
    }

    @Test
    fun moveLimitEndsTheSector() {
        val e = Engine()
        e.newGame(CLASSIC.copy(endless = false, target = 1 shl 20, moveLimit = 5), Rng(3))
        var n = 0
        while (e.state == GameState.PLAYING && n < 50) {
            (0..3).firstNotNullOfOrNull { e.move(it) } ?: break
            n++
        }
        assertEquals(GameState.OVER, e.state)
        assertEquals(EndReason.OUT_OF_MOVES, e.endReason)
        assertEquals(5, e.moves)
    }

    @Test
    fun winStopsAtTheTargetAndEndlessContinues() {
        val e = Engine()
        e.newGame(CLASSIC.copy(target = 8, startTiles = listOf(StartTile(0, 3, 4), StartTile(1, 3, 4))), Rng(11))
        /* slide left until the two fours meet */
        repeat(5) { if (e.state == GameState.PLAYING) e.move(Dir.LEFT) }
        assertTrue(e.won)
        assertEquals(GameState.WON, e.state)
        assertNull(e.move(Dir.RIGHT))
        e.continueEndless()
        assertTrue(e.keepPlaying)
        assertTrue(e.state == GameState.PLAYING || e.state == GameState.OVER)
    }

    @Test
    fun everySectorStartsCleanly() {
        for (s in SECTORS) {
            val e = Engine()
            e.newGame(s.rules, Rng(s.number))
            assertEquals("${s.name} size", s.rules.size, e.size)
            assertEquals("${s.name} tiles", 2 + s.rules.startTiles.size, e.tiles().size)
            assertFalse("${s.name} starts over", e.state == GameState.OVER)
            s.rules.deadCells.forEach { (x, y) -> assertTrue(e.isDead(x, y)); assertNull(e.tileAt(x, y)) }
            assertTrue("${s.name} target is a power of two", Integer.bitCount(s.rules.target) == 1)
            assertTrue("${s.name} par", s.par > 0)
        }
        assertEquals((1..SECTORS.size).toList(), SECTORS.map { it.number })
    }

    @Test
    fun corruptedNodesNeverFuseUpward() {
        val e = Engine()
        val rules = CLASSIC.copy(corruptFrom = 2, corruptChance = 1.0, corruptMax = 16)
        e.newGame(rules, Rng(5))
        repeat(200) { (0..3).firstNotNullOfOrNull { d -> e.move(d) } }
        e.tiles().filter { it.corrupt }.forEach { assertTrue("corrupt ${it.value}", it.value in rules.corruptValues) }
    }
}
