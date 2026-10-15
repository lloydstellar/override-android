package nl.lloydstellar.override.audio

import android.content.Context
import nl.lloydstellar.override.audio.Synth.Env
import nl.lloydstellar.override.audio.Synth.Filter
import nl.lloydstellar.override.audio.Synth.Wave
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ln
import kotlin.random.Random

/**
 * The sound effects of override.js, one for one (same oscillators, filters,
 * pitches, volumes and delays), plus a quiet blip for the menus, which the
 * browser does not have.
 */
object Sfx {
    val synth = Synth()
    @Volatile var enabled = true
    private val voice = HashMap<String, FloatArray>()

    fun start() = synth.start()
    fun stop() = synth.stop()

    /** Loads the pre-rendered S.A.M. lines from assets/voice. */
    fun loadVoice(ctx: Context) {
        if (voice.isNotEmpty()) return
        for (name in listOf("override", "sector", "nexus", "breach", "purge")) {
            runCatching {
                val bytes = ctx.assets.open("voice/$name.wav").use { it.readBytes() }
                val bb = ByteBuffer.wrap(bytes, 44, bytes.size - 44).order(ByteOrder.LITTLE_ENDIAN)
                voice[name] = FloatArray((bytes.size - 44) / 2) { bb.short / 32768f }
            }
        }
    }

    /** "System override successful. A I core offline." and the other lines. */
    fun speak(line: String, delay: Double = 0.0) {
        if (!enabled) return
        voice[line]?.let { synth.playSample(it, 22050, 1.6, delay) }
    }

    private fun tone(type: Wave, f0: Double, f1: Double, ms: Double, vol: Double, delayMs: Double = 0.0) {
        if (!enabled) return
        val len = ms / 1000
        synth.play(type, Env.ramp(maxOf(f0, 1.0), maxOf(f1, 1.0), len), Env.ramp(vol, 0.0001, len), len + 0.02, delayMs / 1000)
    }

    private fun noiseHit(freq: Double, q: Double, ms: Double, vol: Double, delayMs: Double = 0.0, type: Filter = Filter.BANDPASS) {
        if (!enabled) return
        val len = ms / 1000
        synth.play(Wave.NOISE, Env.const(freq), Env.ramp(vol, 0.0001, len), len + 0.02, delayMs / 1000, filter = type, q = q)
    }

    /** A short filtered whoosh; the direction gives a slightly different pitch. */
    fun slide(dir: Int) {
        noiseHit(900.0 + dir * 120, 1.2, 110.0, 0.10)
        tone(Wave.SINE, 180.0, 120.0, 90.0, 0.03)
    }

    fun blocked() = tone(Wave.SQUARE, 140.0, 100.0, 80.0, 0.035)
    fun spawn() = tone(Wave.SQUARE, 1400.0, 1900.0, 45.0, 0.025)

    /** Electric spark plus a frequency sweep that climbs with the tile value. */
    fun merge(value: Int, idx: Int) {
        val n = ln(value.toDouble()) / ln(2.0)         /* 2 -> 1, 2048 -> 11 */
        val d = idx * 40.0
        noiseHit(2200 + n * 300, 4.0, 70.0, 0.10, d, Filter.HIGHPASS)
        tone(Wave.SAW, 160 + n * 55, 420 + n * 170, 140.0, 0.055, d)
        tone(Wave.SINE, 220 + n * 70, 220 + n * 70, 160.0, 0.035, d + 20)
        if (value >= 128) tone(Wave.TRIANGLE, 440 + n * 60, 880 + n * 120, 260.0, 0.04, d + 60)
        if (value >= 512) {
            tone(Wave.SQUARE, 55 * (n - 7), 55 * (n - 7), 320.0, 0.05, d + 40)
            noiseHit(300.0, 2.0, 300.0, 0.05, d + 40, Filter.LOWPASS)
        }
    }

    fun purge() {
        tone(Wave.SAW, 900.0, 60.0, 260.0, 0.07)
        noiseHit(600.0, 3.0, 240.0, 0.09, 0.0, Filter.LOWPASS)
        tone(Wave.SQUARE, 120.0, 40.0, 300.0, 0.06, 40.0)
    }

    fun emp() {
        noiseHit(80.0, 1.0, 500.0, 0.16, 0.0, Filter.LOWPASS)
        tone(Wave.SINE, 90.0, 28.0, 520.0, 0.12)
        tone(Wave.SAW, 2400.0, 300.0, 380.0, 0.04, 30.0)
    }

    fun corrupt() {
        tone(Wave.SQUARE, 660.0, 660.0, 90.0, 0.045); tone(Wave.SQUARE, 494.0, 494.0, 90.0, 0.045, 110.0)
        tone(Wave.SQUARE, 660.0, 660.0, 90.0, 0.045, 220.0); tone(Wave.SQUARE, 494.0, 494.0, 160.0, 0.045, 330.0)
    }

    fun lock() {
        noiseHit(1800.0, 8.0, 60.0, 0.09)
        tone(Wave.SQUARE, 220.0, 140.0, 120.0, 0.06, 20.0)
        tone(Wave.TRIANGLE, 90.0, 60.0, 220.0, 0.08, 60.0)
    }

    fun traceWarn() { tone(Wave.SQUARE, 880.0, 880.0, 60.0, 0.03); tone(Wave.SQUARE, 880.0, 880.0, 60.0, 0.03, 120.0) }

    /** A new session: the link comes up. */
    fun jackIn() {
        tone(Wave.SINE, 110.0, 880.0, 320.0, 0.05)
        noiseHit(1500.0, 1.0, 200.0, 0.05, 0.0, Filter.HIGHPASS)
    }

    fun unlock() {
        val notes = doubleArrayOf(523.0, 784.0, 1047.0, 1568.0)
        notes.forEachIndexed { i, f -> tone(Wave.TRIANGLE, f, f, 160.0, 0.04, i * 70.0) }
    }

    fun hiscore() {
        val notes = doubleArrayOf(523.0, 659.0, 784.0, 1047.0, 784.0, 1047.0)
        notes.forEachIndexed { i, f -> tone(Wave.SQUARE, f, f, 110.0, 0.04, i * 110.0) }
    }

    /** Strobe chord: a rising sweep, then an A minor stack. */
    fun win() {
        tone(Wave.SAW, 60.0, 1800.0, 900.0, 0.07)
        val chord = doubleArrayOf(110.0, 220.0, 262.0, 330.0, 440.0, 523.0, 659.0)
        chord.forEachIndexed { i, f -> tone(Wave.SAW, f, f, 2200.0, 0.025, 500.0 + i * 60) }
        for (k in 0 until 10) noiseHit(3000 + Random.nextDouble() * 3000, 6.0, 40.0, 0.06, 80.0 + k * 90, Filter.HIGHPASS)
    }

    /** SIGNAL LOST: a distorted analogue feedback peak and a dying hum. */
    fun signalLost() {
        if (!enabled) return
        synth.play(
            Wave.SAW,
            Env(doubleArrayOf(0.0, 0.6, 2.2), doubleArrayOf(400.0, 3200.0, 38.0)),
            Env(doubleArrayOf(0.0, 0.12, 0.7, 2.4), doubleArrayOf(0.0001, 0.16, 0.16, 0.0001)),
            2.5, drive = 8.0
        )
        noiseHit(2000.0, 0.7, 1800.0, 0.12, 100.0)
        for (c in 0 until 10) noiseHit(1000 + Random.nextDouble() * 5000, 3.0, 35.0, 0.07, 300.0 + c * 140 + Random.nextDouble() * 80)
    }

    /* ---- new in the app ---- */

    /** Menu feedback: a short, quiet blip in the colour of the terminal. */
    fun ui() = tone(Wave.SQUARE, 1200.0, 1500.0, 35.0, 0.018)

    /** A rollback: the tape spins backwards. */
    fun rollback() {
        tone(Wave.SAW, 1600.0, 180.0, 260.0, 0.05)
        noiseHit(2400.0, 1.5, 220.0, 0.06, 0.0, Filter.BANDPASS)
        tone(Wave.SINE, 300.0, 120.0, 200.0, 0.04, 60.0)
    }

    /** A sector bar lights up. */
    fun bar(i: Int) = tone(Wave.SQUARE, 660.0 * (1 + i * 0.25), 660.0 * (1 + i * 0.25), 120.0, 0.04, i * 160.0)
}
