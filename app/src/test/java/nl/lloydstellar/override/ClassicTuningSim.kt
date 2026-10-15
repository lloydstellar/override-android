package nl.lloydstellar.override

import nl.lloydstellar.override.engine.Engine
import nl.lloydstellar.override.engine.GameState
import nl.lloydstellar.override.engine.Rng
import nl.lloydstellar.override.engine.Rules
import nl.lloydstellar.override.engine.WEB
import nl.lloydstellar.override.engine.CLASSIC
import nl.lloydstellar.override.engine.classicRules
import nl.lloydstellar.override.engine.Special
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
        fun without(k: Special) = classicRules(0, 4096).let { r -> r.copy(specials = r.specials.filter { it.kind != k }) }
        val vet = classicRules(0, 4096)
        fun scaled(f: Double, max: Int) = vet.copy(specialsMax = max, specials = vet.specials.map { it.copy(chance = it.chance * f) })
        val variants = listOf("classic" to CLASSIC, "std new" to classicRules(0, 0), "std vet" to vet, "calm vet" to classicRules(-1, 4096)) +
            Special.entries.map { k -> "only ${k.name.lowercase().take(5)}" to vet.copy(pinChance = 0.0, specials = vet.specials.filter { it.kind == k }) }
        val pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
        val out = StringBuilder()
        fun println(line: String) { kotlin.io.println(line); out.append(line).append('\n') }
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
        val root = System.getProperty("override.root") ?: "."
        java.io.File(root, "tools/sim").mkdirs()
        java.io.File(root, "tools/sim/classic.txt").writeText(out.toString())
    }
}
