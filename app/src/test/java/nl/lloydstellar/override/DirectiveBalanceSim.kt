package nl.lloydstellar.override

import nl.lloydstellar.override.engine.Directives
import nl.lloydstellar.override.engine.Engine
import nl.lloydstellar.override.engine.GameState
import nl.lloydstellar.override.engine.Rng
import nl.lloydstellar.override.engine.Sector
import nl.lloydstellar.override.engine.THREATS
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.LocalDate
import java.util.concurrent.Executors

/**
 * Plays the real Daily and Weekly Directives of the coming months, and every
 * threat level of Classic, with both bots. Reports per twist how often the
 * bars are earned, so par and the lock limit can be tuned. Slow; run with
 *
 *   ./gradlew :app:testDebugUnitTest --tests '*DirectiveBalanceSim*' -Psim=12
 *
 * (games per directive and bot; threat levels get ten times as many).
 */
class DirectiveBalanceSim {
    private val pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())

    private class Outcome(val won: Boolean, val moves: Int, val locks: Int)

    private fun play(s: Sector, skill: Bot.Skill, seed: Int, game: Int) = pool.submit<Outcome> {
        val e = Engine()
        e.newGame(s.rules, Rng(seed))
        val bot = Bot(skill, game * 7919L + seed)
        while (e.state == GameState.PLAYING) e.move(bot.choose(e) ?: break)
        Outcome(e.state == GameState.WON, e.moves, e.locksSuffered)
    }

    private fun report(title: String, groups: Map<String, List<Sector>>, games: Int, out: StringBuilder) {
        out.append("\n$title\n")
        out.append("twist                       skill   n     clear  moves/par  2bars  3bars\n")
        for ((name, list) in groups) for (skill in Bot.Skill.entries) {
            val results = list.flatMap { s -> (0 until games).map { g -> s to play(s, skill, s.seed!!, g) } }.map { (s, f) -> s to f.get() }
            val won = results.filter { it.second.won }
            val ratio = won.map { it.second.moves.toDouble() / it.first.par }.sorted()
            val two = won.count { it.second.moves <= it.first.par }
            val three = won.count { it.second.moves <= it.first.par && it.second.locks <= it.first.lockBudget }
            out.append("%-27s %-7s %-5d %4.0f%%  %8.2f  %4.0f%%  %4.0f%%\n".format(
                name, skill.name.lowercase(), results.size, 100.0 * won.size / results.size,
                ratio.getOrElse(ratio.size / 2) { 0.0 }, 100.0 * two / results.size, 100.0 * three / results.size))
        }
    }

    @Test
    fun simulate() {
        val games = System.getProperty("override.sim")?.toIntOrNull() ?: 0
        assumeTrue("run with -Psim=<games per directive>", games > 0)
        val out = StringBuilder()
        val start = LocalDate.of(2026, 10, 15)

        val dailies = (0 until 120).map { Directives.daily(start.plusDays(it.toLong())) }
        report("DAILY (120 days)", dailies.groupBy { it.name.substringAfter("// ") }, games, out)

        val weeklies = (0 until 26).map { Directives.weekly(start.plusWeeks(it.toLong())) }
        report("WEEKLY (26 weeks)", weeklies.groupBy { "weekly" }, games, out)

        out.append("\nTHREAT LEVELS (Classic to 2048)\n")
        out.append("level                        skill   override  med.moves  locks/game\n")
        for (t in THREATS) for (skill in Bot.Skill.entries) {
            val rules = t.rules.copy(endless = false)
            val res = (0 until games * 10).map { g ->
                pool.submit<Outcome> {
                    val e = Engine()
                    e.newGame(rules, Rng(g * 131 + 9))
                    val bot = Bot(skill, g.toLong())
                    while (e.state == GameState.PLAYING) e.move(bot.choose(e) ?: break)
                    Outcome(e.state == GameState.WON, e.moves, e.locksSuffered)
                }
            }.map { it.get() }
            val moves = res.map { it.moves }.sorted()
            out.append("%-28s %-7s %6.0f%%  %9d  %10.2f\n".format(t.name.take(28), skill.name.lowercase(),
                100.0 * res.count { it.won } / res.size, moves[moves.size / 2], res.sumOf { it.locks }.toDouble() / res.size))
        }
        pool.shutdown()
        print(out)
        val root = System.getProperty("override.root") ?: "."
        java.io.File(root, "tools/sim").mkdirs()
        java.io.File(root, "tools/sim/directives.txt").writeText(out.toString())
    }
}
