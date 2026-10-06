package olygym.app.rest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Adopting the notification's rest when the app comes back, from adoptNativeState in
 * frontend/src/lib/rest-notification.js: the mirror is the authority, and a rest it has already
 * ended or skipped is not a rest at all.
 */
class RestMirrorTest {

    private val now = 1_000_000L

    @Test
    fun `an inactive or expired mirror has nothing to adopt`() {
        assertNull(adoptMirror(MirrorState(), null, now))
        assertNull(adoptMirror(MirrorState(active = true, endsAtMs = now, totalSec = 90), null, now))
        assertNull(adoptMirror(MirrorState(active = true, endsAtMs = now - 1, totalSec = 90), null, now))
        assertNull(adoptMirror(MirrorState(active = true, endsAtMs = 0, totalSec = 90), null, now))
    }

    @Test
    fun `the remaining time is rounded up, so a rest never reads as over early`() {
        assertEquals(90.0, adoptMirror(MirrorState(true, now + 90_000, 90), null, now)?.left)
        assertEquals(90.0, adoptMirror(MirrorState(true, now + 89_001, 90), null, now)?.left)
        assertEquals(1.0, adoptMirror(MirrorState(true, now + 1, 90), null, now)?.left)
        assertEquals(89.0, adoptMirror(MirrorState(true, now + 89_000, 90), null, now)?.left)
    }

    @Test
    fun `the length is floored at one second`() {
        assertEquals(1.0, adoptMirror(MirrorState(true, now + 90_000, 0), null, now)?.total)
        assertEquals(1.0, adoptMirror(MirrorState(true, now + 90_000, -5), null, now)?.total)
        assertEquals(120.0, adoptMirror(MirrorState(true, now + 90_000, 120), null, now)?.total)
    }

    @Test
    fun `the exercise index comes from the mirror, falling back to the one in hand`() {
        val running = MirrorState(true, now + 60_000, 90, forIdx = 3)
        assertEquals(3, adoptMirror(running, 1, now)?.forIdx)
        // A rest that started with no owner of its own keeps whatever the running timer had.
        val ownerless = MirrorState(true, now + 60_000, 90)
        assertEquals(1, adoptMirror(ownerless, 1, now)?.forIdx)
        assertNull(adoptMirror(ownerless, null, now)?.forIdx)
    }

    @Test
    fun `reading a mirror with no rest in it gives an inactive state, not a throw`() {
        val empty = NoRestMirror.read()
        assertEquals(false, empty.active)
        assertEquals(0L, empty.endsAtMs)
        assertEquals(0, empty.totalSec)
        assertNull(empty.forIdx)
    }

    @Test
    fun `the mirror carries the copy the notification shows`() {
        val state = MirrorState(true, now + 60_000, 90, forIdx = 2, text = "Set 2 of 5", sub = "Snatch Day", big = "Snatch Day · Set 2 of 5")
        assertEquals("Set 2 of 5", adoptMirror(state, null, now)?.let { state.text })
        assertEquals("Snatch Day", state.sub)
        assertEquals("Snatch Day · Set 2 of 5", state.big)
    }
}
