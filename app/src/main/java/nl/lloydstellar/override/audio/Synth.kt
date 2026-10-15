package nl.lloydstellar.override.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh

/**
 * A small real-time synth that behaves like the Web Audio graph of
 * override.js: oscillators (sine, square, sawtooth, triangle) and white
 * noise, each through an optional biquad filter whose cutoff can glide,
 * with setValueAtTime / exponentialRampToValueAtTime envelopes, a tanh wave
 * shaper, a sustained pad, two buses (effects and music) and the browser's
 * master gain of 0.9 into a DynamicsCompressor (-14 dB, 4:1).
 *
 * Runs on its own audio thread. The music sequencer is called on that
 * thread once per block, so every note lands on the exact sample. The
 * AudioTrack is parked after a second of silence.
 */
class Synth {
    enum class Wave { SINE, SQUARE, SAW, TRIANGLE, NOISE }
    enum class Filter { NONE, LOWPASS, HIGHPASS, BANDPASS }
    enum class Bus { SFX, MUSIC }

    /**
     * Piecewise envelope: the first value from time 0, then exponential ramps
     * to each following (time, value), then it holds the last value. Times in
     * seconds from the start of the voice.
     */
    class Env(val times: DoubleArray, val values: DoubleArray) {
        companion object {
            fun const(v: Double) = Env(doubleArrayOf(0.0), doubleArrayOf(v))
            fun ramp(v0: Double, v1: Double, len: Double) =
                if (v0 == v1) const(v0) else Env(doubleArrayOf(0.0, len), doubleArrayOf(v0, v1))
        }
    }

    /* walks an Env sample by sample with one multiply per sample */
    private class Cursor(val env: Env) {
        var seg = 0
        var value = env.values[0]
        var mul = 1.0
        var left = 0L
        fun start(rate: Int) { seg = 0; value = env.values[0]; enter(rate) }
        private fun enter(rate: Int) {
            val t = env.times; val v = env.values
            if (seg + 1 >= t.size) { mul = 1.0; left = Long.MAX_VALUE; value = v[seg]; return }
            val n = max(1L, ((t[seg + 1] - t[seg]) * rate).toLong())
            mul = (v[seg + 1] / v[seg]).pow(1.0 / n)
            left = n
            value = v[seg]
        }
        fun next(rate: Int): Double {
            val out = value
            value *= mul
            if (--left <= 0) { seg++; enter(rate) }
            return out
        }
    }

    private class Biquad {
        var b0 = 1.0; var b1 = 0.0; var b2 = 0.0; var a1 = 0.0; var a2 = 0.0
        var x1 = 0.0; var x2 = 0.0; var y1 = 0.0; var y2 = 0.0

        /** Coefficients as the Web Audio spec computes them (Q in dB for low and high pass). */
        fun set(type: Filter, freq: Double, q: Double, rate: Int) {
            val f = freq.coerceIn(10.0, rate / 2.0 - 100)
            val w0 = 2 * PI * f / rate
            val cw = cos(w0)
            val alpha = when (type) {
                Filter.BANDPASS -> sin(w0) / (2 * max(q, 0.0001))
                else -> sin(w0) / (2 * 10.0.pow(q / 20))
            }
            val a0 = 1 + alpha
            when (type) {
                Filter.LOWPASS -> { b0 = (1 - cw) / 2; b1 = 1 - cw; b2 = (1 - cw) / 2 }
                Filter.HIGHPASS -> { b0 = (1 + cw) / 2; b1 = -(1 + cw); b2 = (1 + cw) / 2 }
                Filter.BANDPASS -> { b0 = alpha; b1 = 0.0; b2 = -alpha }
                Filter.NONE -> { b0 = a0; b1 = 0.0; b2 = 0.0 }
            }
            b0 /= a0; b1 /= a0; b2 /= a0
            a1 = -2 * cw / a0
            a2 = (1 - alpha) / a0
        }

        fun process(x: Double): Double {
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1; x1 = x; y2 = y1; y1 = y
            return y
        }
    }

    private class Voice(
        val wave: Wave,
        val freq: Cursor,
        val gain: Cursor,
        val filter: Filter,
        val cutoff: Cursor?,
        val q: Double,
        val drive: Double,
        val bus: Bus,
        var start: Long,
        val len: Long,
        val sample: FloatArray?,
        val sampleStep: Double,
    ) {
        var phase = 0.0
        var pos = 0.0
        var seed = 0x2545F491
        val bq = Biquad()
        var started = false
    }

    /** Three oscillators through a slowly breathing low-pass: the pad of the soundtrack. */
    private class Pad {
        val freq = doubleArrayOf(55.0, 55.0, 110.0)
        val target = doubleArrayOf(55.0, 55.0, 110.0)
        var smooth = 0.0001
        val phase = DoubleArray(3)
        var cutoff = 220.0
        var cutoffTarget = 220.0
        var lfo = 0.0
        val bq = Biquad()
        var tick = 0
    }

    private val rate = RATE
    private val block = 192
    private val pending = ConcurrentLinkedQueue<Voice>()
    private val voices = ArrayList<Voice>()
    private val lock = Object()
    @Volatile private var running = false
    private var thread: Thread? = null
    private var frame = 0L

    private val pad = Pad()
    @Volatile private var padTarget: DoubleArray? = null
    @Volatile private var padHard = false
    @Volatile private var padCutoff = 220.0

    /* music bus gain with setTargetAtTime(…, 0.5 s) smoothing */
    @Volatile var musicTarget = 0.0
    @Volatile var musicTimeConstant = 0.5
    private var musicGain = 0.0

    /** Called on the audio thread at the start of every block with the block's first frame. */
    @Volatile var sequencer: ((Long, Int) -> Unit)? = null

    /* DynamicsCompressor */
    private var env = 0.0
    private val compAttack = 1 - exp(-1.0 / (0.004 * RATE))
    private val compRelease = 1 - exp(-1.0 / (0.2 * RATE))
    val masterGain = 0.9

    /* tests render offline instead of through the AudioTrack */
    internal var offline = false

    val now: Long get() = frame

    fun start() {
        if (running) return
        running = true
        thread = Thread({ loop() }, "override-synth").also { it.start() }
    }

    fun stop() {
        running = false
        synchronized(lock) { lock.notifyAll() }
        thread?.join(300)
        thread = null
        pending.clear()
    }

    /**
     * A one-shot voice. [delay] and [len] in seconds; envelope times are
     * relative to the voice start. [cutoff] glides the filter frequency.
     */
    fun play(
        wave: Wave, freq: Env, gain: Env, len: Double, delay: Double = 0.0,
        filter: Filter = Filter.NONE, cutoff: Env? = null, q: Double = 1.0, drive: Double = 0.0, bus: Bus = Bus.SFX,
    ) {
        if (!running && !offline) return
        pending.add(voice(wave, freq, gain, len, (delay * rate).toLong(), filter, cutoff, q, drive, bus))
        synchronized(lock) { lock.notifyAll() }
    }

    /** Schedules a voice at an absolute frame; only from the sequencer (audio thread). */
    fun playAt(
        at: Long, wave: Wave, freq: Env, gain: Env, len: Double,
        filter: Filter = Filter.NONE, cutoff: Env? = null, q: Double = 1.0,
    ) {
        voices.add(voice(wave, freq, gain, len, at, filter, cutoff, q, 0.0, Bus.MUSIC))
    }

    /** Plays mono PCM at [srcRate] (the S.A.M. lines). */
    fun playSample(pcm: FloatArray, srcRate: Int, gain: Double, delay: Double = 0.0) {
        if (!running && !offline) return
        val len = (pcm.size.toDouble() / srcRate * rate).toLong()
        pending.add(Voice(Wave.SINE, Cursor(Env.const(1.0)), Cursor(Env.const(gain)), Filter.NONE, null, 1.0, 0.0,
            Bus.SFX, (delay * rate).toLong(), len, pcm, srcRate.toDouble() / rate))
        synchronized(lock) { lock.notifyAll() }
    }

    private fun voice(
        wave: Wave, freq: Env, gain: Env, len: Double, start: Long,
        filter: Filter, cutoff: Env?, q: Double, drive: Double, bus: Bus,
    ) = Voice(wave, Cursor(freq), Cursor(gain), filter, cutoff?.let { Cursor(it) }, q, drive, bus,
        start, (len * rate).toLong(), null, 0.0).also { it.seed = (System.nanoTime().toInt() or 1) }

    /** The pad's chord: three frequencies, gliding with time constant [hard] ? 10 ms : 600 ms. */
    fun setPad(f0: Double, f1: Double, f2: Double, hard: Boolean) {
        padHard = hard
        padTarget = doubleArrayOf(f0, f1, f2)
    }

    /** The pad's filter follows the music level with a 1.5 s time constant. */
    fun setPadCutoff(hz: Double) { padCutoff = hz }

    private fun drain() {
        while (true) {
            val v = pending.poll() ?: break
            v.start += frame
            voices.add(v)
        }
    }

    private fun loop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val minBuf = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build())
            .setAudioFormat(AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setSampleRate(rate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build())
            .setBufferSizeInBytes(max(minBuf, block * 4 * 4))
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        val buf = FloatArray(block)
        var playing = false
        var silentBlocks = 0
        try {
            while (running) {
                drain()
                val musicLive = musicTarget > 0.0 || musicGain > 0.0005
                if (voices.isEmpty() && !musicLive) {
                    silentBlocks++
                    if (silentBlocks > rate / block && playing) {
                        /* a second of silence: park the track to save battery */
                        track.pause(); track.flush(); playing = false
                    }
                    if (!playing) {
                        synchronized(lock) { if (pending.isEmpty() && running && musicTarget == 0.0) lock.wait(2000) }
                        continue
                    }
                } else {
                    silentBlocks = 0
                }
                if (!playing) { track.play(); playing = true }
                render(buf)
                track.write(buf, 0, block, AudioTrack.WRITE_BLOCKING)
            }
        } catch (_: InterruptedException) {
        } finally {
            track.stop()
            track.release()
        }
    }

    /** Renders [seconds] of everything scheduled so far, for tests. */
    internal fun renderOffline(seconds: Double): FloatArray {
        val total = (seconds * rate).toInt()
        val out = FloatArray(total)
        val buf = FloatArray(block)
        var pos = 0
        while (pos < total) {
            drain()
            render(buf)
            val n = minOf(block, total - pos)
            System.arraycopy(buf, 0, out, pos, n)
            pos += n
        }
        return out
    }

    private val sfx = DoubleArray(BLOCK)
    private val mus = DoubleArray(BLOCK)

    private fun render(out: FloatArray) {
        java.util.Arrays.fill(sfx, 0.0)
        java.util.Arrays.fill(mus, 0.0)
        val musicLive = musicTarget > 0.0 || musicGain > 0.0005
        if (musicLive) sequencer?.invoke(frame, out.size)

        val it = voices.iterator()
        while (it.hasNext()) {
            val v = it.next()
            val end = v.start + v.len
            if (end <= frame) { it.remove(); continue }
            val dst = if (v.bus == Bus.SFX) sfx else mus
            for (i in out.indices) {
                val f = frame + i
                if (f < v.start) continue
                if (f >= end) break
                if (!v.started) {
                    v.started = true
                    v.freq.start(rate); v.gain.start(rate); v.cutoff?.start(rate)
                    if (v.filter != Filter.NONE) v.bq.set(v.filter, (v.cutoff ?: v.freq).value, v.q, rate)
                }
                val g = v.gain.next(rate)
                val hz = v.freq.next(rate)
                var s = when {
                    v.sample != null -> {
                        val p = v.pos
                        val i0 = p.toInt()
                        v.pos += v.sampleStep
                        if (i0 + 1 < v.sample.size) {
                            val fr = p - i0
                            v.sample[i0] * (1 - fr) + v.sample[i0 + 1] * fr
                        } else 0.0
                    }
                    else -> osc(v, hz)
                }
                if (v.drive > 0) s = tanh(s.coerceIn(-1.0, 1.0) * v.drive)
                if (v.filter != Filter.NONE) {
                    val c = v.cutoff
                    if (c != null) {
                        val hzC = c.next(rate)
                        /* the cutoff glides: refresh the coefficients every 32 samples */
                        if (((f - v.start) and 31L) == 0L) v.bq.set(v.filter, hzC, v.q, rate)
                    }
                    s = v.bq.process(s)
                }
                dst[i] += s * g
            }
            if (frame + out.size >= end) it.remove()
        }

        if (musicLive) renderPad()

        val k = 1 - exp(-1.0 / (musicTimeConstant * rate))
        for (i in out.indices) {
            musicGain += (musicTarget - musicGain) * k
            val x = (sfx[i] + mus[i] * musicGain) * masterGain
            out[i] = compress(x).toFloat()
        }
        frame += out.size
    }

    private fun osc(v: Voice, hz: Double): Double {
        val dt = hz / rate
        return when (v.wave) {
            Wave.SINE -> { v.phase += dt; if (v.phase >= 1) v.phase -= 1.0; sin(2 * PI * v.phase) }
            Wave.SQUARE -> {
                v.phase += dt; if (v.phase >= 1) v.phase -= 1.0
                var sq = if (v.phase < 0.5) 1.0 else -1.0
                sq += polyBlep(v.phase, dt)
                sq -= polyBlep((v.phase + 0.5) % 1.0, dt)
                sq
            }
            Wave.SAW -> {
                v.phase += dt; if (v.phase >= 1) v.phase -= 1.0
                2 * v.phase - 1 - polyBlep(v.phase, dt)
            }
            Wave.TRIANGLE -> {
                v.phase += dt; if (v.phase >= 1) v.phase -= 1.0
                4 * abs(v.phase - 0.5) - 1
            }
            Wave.NOISE -> {
                var x = v.seed
                x = x xor (x shl 13); x = x xor (x ushr 17); x = x xor (x shl 5)
                v.seed = x
                x.toDouble() / Int.MAX_VALUE
            }
        }
    }

    private fun renderPad() {
        val p = pad
        padTarget?.let { t ->
            padTarget = null
            for (i in 0..2) p.target[i] = t[i]
            p.smooth = 1 - exp(-1.0 / ((if (padHard) 0.01 else 0.6) * rate))
        }
        p.cutoffTarget = padCutoff
        val cutK = 1 - exp(-1.0 / (1.5 * rate))
        val waves = arrayOf(Wave.SAW, Wave.SAW, Wave.SINE)
        for (i in 0 until BLOCK) {
            var s = 0.0
            for (o in 0..2) {
                p.freq[o] += (p.target[o] - p.freq[o]) * p.smooth
                val dt = p.freq[o] / rate
                p.phase[o] += dt
                if (p.phase[o] >= 1) p.phase[o] -= 1.0
                s += if (waves[o] == Wave.SAW) 2 * p.phase[o] - 1 - polyBlep(p.phase[o], dt) else sin(2 * PI * p.phase[o])
            }
            p.cutoff += (p.cutoffTarget - p.cutoff) * cutK
            p.lfo += 0.08 / rate
            if (p.lfo >= 1) p.lfo -= 1.0
            if ((p.tick++ and 31) == 0) p.bq.set(Filter.LOWPASS, p.cutoff + 90 * sin(2 * PI * p.lfo), 2.0, rate)
            mus[i] += p.bq.process(s) * 0.11
        }
    }

    /* DynamicsCompressor: -14 dB threshold, 4:1, 30 dB soft knee, 4 ms attack, 200 ms release */
    private fun compress(x: Double): Double {
        val a = abs(x)
        env += (a - env) * (if (a > env) compAttack else compRelease)
        val db = 20 * log10(max(env, 1e-6))
        val th = -14.0; val knee = 30.0; val ratio = 4.0
        val over = db - th
        val reduction = when {
            over <= -knee / 2 -> 0.0
            over >= knee / 2 -> over - over / ratio
            else -> (1 / ratio - 1) * -((over + knee / 2) * (over + knee / 2)) / (2 * knee)
        }
        val y = x * 10.0.pow(-reduction / 20) * MAKEUP
        /* a soft knee instead of hard clipping when a big chord stacks up */
        val ay = abs(y)
        return if (ay < 0.85) y else Math.signum(y) * (0.85 + 0.15 * tanh((ay - 0.85) / 0.15))
    }

    private fun polyBlep(t: Double, dt: Double): Double = when {
        t < dt -> { val x = t / dt; x + x - x * x - 1 }
        t > 1 - dt -> { val x = (t - 1) / dt; x * x + x + x + 1 }
        else -> 0.0
    }

    internal val sampleRate get() = rate

    companion object {
        const val RATE = 48000
        const val BLOCK = 192
        /* the browser's compressor adds makeup gain; about +4 dB at these settings */
        private val MAKEUP = 10.0.pow(4.0 / 20)
    }
}
