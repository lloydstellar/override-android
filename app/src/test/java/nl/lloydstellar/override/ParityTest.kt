package nl.lloydstellar.override

import nl.lloydstellar.override.engine.CLASSIC
import nl.lloydstellar.override.engine.Engine
import nl.lloydstellar.override.engine.GameState
import nl.lloydstellar.override.engine.Rng
import nl.lloydstellar.override.engine.Rules
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Replays games recorded from the browser's override.js (tools/parity/run-js.mjs)
 * and checks that Engine ends every move with the same board, locks, score,
 * move count, trace and win state. The spawns come from the same seeded
 * mulberry32 on both sides.
 *
 * parity.json.gz is the real game. parity-stress.json.gz is the same JavaScript with
 * three numbers patched (faster trace, more Corrupted Nodes, the Override at
 * 128), so NEXUS-9 locks, EMP fries and endless mode occur in every game.
 */
class ParityTest {

    @Test fun classicMatchesTheBrowser() = replay("/parity.json.gz")

    @Test fun stressMatchesTheBrowser() = replay("/parity-stress.json.gz")

    private fun replay(resource: String) {
        val text = java.util.zip.GZIPInputStream(javaClass.getResourceAsStream(resource)!!).bufferedReader().readText()
        val root = JSONObject(text)
        val r = root.getJSONObject("rules")
        val rules: Rules = CLASSIC.copy(
            traceEmpty = r.optInt("traceEmpty", CLASSIC.traceEmpty),
            corruptChance = r.optDouble("corruptChance", CLASSIC.corruptChance),
            target = r.optInt("target", CLASSIC.target),
        )
        val games = root.getJSONArray("games")
        var moves = 0
        for (g in 0 until games.length()) {
            val game = games.getJSONObject(g)
            val engine = Engine()
            engine.newGame(rules, Rng(game.getInt("seed")))
            val where = "$resource game $g"
            check(engine, game.getJSONObject("init"), "$where init")
            val steps = game.getJSONArray("steps")
            for (i in 0 until steps.length()) {
                val step = steps.getJSONObject(i)
                val at = "$where step $i"
                when {
                    step.optBoolean("cont") -> engine.continueEndless()
                    step.optBoolean("blocked") -> { assertNull("$at should be blocked", engine.move(step.getInt("dir"))); continue }
                    else -> { assertTrue("$at should move", engine.move(step.getInt("dir")) != null); moves++ }
                }
                val state = step.opt("state")
                if (state == null || state == JSONObject.NULL) {
                    assertEquals("$at: the browser ended the game", GameState.OVER, engine.state)
                } else {
                    check(engine, state as JSONObject, at)
                }
            }
        }
        println("$resource: ${games.length()} games, $moves moves identical")
    }

    private fun check(e: Engine, s: JSONObject, at: String) {
        val want = sortedSetOf<String>()
        val cells = s.getJSONArray("cells")
        for (i in 0 until cells.length()) {
            val c = cells.getJSONArray(i)
            want.add("${c.getInt(0)},${c.getInt(1)}=${c.getInt(2)}${if (c.getInt(3) == 1) "!" else ""}")
        }
        val got = sortedSetOf<String>()
        e.eachTile { got.add("${it.x},${it.y}=${it.value}${if (it.corrupt) "!" else ""}") }
        assertEquals("$at cells", want, got)

        val wantLocks = sortedSetOf<String>()
        val ls = s.getJSONArray("locks")
        for (i in 0 until ls.length()) {
            val l = ls.getJSONObject(i)
            wantLocks.add("${l.getInt("x")},${l.getInt("y")}:${l.getInt("turns")}")
        }
        assertEquals("$at locks", wantLocks, e.locks.map { "${it.x},${it.y}:${it.turns}" }.toSortedSet())
        assertEquals("$at score", s.getInt("score"), e.score)
        assertEquals("$at moves", s.getInt("moves"), e.moves)
        assertEquals("$at trace", s.getInt("trace"), e.trace)
        assertEquals("$at won", s.getBoolean("won"), e.won)
        assertEquals("$at keep playing", s.getBoolean("keep"), e.keepPlaying)
        val wantState = if (s.getString("state") == "won") GameState.WON else GameState.PLAYING
        assertEquals("$at state", wantState, e.state)
    }
}
