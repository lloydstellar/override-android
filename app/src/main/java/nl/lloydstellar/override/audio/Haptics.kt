package nl.lloydstellar.override.audio

import android.content.Context
import android.os.VibrationEffect
import android.os.VibrationEffect.Composition
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Haptic feedback, new in the app. Uses the composition primitives (Pixel
 * and most phones since 2024) and falls back to the predefined effects.
 * Strength climbs with the tile value in steps of about 1.4x, the smallest
 * difference a thumb notices.
 */
object Haptics {
    @Volatile var enabled = true
    private var vibrator: Vibrator? = null
    private var rich = false

    fun init(ctx: Context) {
        val v = (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        vibrator = v
        rich = v.hasVibrator() && v.areAllPrimitivesSupported(
            Composition.PRIMITIVE_CLICK, Composition.PRIMITIVE_TICK, Composition.PRIMITIVE_THUD,
            Composition.PRIMITIVE_LOW_TICK, Composition.PRIMITIVE_QUICK_RISE, Composition.PRIMITIVE_QUICK_FALL,
            Composition.PRIMITIVE_SPIN
        )
    }

    private fun vibrate(effect: () -> VibrationEffect) {
        if (!enabled) return
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        try { v.vibrate(effect()) } catch (_: Exception) { }
    }

    private fun compose(fallback: Int, build: Composition.() -> Unit) = vibrate {
        if (rich) VibrationEffect.startComposition().apply(build).compose()
        else VibrationEffect.createPredefined(fallback)
    }

    /** A slide without a fusion: a light tick. */
    fun slide() = compose(VibrationEffect.EFFECT_TICK) { addPrimitive(Composition.PRIMITIVE_TICK, 0.3f) }

    /** Nothing moved: the system's own "rejected" feel. */
    fun blocked(view: View) {
        if (enabled) view.performHapticFeedback(HapticFeedbackConstants.REJECT)
    }

    /** The biggest fusion of a move: 0.35, 0.5, 0.7, 1.0 by size. */
    fun merge(value: Int) {
        val strength = when {
            value >= 512 -> 1f
            value >= 128 -> 0.7f
            value >= 32 -> 0.5f
            else -> 0.35f
        }
        compose(if (value >= 128) VibrationEffect.EFFECT_HEAVY_CLICK else VibrationEffect.EFFECT_CLICK) {
            if (value >= 512) addPrimitive(Composition.PRIMITIVE_THUD, strength)
            else addPrimitive(Composition.PRIMITIVE_CLICK, strength)
        }
    }

    fun purge() = compose(VibrationEffect.EFFECT_DOUBLE_CLICK) {
        addPrimitive(Composition.PRIMITIVE_CLICK, 0.8f)
        addPrimitive(Composition.PRIMITIVE_QUICK_FALL, 0.6f, 30)
    }

    fun emp() = compose(VibrationEffect.EFFECT_HEAVY_CLICK) {
        addPrimitive(Composition.PRIMITIVE_THUD, 1f)
        addPrimitive(Composition.PRIMITIVE_LOW_TICK, 0.7f, 70)
        addPrimitive(Composition.PRIMITIVE_LOW_TICK, 0.4f, 70)
    }

    fun corrupt() = compose(VibrationEffect.EFFECT_DOUBLE_CLICK) {
        addPrimitive(Composition.PRIMITIVE_LOW_TICK, 0.6f)
        addPrimitive(Composition.PRIMITIVE_LOW_TICK, 0.6f, 90)
    }

    fun lock() = compose(VibrationEffect.EFFECT_HEAVY_CLICK) {
        addPrimitive(Composition.PRIMITIVE_QUICK_RISE, 0.6f)
        addPrimitive(Composition.PRIMITIVE_THUD, 1f, 20)
    }

    fun traceWarn() = compose(VibrationEffect.EFFECT_DOUBLE_CLICK) {
        addPrimitive(Composition.PRIMITIVE_LOW_TICK, 0.7f)
        addPrimitive(Composition.PRIMITIVE_LOW_TICK, 0.45f, 90)
    }

    fun win() = compose(VibrationEffect.EFFECT_HEAVY_CLICK) {
        addPrimitive(Composition.PRIMITIVE_QUICK_RISE, 0.7f)
        addPrimitive(Composition.PRIMITIVE_SPIN, 1f, 40)
        addPrimitive(Composition.PRIMITIVE_THUD, 1f, 120)
    }

    fun signalLost() = compose(VibrationEffect.EFFECT_HEAVY_CLICK) {
        addPrimitive(Composition.PRIMITIVE_THUD, 1f)
        addPrimitive(Composition.PRIMITIVE_QUICK_FALL, 0.8f, 120)
        addPrimitive(Composition.PRIMITIVE_LOW_TICK, 0.4f, 200)
    }

    fun rollback() = compose(VibrationEffect.EFFECT_TICK) {
        addPrimitive(Composition.PRIMITIVE_QUICK_FALL, 0.7f)
        addPrimitive(Composition.PRIMITIVE_TICK, 0.5f, 60)
    }

    fun success() = compose(VibrationEffect.EFFECT_CLICK) {
        addPrimitive(Composition.PRIMITIVE_CLICK, 0.6f)
        addPrimitive(Composition.PRIMITIVE_CLICK, 0.9f, 80)
    }

    fun ui() = compose(VibrationEffect.EFFECT_TICK) { addPrimitive(Composition.PRIMITIVE_TICK, 0.5f) }
}
