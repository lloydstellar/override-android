package nl.lloydstellar.override

import nl.lloydstellar.override.audio.Music
import nl.lloydstellar.override.audio.Sfx
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Renders every sound effect and every level of the soundtrack offline,
 * checks that nothing blows up (no NaN, no clipping, not silent) and writes
 * the result to tools/audio/ as WAV, to compare by ear with the browser.
 */
class AudioTest {
    private val synth = Sfx.synth.also { it.offline = true }
    private val outDir = File(System.getProperty("override.root") ?: ".", "tools/audio").also { it.mkdirs() }

    private fun render(name: String, seconds: Double, play: () -> Unit): FloatArray {
        play()
        val pcm = synth.renderOffline(seconds)
        var peak = 0f
        var sum = 0.0
        for (s in pcm) {
            assertTrue("$name: NaN", !s.isNaN())
            peak = maxOf(peak, abs(s))
            sum += s * s
        }
        val rms = sqrt(sum / pcm.size)
        println("%-14s peak %.3f  rms %.4f".format(name, peak, rms))
        assertTrue("$name: clipped ($peak)", peak <= 1.0f)
        assertTrue("$name: silent", peak > 0.005f)
        writeWav(File(outDir, "$name.wav"), pcm)
        return pcm
    }

    @Test
    fun effects() {
        Sfx.enabled = true
        render("slide", 0.4) { Sfx.slide(1) }
        render("merge-8", 0.5) { Sfx.merge(8, 0) }
        render("merge-256", 0.6) { Sfx.merge(256, 0); Sfx.merge(256, 1) }
        render("merge-2048", 0.8) { Sfx.merge(2048, 0) }
        render("purge", 0.6) { Sfx.purge() }
        render("emp", 0.8) { Sfx.emp() }
        render("corrupt", 0.7) { Sfx.corrupt() }
        render("lock", 0.5) { Sfx.lock() }
        render("blocked", 0.3) { Sfx.blocked() }
        render("jack-in", 0.6) { Sfx.jackIn() }
        render("win", 3.2) { Sfx.win() }
        render("signal-lost", 2.8) { Sfx.signalLost() }
        render("rollback", 0.5) { Sfx.rollback() }
        render("hiscore", 1.0) { Sfx.hiscore() }
        render("ui", 0.2) { Sfx.ui() }
    }

    @Test
    fun soundtrack() {
        Music.install()
        for (level in 0..5) {
            Music.level = level
            synth.musicTimeConstant = 0.01
            synth.musicTarget = 0.75
            render("music-level$level", 8.0) { }
            synth.musicTarget = 0.0
            synth.renderOffline(0.3)
        }
    }

    private fun writeWav(f: File, pcm: FloatArray) {
        val rate = 48000
        val data = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        pcm.forEach { data.putShort((it.coerceIn(-1f, 1f) * 32767).toInt().toShort()) }
        val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        h.put("RIFF".toByteArray()).putInt(36 + pcm.size * 2).put("WAVE".toByteArray())
        h.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
        h.put("data".toByteArray()).putInt(pcm.size * 2)
        f.writeBytes(h.array() + data.array())
    }
}
