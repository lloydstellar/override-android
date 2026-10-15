package nl.lloydstellar.override

import nl.lloydstellar.override.engine.Engine
import nl.lloydstellar.override.engine.GameState
import kotlin.math.abs
import kotlin.math.ln

/**
 * A test player for the balance simulation. [Skill.CASUAL] looks one move
 * ahead and slips now and then, like someone playing on the bus; [Skill.GOOD]
 * looks two moves ahead over a few sampled spawns, like a player who knows
 * the corner strategy.
 */
class Bot(private val skill: Skill, seed: Long) {
    enum class Skill { CASUAL, GOOD }

    private val rnd = java.util.Random(seed)

    fun choose(e: Engine): Int? {
        val options = (0..3).mapNotNull { d -> e.copy().let { c -> if (c.move(d) != null) d to c else null } }
        if (options.isEmpty()) return null
        if (skill == Skill.CASUAL && rnd.nextDouble() < 0.12) return options[rnd.nextInt(options.size)].first
        return options.maxBy { (_, c) ->
            if (skill == Skill.CASUAL) eval(c) else lookAhead(c)
        }.first
    }

    /* average over a few spawns of the best reply */
    private fun lookAhead(e: Engine): Double {
        if (e.state != GameState.PLAYING) return eval(e)
        var sum = 0.0
        val samples = 3
        repeat(samples) {
            val s = e.copy()
            /* the spawn already happened in move(); vary the future with another rng state */
            s.rng.state = rnd.nextInt()
            val best = (0..3).maxOf { d -> s.copy().let { c -> if (c.move(d) != null) eval(c) else -1e6 } }
            sum += best
        }
        return sum / samples
    }

    private fun eval(e: Engine): Double {
        if (e.state == GameState.OVER) return -1e7
        if (e.state == GameState.WON) return 1e7
        val n = e.size
        var empty = 0
        var smooth = 0.0
        var mono = 0.0
        var corrupt = 0
        for (x in 0 until n) for (y in 0 until n) {
            val t = e.tileAt(x, y)
            if (t == null) { if (!e.isDead(x, y) && e.lockAt(x, y) == null) empty++; continue }
            if (t.corrupt) { corrupt++; continue }
            val v = ln(t.value.toDouble())
            /* weight towards the bottom-left corner, snake-ish */
            mono += v * ((n - 1 - x) + y) * 0.35
            e.tileAt(x + 1, y)?.let { if (!it.corrupt) smooth -= abs(v - ln(it.value.toDouble())) }
            e.tileAt(x, y + 1)?.let { if (!it.corrupt) smooth -= abs(v - ln(it.value.toDouble())) }
        }
        return empty * 2.7 + smooth + mono - corrupt * 2.0 - e.trace * 0.06 - e.locks.size * 2.5 + ln(e.score + 1.0)
    }
}
