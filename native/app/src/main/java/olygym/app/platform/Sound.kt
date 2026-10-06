package olygym.app.platform

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * The two things a finished set does: a tone and a buzz. Injected into the UI holder so the timer
 * logic around it — when a beep is earned, and how many — stays a JVM test.
 */
interface Sound {
    /** A tone at roughly this frequency, this long, optionally this many seconds from now. */
    fun beep(enabled: Boolean, freq: Int, seconds: Double, delaySeconds: Double = 0.0)

    fun vibrate(millis: Long)
}

/** What the tests and a device without an audio path get. */
object NoSound : Sound {
    override fun beep(enabled: Boolean, freq: Int, seconds: Double, delaySeconds: Double) = Unit
    override fun vibrate(millis: Long) = Unit
}

/**
 * The app's beeps, through the platform's tone generator.
 *
 * ponytail: the web builds an AudioContext and plays sine tones at 660, 880, 1040 and 1320 Hz.
 * ToneGenerator cannot be given a frequency — it plays one of its own tones — so the frequency is
 * mapped to the closest generator tone instead. What a set needs to say is "three seconds left"
 * versus "the rest is over", and two distinguishable tones say it. The alternative (an AudioTrack
 * with a generated sine) is the faithful one if a trace ever says this is not enough.
 */
class ToneSound(context: Context) : Sound {

    private val appContext = context.applicationContext
    private val vibrator: Vibrator? = resolveVibrator(appContext)
    private var tone: ToneGenerator? = null

    override fun beep(enabled: Boolean, freq: Int, seconds: Double, delaySeconds: Double) {
        if (!enabled || seconds <= 0.0) return
        val generator = generator() ?: return
        val durationMs = maxOf(60, (seconds * 1000).toInt())
        val type = when {
            freq < 700 -> ToneGenerator.TONE_PROP_BEEP
            freq < 1100 -> ToneGenerator.TONE_PROP_ACK
            else -> ToneGenerator.TONE_PROP_BEEP2
        }
        val play = { runCatching { generator.startTone(type, durationMs) }; Unit }
        if (delaySeconds <= 0.0) play() else android.os.Handler(android.os.Looper.getMainLooper())
            .postDelayed(play, (delaySeconds * 1000).toLong())
    }

    override fun vibrate(millis: Long) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(millis)
            }
        }
    }

    private fun generator(): ToneGenerator? {
        tone?.let { return it }
        val made = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, VOLUME) }.getOrNull() ?: return null
        tone = made
        return made
    }

    private companion object {
        const val VOLUME = 80

        fun resolveVibrator(context: Context): Vibrator? = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        }.getOrNull()
    }
}
