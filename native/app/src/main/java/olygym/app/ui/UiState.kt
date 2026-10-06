package olygym.app.ui

import androidx.compose.runtime.Composable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import olygym.app.lib.I18nCore
import olygym.app.platform.NoSound
import olygym.app.platform.Sound
import olygym.app.rest.NoRestMirror
import olygym.app.rest.RestMirror
import olygym.app.rest.adoptMirror
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** A sheet, or a centred dialog. */
enum class SheetKind { SHEET, CENTER }

/** One entry of the sheet stack. The content is a composable that closes the sheet. */
data class Sheet(
    val id: Long,
    val kind: SheetKind = SheetKind.SHEET,
    val locked: Boolean = false,
    val content: @Composable (close: () -> Unit) -> Unit,
)

/** The rest countdown between sets. */
data class RestCountdown(
    val left: Double,
    val total: Double,
    val endsAt: Long,
    val forIdx: Int? = null,
    val label: String = "",
)

/** The work countdown during a timed set — what the set is actually held for. */
data class WorkCountdown(val left: Double, val total: Double, val endsAt: Long, val label: String = "")

/** Everything that belongs to the app's UI rather than to the training log. */
data class UiSnapshot(
    val sheets: List<Sheet> = emptyList(),
    val toast: String? = null,
    val rest: RestCountdown? = null,
    val work: WorkCountdown? = null,
    /** Bumped when the timer wants the screen to blink. The counter is the trigger, not a state. */
    val timerFlash: Int = 0,
)

/**
 * The ephemeral half of the app: the sheet stack, the toast and the two countdowns. A port of
 * frontend/src/store/useUI.js, built the way the store is — one holder, created at startup, read by
 * the screens — with the platform bits injected so all of this is a JVM test.
 *
 * The two timers mean opposite things and never run together: [startWork] stops any rest, and a work
 * countdown that ends records what was actually held rather than the target.
 */
class UiState(
    private val mirror: RestMirror = NoRestMirror,
    private val sound: Sound = NoSound,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val soundEnabled: () -> Boolean = { true },
    private val flashEnabled: () -> Boolean = { false },
    /** The running session's name, for the lock-screen card. Read at push time, like the web's. */
    private val sessionName: () -> String = { "" },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
    private val toastMillis: Long = 2200,
) {

    private val _state = MutableStateFlow(UiSnapshot())
    val state: StateFlow<UiSnapshot> = _state.asStateFlow()

    private var tickJob: Job? = null
    private var workDone: ((Double) -> Unit)? = null
    private var nextSheetId = 1L

    /* ---------------------------------------------------------------- sheets -- */

    fun openSheet(
        kind: SheetKind = SheetKind.SHEET,
        locked: Boolean = false,
        content: @Composable (close: () -> Unit) -> Unit,
    ): Long {
        val id = nextSheetId++
        _state.update { it.copy(sheets = it.sheets + Sheet(id, kind, locked, content)) }
        return id
    }

    fun closeSheet(id: Long) = _state.update { it.copy(sheets = it.sheets.filterNot { s -> s.id == id }) }

    fun closeAll() = _state.update { it.copy(sheets = emptyList()) }

    /** The topmost sheet, which is the one back closes and the one the keyboard belongs to. */
    fun topSheet(): Sheet? = _state.value.sheets.lastOrNull()

    /** Closes the top sheet unless it is locked; returns true when it closed one. */
    fun closeTop(): Boolean {
        val top = topSheet() ?: return false
        if (top.locked) return true
        closeSheet(top.id)
        return true
    }

    /* ----------------------------------------------------------------- toast -- */

    fun toast(message: String) {
        _state.update { it.copy(toast = message) }
        scope.launch {
            delay(toastMillis)
            _state.update { if (it.toast == message) it.copy(toast = null) else it }
        }
    }

    /* ------------------------------------------------------------ rest timer -- */

    /**
     * Start (or replace) a rest. A rest of zero is not a rest: the profile's "off" and a rest that
     * has already been taken off with the -15 button both mean the same thing here.
     */
    fun startRest(seconds: Double, forIdx: Int? = null, label: String = "") {
        stopRest()
        if (!(seconds > 0)) return
        val endsAt = clock() + (seconds * 1000).roundToLong()
        _state.update { it.copy(rest = RestCountdown(seconds, seconds, endsAt, forIdx, label)) }
        pushMirror()
        armTick()
    }

    fun addRest(seconds: Double) {
        val rest = _state.value.rest ?: return
        val left = rest.left + seconds
        // Taking off more than is left means "I am ready now" — the same as skipping, and it keeps a
        // negative duration out of the progress bar.
        if (left <= 0) return stopRest()
        val endsAt = rest.endsAt + (seconds * 1000).roundToLong()
        _state.update { it.copy(rest = rest.copy(left = left, total = rest.total + seconds, endsAt = endsAt)) }
        pushMirror()
    }

    /** The active list changed shape: keep the rest pointing at the same exercise. */
    fun shiftRestOwner(at: Int, delta: Int) {
        val rest = _state.value.rest ?: return
        val owner = rest.forIdx ?: return
        if (owner < at) return
        _state.update { it.copy(rest = rest.copy(forIdx = owner + delta)) }
    }

    fun stopRest() {
        tickJob?.cancel()
        tickJob = null
        if (_state.value.rest == null) return
        _state.update { it.copy(rest = null) }
        mirror.stop()
    }

    /** A rest that was controlled from the notification while the app was away. */
    fun adoptRest(rest: RestCountdown) {
        _state.update { it.copy(rest = rest) }
        armTick()
    }

    /**
     * On the way back: the mirror is the authority, because its +15s and Skip happened while this
     * app was not looking. A rest the notification has already ended or skipped stops here.
     */
    fun reconcileRest() {
        val rest = _state.value.rest
        val mirrorState = mirror.read()
        val adopted = adoptMirror(mirrorState, rest?.forIdx, clock())
        if (adopted == null) {
            if (rest != null) stopRest()
            return
        }
        _state.update {
            it.copy(
                rest = RestCountdown(
                    left = adopted.left,
                    total = adopted.total,
                    endsAt = adopted.endsAt,
                    forIdx = adopted.forIdx,
                    label = rest?.label ?: mirrorState.text,
                )
            )
        }
        armTick()
    }

    /* ------------------------------------------------------------ work timer -- */

    /** Time the set itself. onDone is called with what was actually held, early finish included. */
    fun startWork(seconds: Double, label: String = "", onDone: ((Double) -> Unit)? = null) {
        stopWork()
        stopRest()
        val total = maxOf(1.0, seconds.roundToInt().toDouble())
        workDone = onDone
        _state.update { it.copy(work = WorkCountdown(total, total, clock() + (total * 1000).roundToLong(), label)) }
        armTick()
    }

    fun finishWorkEarly() {
        val work = _state.value.work ?: return
        val elapsed = maxOf(1.0, work.total - work.left)
        val done = workDone
        sound.vibrate(30)
        stopWork()
        done?.invoke(elapsed)
    }

    fun stopWork() {
        workDone = null
        if (_state.value.work == null) return
        _state.update { it.copy(work = null) }
    }

    /* ------------------------------------------------------------- the tick -- */

    /** Ticking a set: a short high tone and the same buzz, when the profile wants sound. */
    fun setTick() {
        val on = soundEnabled()
        sound.beep(on, 1040, 0.12)
        sound.vibrate(30)
    }

    /* ----------------------------------------------------------------- flash -- */

    fun flashTimer() {
        if (!flashEnabled()) return
        _state.update { it.copy(timerFlash = it.timerFlash + 1) }
    }

    /* ------------------------------------------------------------------ tick -- */

    private fun armTick() {
        if (tickJob?.isActive == true) return
        tickJob = scope.launch {
            while (isActive) {
                delay(TICK_MILLIS)
                if (!tick()) break
            }
        }
    }

    /**
     * One second's worth of work. The countdown is always recomputed from the end time rather than
     * decremented, so a tick that is late — or that arrives after the process was frozen — lands on
     * the right number.
     *
     * Returns false when there is no timer left to run.
     */
    internal fun tick(): Boolean {
        val work = _state.value.work
        if (work != null) return tickWork(work)
        val rest = _state.value.rest ?: return false
        return tickRest(rest)
    }

    private fun tickRest(rest: RestCountdown): Boolean {
        // The mirror has the last word on the end time: the notification's own buttons move it, and
        // re-reading it every second is how they are picked up with no event bridge between them.
        val mirrorState = mirror.read()
        val endsAt = if (mirrorState.active && mirrorState.endsAtMs > 0) mirrorState.endsAtMs else rest.endsAt
        val left = maxOf(0.0, ceil((endsAt - clock()) / 1000.0))
        if (left == rest.left && endsAt == rest.endsAt) return true
        if (left <= 0.0) {
            alertRestOver()
            stopRest()
            return false
        }
        if (left <= 3) sound.beep(soundEnabled(), 660, 0.1)
        _state.update { it.copy(rest = rest.copy(left = left, endsAt = endsAt)) }
        return true
    }

    private fun tickWork(work: WorkCountdown): Boolean {
        val left = maxOf(0.0, ceil((work.endsAt - clock()) / 1000.0))
        if (left == work.left) return true
        if (left <= 0.0) {
            alert()
            val done = workDone
            stopWork()
            done?.invoke(work.total)
            return false
        }
        if (left <= 3) sound.beep(soundEnabled(), 660, 0.1)
        _state.update { it.copy(work = work.copy(left = left)) }
        return true
    }

    /** The rest is over: the tones, the buzz, the blink and the toast, in that order. */
    private fun alertRestOver() {
        alert()
        toast(I18nCore.t("Rest over — next set!"))
    }

    private fun alert() {
        val on = soundEnabled()
        sound.beep(on, 880, 0.15)
        sound.beep(on, 880, 0.15, 0.25)
        sound.beep(on, 1320, 0.4, 0.5)
        sound.vibrate(200)
        flashTimer()
    }

    private fun pushMirror() {
        val rest = _state.value.rest ?: return
        val name = sessionName()
        val big = listOf(name, rest.label).filter { it.isNotEmpty() }.joinToString(" · ")
        mirror.start(
            endsAtMs = rest.endsAt,
            totalSec = maxOf(1, rest.total.roundToInt()),
            forIdx = rest.forIdx,
            text = rest.label,
            sub = name,
            big = big,
        )
    }

    private companion object {
        /**
         * ponytail: 500ms rather than the web's 1000ms, so the number on screen is never more than
         * half a second behind — the lock-screen chronometer is exact either way, and a tick that
         * finds nothing changed costs a comparison.
         */
        const val TICK_MILLIS = 500L
    }
}
