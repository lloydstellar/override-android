package nl.lloydstellar.override

import nl.lloydstellar.override.engine.DeepGrid
import nl.lloydstellar.override.engine.Engine
import nl.lloydstellar.override.engine.GameState
import nl.lloydstellar.override.engine.Rng
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.Executors

/**
 * Plays the first fifty sectors of the Deep Grid with both bots and reports
 * per sector how often they clear it and earn the bars, so the stages and
 * their factor bands can be tuned. Slow; run with
 *
 *   ./gradlew :app:testDebugUnitTest --tests '*DeepGridSim*' -Psim=12
 *
 * (games per sector and bot).
 */
class DeepGridSim {
    private val pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())

    private class Outcome(val won: Boolean, val moves: Int, val locks: Int)

    @Test
    fun simulate() {
        val games = System.getProperty("override.sim")?.toIntOrNull() ?: 0
        assumeTrue("run with -Psim=<games per sector>", games > 0)
        val out = StringBuilder()
        out.append("sector                    twists                                              skill   clear  moves/par  2bars  3bars  locks  budget\n")
        val sectors = (DeepGrid.FIRST until DeepGrid.FIRST + 50).map { DeepGrid.sector(it) }
        for (s in sectors) for (skill in Bot.Skill.entries) {
            val res = (0 until games).map { g ->
                pool.submit<Outcome> {
                    val e = Engine()
                    e.newGame(s.rules, Rng(g * 7907 + s.number * 13))
                    val bot = Bot(skill, g * 31L + s.number)
                    while (e.state == GameState.PLAYING) e.move(bot.choose(e) ?: break)
                    Outcome(e.state == GameState.WON, e.moves, e.locksSuffered)
                }
            }.map { it.get() }
            val won = res.filter { it.won }
            val ratio = won.map { it.moves.toDouble() / s.par }.sorted()
            val two = won.count { it.moves <= s.par }
            val three = won.count { it.moves <= s.par && it.locks <= s.lockBudget }
            val locks = won.map { it.locks }.sorted()
            out.append("%-25s %-51s %-7s %4.0f%%  %8.2f  %4.0f%%  %4.0f%%  %5d  %6d\n".format(
                "${s.code} ${s.name} ${s.rules.target}", s.short.take(51), skill.name.lowercase(),
                100.0 * won.size / res.size, ratio.getOrElse(ratio.size / 2) { 0.0 }, 100.0 * two / res.size, 100.0 * three / res.size,
                locks.getOrElse(locks.size / 2) { 0 }, s.lockBudget))
        }
        pool.shutdown()
        print(out)
        val root = System.getProperty("override.root") ?: "."
        java.io.File(root, "tools/sim").mkdirs()
        java.io.File(root, "tools/sim/deep.txt").writeText(out.toString())
    }
}
