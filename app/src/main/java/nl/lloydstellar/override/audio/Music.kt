package nl.lloydstellar.override.audio

import nl.lloydstellar.override.audio.Synth.Env
import nl.lloydstellar.override.audio.Synth.Filter
import nl.lloydstellar.override.audio.Synth.Wave
import kotlin.math.pow

/**
 * The generative soundtrack of override.js: a dark electro loop whose tempo
 * and density follow the highest tile on the board.
 *
 * Level 0 (2..8): pad and a sub pulse. 1 (16..32): slow kick. 2 (64..128):
 * kick and hats. 3 (256..512): bassline. 4 (1024): the full pattern.
 * 5 (2048 and up): faster, with extra percussion.
 *
 * The browser schedules 120 ms ahead from a 25 ms timer; here the sequencer
 * runs on the audio thread and puts every step on its exact sample.
 */
object Music {
    private val BPM_BY_LEVEL = intArrayOf(92, 98, 106, 114, 122, 130)
    /* semitones above A1 */
    private val BASS_SEQ = intArrayOf(0, 0, 7, 0, 12, 0, 7, 10, 0, 0, 7, 3, 12, 10, 7, 5)
    private val CHORDS = arrayOf(intArrayOf(0, 3, 7, 10), intArrayOf(-2, 2, 5, 10), intArrayOf(-4, 0, 3, 7), intArrayOf(-5, 0, 3, 8))
    private const val ROOT = 55.0

    private val synth get() = Sfx.synth

    @Volatile var level = 0
    @Volatile private var enabled = true
    @Volatile private var active = false

    private var step = 0
    private var bar = 0
    private var chord = 0
    private var nextFrame = -1.0

    fun noteHz(semi: Int) = ROOT * 2.0.pow(semi / 12.0)

    fun levelFor(maxTile: Int) = when {
        maxTile >= 2048 -> 5
        maxTile >= 1024 -> 4
        maxTile >= 256 -> 3
        maxTile >= 64 -> 2
        maxTile >= 16 -> 1
        else -> 0
    }

    fun install() {
        synth.sequencer = ::onBlock
        setPadChord(0, true)
    }

    /** The music plays while a game is running, when both sound and music are on. */
    fun update(playing: Boolean, soundOn: Boolean, musicOn: Boolean) {
        enabled = soundOn && musicOn
        active = playing && enabled
        synth.musicTimeConstant = 0.5
        synth.musicTarget = if (active) 0.75 else 0.0
    }

    /** The app leaves the screen: fade out faster, like visibilitychange in the browser. */
    fun hide() {
        synth.musicTimeConstant = 0.2
        synth.musicTarget = 0.0
        active = false
    }

    private fun setPadChord(idx: Int, hard: Boolean) {
        val ch = CHORDS[idx % CHORDS.size]
        synth.setPad(noteHz(ch[0]), noteHz(ch[1]) * 1.003, noteHz(ch[2] + 12), hard)
    }

    /* audio thread */
    private fun onBlock(frame: Long, frames: Int) {
        val rate = Synth.RATE.toDouble()
        if (nextFrame < frame - frames) nextFrame = frame + 0.1 * rate
        val stepLen = 60.0 / BPM_BY_LEVEL[level] / 4
        while (nextFrame < frame + frames) {
            scheduleStep(step, nextFrame.toLong(), stepLen)
            nextFrame += stepLen * rate
            step = (step + 1) % 16
            if (step == 0) {
                bar++
                if (bar % 2 == 0) { chord = (chord + 1) % CHORDS.size; setPadChord(chord, false) }
            }
        }
        synth.setPadCutoff(200.0 + level * 160)
    }

    private fun kick(at: Long, vol: Double) =
        synth.playAt(at, Wave.SINE, Env.ramp(150.0, 42.0, 0.16), Env.ramp(vol, 0.0001, 0.26), 0.3)

    private fun hat(at: Long, vol: Double, open: Boolean) =
        synth.playAt(at, Wave.NOISE, Env.const(7000.0), Env.ramp(vol, 0.0001, if (open) 0.18 else 0.045), 0.2, Filter.HIGHPASS, q = 1.0)

    private fun snare(at: Long, vol: Double) {
        synth.playAt(at, Wave.NOISE, Env.const(1800.0), Env.ramp(vol, 0.0001, 0.14), 0.16, Filter.BANDPASS, q = 0.8)
        synth.playAt(at, Wave.SINE, Env.const(190.0), Env.ramp(vol * 0.7, 0.0001, 0.09), 0.1)
    }

    private fun bass(at: Long, semi: Int, len: Double, vol: Double, accent: Boolean) =
        synth.playAt(
            at, Wave.SAW, Env.const(noteHz(semi)), Env.ramp(vol, 0.0001, len), len + 0.02,
            Filter.LOWPASS, Env.ramp(if (accent) 1400.0 else 700.0, 120.0, len), if (accent) 10.0 else 6.0
        )

    private fun blip(at: Long, hz: Double, vol: Double) =
        synth.playAt(at, Wave.SQUARE, Env.const(hz), Env.ramp(vol, 0.0001, 0.06), 0.08)

    private fun scheduleStep(s: Int, at: Long, stepLen: Double) {
        val l = level
        /* sub pulse on the one, always */
        if (s == 0) bass(at, 0, stepLen * 3.5, 0.10, false)
        if (l >= 1) {
            if (s == 0 || s == 8) kick(at, 0.55)
            if (l >= 2 && (s == 6 || s == 10)) kick(at, 0.42)
            if (l >= 4 && s == 14) kick(at, 0.3)
        }
        if (l >= 2) {
            if (s % 2 == 0) hat(at, if (s % 4 == 2) 0.16 else 0.08, false)
            if (l >= 3 && s % 4 == 2) hat(at, 0.1, true)
            if (l >= 3 && (s == 4 || s == 12)) snare(at, 0.22)
        }
        if (l >= 3) {
            if (s % 2 == 0 || l >= 4) bass(at, BASS_SEQ[s], stepLen * 0.9, 0.09, s % 4 == 0)
        }
        if (l >= 4 && s % 4 == 3) blip(at, noteHz(CHORDS[chord][s % 4] + 36), 0.02)
        if (l >= 5 && (s == 3 || s == 11 || s == 13)) {
            snare(at, 0.12)
            blip(at, noteHz(24 + BASS_SEQ[(s + 5) % 16]), 0.025)
        }
    }
}
