package olygym.app.rest

import android.content.Context
import kotlin.math.ceil

/**
 * The mirror as a screen reads it back: what the lock-screen notification is showing, if anything.
 * Never null — an app that has never run has one too, with active false.
 */
data class MirrorState(
    val active: Boolean = false,
    val endsAtMs: Long = 0L,
    val totalSec: Int = 0,
    val forIdx: Int? = null,
    val text: String = "",
    val sub: String = "",
    val big: String = "",
)

/**
 * The rest mirror, behind an interface so the timer's reading of it is a plain JVM test. The
 * shipping implementation writes the notification and its SharedPreferences mirror; a test uses
 * [NoRestMirror] plus its own recording stand-in.
 */
interface RestMirror {
    fun start(endsAtMs: Long, totalSec: Int, forIdx: Int?, text: String, sub: String, big: String)
    fun stop()
    fun read(): MirrorState
}

/** Where a rest ends up after the notification has had it while the app was away. */
data class AdoptedRest(val left: Double, val total: Double, val endsAt: Long, val forIdx: Int?)

/**
 * The rest to run after the notification was used — or null, which the caller reads as "stop".
 *
 * A port of adoptNativeState in frontend/src/lib/rest-notification.js: the mirror is the authority
 * because its +15s and Skip happened while the app was asleep. The exercise index falls back to the
 * one the in-app timer already had, since the mirror only carries it when a rest started with one.
 */
fun adoptMirror(mirror: MirrorState, forIdx: Int?, now: Long): AdoptedRest? {
    if (!mirror.active || mirror.endsAtMs <= now) return null
    return AdoptedRest(
        left = maxOf(0.0, ceil((mirror.endsAtMs - now) / 1000.0)),
        total = maxOf(1.0, mirror.totalSec.toDouble()),
        endsAt = mirror.endsAtMs,
        forIdx = mirror.forIdx ?: forIdx,
    )
}

/** The shipping mirror: the notification, its foreground service and the buttons behind it. */
class SystemRestMirror(private val context: Context) : RestMirror {

    override fun start(
        endsAtMs: Long,
        totalSec: Int,
        forIdx: Int?,
        text: String,
        sub: String,
        big: String,
    ) {
        RestTimer.start(
            context = context,
            endsAtMillis = endsAtMs,
            totalMillis = totalSec * 1000L,
            forIdx = forIdx,
            text = text,
            sub = sub,
            big = big,
        )
    }

    override fun stop() = RestTimer.stop(context)

    override fun read(): MirrorState = RestNotification.read(context)
}

/** No mirror at all: a profile with no rest timer, or a test that does not care. */
object NoRestMirror : RestMirror {
    override fun start(endsAtMs: Long, totalSec: Int, forIdx: Int?, text: String, sub: String, big: String) = Unit
    override fun stop() = Unit
    override fun read(): MirrorState = MirrorState()
}
