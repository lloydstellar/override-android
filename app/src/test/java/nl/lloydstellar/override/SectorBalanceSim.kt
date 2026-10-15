package nl.lloydstellar.override

import nl.lloydstellar.override.engine.Engine
import nl.lloydstellar.override.engine.GameState
import nl.lloydstellar.override.engine.Rng
import nl.lloydstellar.override.engine.CLASSIC
import nl.lloydstellar.override.engine.SECTORS
import nl.lloydstellar.override.engine.Sector
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.Executors

/**
 * Plays every sector with two bots and reports how often it is cleared, how
 * many moves that takes, and how often all three bars are earned. Slow, so
 * it only runs on request:
 *
 *   ./gradlew :app:testDebugUnitTest --tests '*SectorBalanceSim*' -Psim=200
 *
 * The report goes to tools/sim/report.txt.
 */
class SectorBalanceSim {

    @Test
    fun simulate() {
        val games = System.getProperty("override.sim")?.toIntOrNull() ?: 0
        assumeTrue("run with -Psim=<games per sector>", games > 0)
        val pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
        val out = StringBuilder()
        out.append("sector               skill   clear  median  p75   par  2bars  3bars  locks/game  budget  locks p40/p60\n")
        /* Classic (to 2048, then stop) as the yardstick */
        val classic = Sector(0, "CLASSIC", "", emptyList(), CLASSIC.copy(endless = false), 1000)
        for (s in SECTORS + classic) for (skill in Bot.Skill.entries) {
            val results = (0 until games).map { g ->
                pool.submit<IntArray> {
                    val e = Engine()
                    e.newGame(s.rules, Rng(g * 31 + s.number * 1009))
                    val bot = Bot(skill, g.toLong() * 7 + s.number)
                    while (e.state == GameState.PLAYING) {
                        val d = bot.choose(e) ?: break
                        e.move(d)
                    }
                    val cleared = if (e.state == GameState.WON) 1 else 0
                    intArrayOf(cleared, e.moves, e.locksSuffered)
                }
            }.map { it.get() }
            val cleared = results.filter { it[0] == 1 }
            val moves = cleared.map { it[1] }.sorted()
            val median = moves.getOrElse(moves.size / 2) { 0 }
            val p75 = moves.getOrElse(moves.size * 3 / 4) { 0 }
            val twoBars = cleared.count { it[1] <= s.par }
            val threeBars = cleared.count { it[1] <= s.par && it[2] <= s.lockBudget }
            val clearedLocks = cleared.map { it[2] }.sorted()
            val p40 = clearedLocks.getOrElse(clearedLocks.size * 2 / 5) { 0 }
            val p60 = clearedLocks.getOrElse(clearedLocks.size * 3 / 5) { 0 }
            val locks = results.sumOf { it[2] }.toDouble() / games
            out.append(
                "%-20s %-7s %4.0f%%  %6d %5d %5d  %4.0f%%  %4.0f%%  %10.2f  %6d  %3d/%d\n".format(
                    "${s.code} ${s.name}", skill.name.lowercase(), 100.0 * cleared.size / games, median, p75, s.par,
                    100.0 * twoBars / games, 100.0 * threeBars / games, locks, s.lockBudget, p40, p60
                )
            )
        }
        pool.shutdown()
        print(out)
        val root = System.getProperty("override.root") ?: "."
        File(root, "tools/sim").mkdirs()
        File(root, "tools/sim/report.txt").writeText(out.toString())
    }
}
