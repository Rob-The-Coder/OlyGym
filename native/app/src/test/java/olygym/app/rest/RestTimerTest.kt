package olygym.app.rest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The parts of the port that are pure: the chip's clock, the Live Update progress fraction, the
 * action-to-step mapping and the mirror's length. The Android half (channel, builder, service,
 * receiver) needs a device, so it is not covered here.
 */
class RestTimerTest {

    @Test
    fun `restClock rounds up to the next second and formats minutes and seconds`() {
        assertEquals("0:00", restClock(0))
        assertEquals("0:01", restClock(1))
        assertEquals("0:01", restClock(999))
        assertEquals("0:01", restClock(1000))
        assertEquals("0:02", restClock(1001))
        assertEquals("1:00", restClock(59001))
        assertEquals("1:00", restClock(60000))
        assertEquals("1:30", restClock(90000))
        assertEquals("1:31", restClock(90500))
        assertEquals("59:59", restClock(3599000))
        // A rest that already ran out still reads as 0:00, never a negative clock.
        assertEquals("0:00", restClock(-5))
    }

    @Test
    fun `restProgressPercent runs 0 to 100 as the rest is spent`() {
        assertEquals(0, restProgressPercent(90000, 90))
        assertEquals(50, restProgressPercent(45000, 90))
        assertEquals(99, restProgressPercent(1000, 90))
        assertEquals(100, restProgressPercent(0, 90))
        // Past the end it clamps at 100 rather than overflowing the bar.
        assertEquals(100, restProgressPercent(-5000, 90))
    }

    @Test
    fun `restProgressPercent floors the length at one second`() {
        // Java Math.max(1, totalSec), so a mirror written with 0 cannot divide by zero.
        assertEquals(50, restProgressPercent(500, 0))
        assertEquals(50, restProgressPercent(500, -3))
    }

    @Test
    fun `totalSeconds rounds the rest length with a floor of one`() {
        assertEquals(90, totalSeconds(90000))
        assertEquals(91, totalSeconds(90500))
        assertEquals(2, totalSeconds(1500))
        assertEquals(1, totalSeconds(1499))
        assertEquals(1, totalSeconds(0))
        assertEquals(1, totalSeconds(400))
        assertEquals(1, totalSeconds(-90000))
    }

    @Test
    fun `restStepMillis maps only the two step actions`() {
        assertEquals(15000L, restStepMillis(RestNotification.ACTION_PLUS15))
        assertEquals(-15000L, restStepMillis(RestNotification.ACTION_MINUS15))
        assertNull(restStepMillis(RestNotification.ACTION_SKIP))
        assertNull(restStepMillis(RestNotification.ACTION_EXPIRE))
        assertNull(restStepMillis("olygym.app.action.REST_OTHER"))
        assertNull(restStepMillis(""))
    }

    @Test
    fun `restSteppedEnd keeps a running end and reports an expired one`() {
        assertEquals(115000L, restSteppedEnd(100000, 15000, 50000))
        assertEquals(85000L, restSteppedEnd(100000, -15000, 80000))
        assertEquals(85000L, restSteppedEnd(100000, -15000, 84999))
        // Landing exactly on now is already over.
        assertNull(restSteppedEnd(100000, -15000, 85000))
        assertNull(restSteppedEnd(100000, -15000, 90000))
        assertNull(restSteppedEnd(100000, 15000, 200000))
    }

    @Test
    fun `the mirror and the notification use the shipping ids`() {
        assertEquals(2000, RestNotification.NOTIFICATION_ID)
        assertEquals("rest_timer", RestNotification.CHANNEL_ID)
        assertEquals("rest_timer_2", RestNotification.CHANNEL_ID_CURRENT)
        assertEquals("olygym_rest", RestNotification.PREFS)
        assertEquals("Rest timer", RestNotification.CHANNEL_NAME)
        assertEquals("olygym.app.action.REST_PLUS15", RestNotification.ACTION_PLUS15)
        assertEquals("olygym.app.action.REST_MINUS15", RestNotification.ACTION_MINUS15)
        assertEquals("olygym.app.action.REST_SKIP", RestNotification.ACTION_SKIP)
        assertEquals("olygym.app.action.REST_EXPIRE", RestNotification.ACTION_EXPIRE)
        assertEquals(15000L, REST_STEP_MS)
    }
}
