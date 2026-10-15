package nl.lloydstellar.override

import nl.lloydstellar.override.engine.Engine
import nl.lloydstellar.override.engine.GameState
import nl.lloydstellar.override.engine.Rng
import nl.lloydstellar.override.engine.Rules
import nl.lloydstellar.override.engine.WEB
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.Executors

/**
 * Compares trace settings for Classic: how often NEXUS-9 locks a cell and
 * what that does to the Override rate. Run with
 *
 *   ./gradlew :app:testDebugUnitTest --tests '*ClassicTuningSim*' -Psim=120
 */
class ClassicTuningSim {
    @Test
    fun compare() {
        val games = System.getProperty("override.sim")?.toIntOrNull() ?: 0
        assumeTrue("run with -Psim=<games per variant>", games > 0)
        val variants = listOf(
            "web (te2)" to WEB,
            "te4" to WEB.copy(traceEmpty = 4),
            "te5 after25" to WEB.copy(traceEmpty = 5, traceAfterLock = 25),
            "te5 after15" to WEB.copy(traceEmpty = 5, traceAfterLock = 15),
            "te4 node2" to WEB.copy(traceEmpty = 4, tracePerNode = 2),
            "te5" to WEB.copy(traceEmpty = 5),
        )
        val pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
        println("variant      skill   override  med.moves  locks/game  moves/lock  games w/ lock")
        for ((name, r) in variants) for (skill in Bot.Skill.entries) {
            val rules: Rules = r.copy(endless = false)
            val res = (0 until games).map { g ->
                pool.submit<IntArray> {
                    val e = Engine()
                    e.newGame(rules, Rng(g * 97 + 5))
                    val bot = Bot(skill, g.toLong())
                    while (e.state == GameState.PLAYING) e.move(bot.choose(e) ?: break)
                    intArrayOf(if (e.state == GameState.WON) 1 else 0, e.moves, e.locksSuffered)
                }
            }.map { it.get() }
            val moves = res.map { it[1] }.sorted()
            val locks = res.sumOf { it[2] }
            println("%-13s %-7s %6.0f%%  %9d  %10.2f  %10.0f  %10.0f%%".format(
                name, skill.name.lowercase(), 100.0 * res.count { it[0] == 1 } / games, moves[moves.size / 2],
                locks.toDouble() / games, if (locks > 0) res.sumOf { it[1] }.toDouble() / locks else 0.0,
                100.0 * res.count { it[2] > 0 } / games))
        }
        pool.shutdown()
    }
}
