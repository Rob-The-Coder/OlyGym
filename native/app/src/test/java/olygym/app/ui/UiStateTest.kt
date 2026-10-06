package olygym.app.ui

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import olygym.app.platform.Sound
import olygym.app.rest.MirrorState
import olygym.app.rest.RestMirror
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The UI holder: two countdowns, the sheet stack and the toast, driven by a fixed clock so every
 * case is exact. The tick is called by hand — the coroutine that calls it in the app is the same
 * function on a timer.
 */
class UiStateTest {

    /** A dispatcher that never runs anything: the timer loop is driven by hand instead. */
    private object Paused : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) = Unit
    }

    private class FakeMirror : RestMirror {
        var now: MirrorState = MirrorState()
        var starts = 0
        var stops = 0
        var lastEndsAt = 0L
        var lastTotalSec = 0
        var lastForIdx: Int? = null
        var lastText = ""
        var lastSub = ""
        var lastBig = ""

        override fun start(endsAtMs: Long, totalSec: Int, forIdx: Int?, text: String, sub: String, big: String) {
            starts++
            lastEndsAt = endsAtMs
            lastTotalSec = totalSec
            lastForIdx = forIdx
            lastText = text
            lastSub = sub
            lastBig = big
            now = MirrorState(active = true, endsAtMs = endsAtMs, totalSec = totalSec, forIdx = forIdx, text = text, sub = sub, big = big)
        }

        override fun stop() {
            stops++
            now = MirrorState()
        }

        override fun read(): MirrorState = now
    }

    private class FakeSound : Sound {
        val beeps = mutableListOf<Pair<Int, Double>>()
        var vibrates = 0

        override fun beep(enabled: Boolean, freq: Int, seconds: Double, delaySeconds: Double) {
            if (enabled) beeps += freq to delaySeconds
        }

        override fun vibrate(millis: Long) {
            vibrates++
        }
    }

    private var now = 1_000_000L
    private val mirror = FakeMirror()
    private val sound = FakeSound()
    private var soundOn = true
    private var flashOn = false

    private fun ui(toastMillis: Long = 2200) = UiState(
        mirror = mirror,
        sound = sound,
        clock = { now },
        soundEnabled = { soundOn },
        flashEnabled = { flashOn },
        sessionName = { "Snatch Day" },
        scope = CoroutineScope(SupervisorJob() + Paused),
        toastMillis = toastMillis,
    )

    @Test
    fun `a rest of zero is not a rest`() {
        val u = ui()
        u.startRest(0.0)
        assertNull(u.state.value.rest)
        assertEquals(0, mirror.starts)
    }

    @Test
    fun `starting a rest writes the mirror and replaces a running one`() {
        val u = ui()
        u.startRest(60.0, forIdx = 2, label = "Set 2 of 5")
        assertEquals(60.0, u.state.value.rest?.left)
        assertEquals(now + 60_000, mirror.lastEndsAt)
        assertEquals(60, mirror.lastTotalSec)
        assertEquals(2, mirror.lastForIdx)
        assertEquals("Set 2 of 5", mirror.lastText)
        assertEquals("Snatch Day", mirror.lastSub)
        assertEquals("Snatch Day · Set 2 of 5", mirror.lastBig)

        u.startRest(90.0)
        assertEquals(90.0, u.state.value.rest?.left)
        assertEquals(2, mirror.starts)
        assertEquals(now + 90_000, mirror.lastEndsAt)
    }

    @Test
    fun `adding time moves the end and rewrites the mirror`() {
        val u = ui()
        u.startRest(60.0)
        u.addRest(15.0)
        assertEquals(75.0, u.state.value.rest?.left)
        assertEquals(75.0, u.state.value.rest?.total)
        assertEquals(now + 75_000, mirror.lastEndsAt)
        assertEquals(2, mirror.starts)
    }

    @Test
    fun `taking off more than is left means ready now`() {
        val u = ui()
        u.startRest(30.0)
        u.addRest(-60.0)
        assertNull(u.state.value.rest)
        assertEquals(1, mirror.stops)
    }

    @Test
    fun `shiftRestOwner follows the exercise it belongs to`() {
        val u = ui()
        u.startRest(90.0, forIdx = 2)
        u.shiftRestOwner(at = 3, delta = -1)
        assertEquals(2, u.state.value.rest?.forIdx)
        u.shiftRestOwner(at = 2, delta = -1)
        assertEquals(1, u.state.value.rest?.forIdx)
        // A rest with no owner stays without one rather than inventing an exercise.
        u.stopRest()
        u.startRest(90.0)
        u.shiftRestOwner(at = 0, delta = 1)
        assertNull(u.state.value.rest?.forIdx)
    }

    @Test
    fun `the tick counts down from the end time, not from the last tick`() {
        val u = ui()
        u.startRest(10.0)
        now += 3_000
        assertTrue(u.tick())
        assertEquals(7.0, u.state.value.rest?.left)
        // A tick that arrives late lands on the right number all the same.
        now += 5_400
        u.tick()
        assertEquals(2.0, u.state.value.rest?.left)
    }

    @Test
    fun `the tick takes a moved end time from the mirror`() {
        val u = ui()
        u.startRest(60.0)
        // The notification's own +15s, with nothing telling this app about it.
        mirror.now = MirrorState(active = true, endsAtMs = now + 75_000, totalSec = 75)
        u.tick()
        assertEquals(75.0, u.state.value.rest?.left)
    }

    @Test
    fun `the last three seconds beep once a second`() {
        val u = ui()
        u.startRest(4.0)
        now += 1_400
        u.tick()
        assertEquals(3.0, u.state.value.rest?.left)
        assertEquals(listOf(660 to 0.0), sound.beeps)
    }

    @Test
    fun `a rest that runs out alerts, toasts and stops`() {
        val u = ui()
        u.startRest(2.0)
        now += 2_500
        assertFalse(u.tick())
        assertNull(u.state.value.rest)
        assertEquals("Rest over — next set!", u.state.value.toast)
        assertEquals(listOf(880 to 0.0, 880 to 0.25, 1320 to 0.5), sound.beeps)
        assertEquals(1, sound.vibrates)
        assertEquals(1, mirror.stops)
    }

    @Test
    fun `the sound setting gates the tones but not the buzz`() {
        val u = ui()
        soundOn = false
        u.startRest(1.0)
        now += 1_500
        u.tick()
        assertTrue(sound.beeps.isEmpty())
        assertEquals(1, sound.vibrates)
    }

    @Test
    fun `the flash is gated on its setting`() {
        val u = ui()
        u.flashTimer()
        assertEquals(0, u.state.value.timerFlash)
        flashOn = true
        u.flashTimer()
        assertEquals(1, u.state.value.timerFlash)
    }

    @Test
    fun `a timed set that is ended early logs what was held`() {
        val u = ui()
        var logged = -1.0
        u.startWork(45.0, "Snatch", onDone = { logged = it })
        now += 20_000
        u.tick()
        assertEquals(25.0, u.state.value.work?.left)
        u.finishWorkEarly()
        assertEquals(20.0, logged, 1e-9)
        assertNull(u.state.value.work)
    }

    @Test
    fun `a timed set that runs out logs the whole hold`() {
        val u = ui()
        var logged = -1.0
        u.startWork(3.0, onDone = { logged = it })
        now += 3_500
        assertFalse(u.tick())
        assertEquals(3.0, logged, 1e-9)
        assertNull(u.state.value.work)
    }

    @Test
    fun `starting a timed set stops the rest it replaces`() {
        val u = ui()
        u.startRest(90.0)
        u.startWork(45.0)
        assertNull(u.state.value.rest)
        assertEquals(1, mirror.stops)
        assertTrue(u.state.value.work != null)
    }

    @Test
    fun `a rest adopted from the notification takes its end time`() {
        val u = ui()
        mirror.now = MirrorState(active = true, endsAtMs = now + 30_000, totalSec = 90)
        u.reconcileRest()
        assertEquals(30.0, u.state.value.rest?.left)
        assertEquals(90.0, u.state.value.rest?.total)
        assertEquals(now + 30_000, u.state.value.rest?.endsAt)
    }

    @Test
    fun `a rest the notification already ended stops`() {
        val u = ui()
        u.startRest(60.0)
        mirror.now = MirrorState(active = false)
        u.reconcileRest()
        assertNull(u.state.value.rest)
    }

    @Test
    fun `a rest the notification extended is adopted, keeping its label`() {
        val u = ui()
        u.startRest(60.0, forIdx = 1, label = "Set 1 of 5")
        mirror.now = MirrorState(active = true, endsAtMs = now + 90_000, totalSec = 120, forIdx = 1)
        u.reconcileRest()
        assertEquals(90.0, u.state.value.rest?.left)
        assertEquals("Set 1 of 5", u.state.value.rest?.label)
        assertEquals(1, u.state.value.rest?.forIdx)
    }

    @Test
    fun `a sheet stack closes by id, and a locked sheet refuses back`() {
        val u = ui()
        val locked = u.openSheet(locked = true) {}
        assertTrue(u.closeTop())
        assertEquals(1, u.state.value.sheets.size)
        val open = u.openSheet {}
        assertTrue(u.closeTop())
        assertEquals(listOf(locked), u.state.value.sheets.map { it.id })
        u.closeSheet(locked)
        assertTrue(u.state.value.sheets.isEmpty())
        assertFalse(u.closeTop())
    }

    @Test
    fun `the toast clears itself`() {
        val real = UiState(clock = { now }, toastMillis = 10, scope = CoroutineScope(SupervisorJob() + Dispatchers.Default))
        real.toast("Weight saved")
        assertEquals("Weight saved", real.state.value.toast)
        Thread.sleep(200)
        assertNull(real.state.value.toast)
    }
}
