package nl.lloydstellar.override.engine

/**
 * mulberry32: a tiny PRNG that gives the same numbers in Kotlin and in
 * JavaScript, so the parity harness can feed the browser's override.js the
 * exact draws the engine makes. The whole state is one Int, which makes it
 * easy to save with the board and to restore on a rollback (undoing a move
 * and making it again spawns the same tile, so a rollback cannot be used to
 * fish for a better spawn).
 */
class Rng(var state: Int) {
    /** A double in [0, 1), like Math.random(). */
    fun next(): Double {
        state += 0x6D2B79F5
        var t = state
        t = (t xor (t ushr 15)) * (t or 1)
        t = (t + ((t xor (t ushr 7)) * (t or 61))) xor t
        return ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL) / 4294967296.0
    }

    /** Math.floor(Math.random() * n) */
    fun below(n: Int): Int = (next() * n).toInt()

    companion object {
        fun seeded(): Rng = Rng((System.nanoTime() xor System.currentTimeMillis().shl(20)).toInt())
    }
}
