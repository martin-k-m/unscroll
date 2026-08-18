package me.blinkdev.unscroll.usage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.TimeZone

private const val IG = "com.instagram.android"
private const val YT = "com.google.android.youtube"
private const val MINUTE = 60_000L

private fun resumed(pkg: String, at: Long) = ForegroundEvent(pkg, at, resumed = true)
private fun ended(pkg: String, at: Long) = ForegroundEvent(pkg, at, resumed = false)

class UsageMathFoldTest {

    @Test
    fun `no events means no time for anyone`() {
        assertEquals(emptyMap<String, Long>(), UsageMath.foregroundMillis(emptyList(), 10 * MINUTE))
    }

    @Test
    fun `one closed session counts resume to pause`() {
        val totals = UsageMath.foregroundMillis(
            listOf(resumed(IG, 0), ended(IG, 3 * MINUTE)),
            now = 10 * MINUTE,
        )
        assertEquals(mapOf(IG to 3 * MINUTE), totals)
    }

    @Test
    fun `separate sessions accumulate`() {
        val totals = UsageMath.foregroundMillis(
            listOf(
                resumed(IG, 0), ended(IG, 2 * MINUTE),
                resumed(IG, 5 * MINUTE), ended(IG, 9 * MINUTE),
            ),
            now = 20 * MINUTE,
        )
        assertEquals(mapOf(IG to 6 * MINUTE), totals)
    }

    @Test
    fun `a session still open counts up to now`() {
        val totals = UsageMath.foregroundMillis(listOf(resumed(IG, 4 * MINUTE)), now = 7 * MINUTE)
        assertEquals(mapOf(IG to 3 * MINUTE), totals)
    }

    @Test
    fun `a closed session plus an open one both count`() {
        val totals = UsageMath.foregroundMillis(
            listOf(resumed(IG, 0), ended(IG, MINUTE), resumed(IG, 5 * MINUTE)),
            now = 8 * MINUTE,
        )
        assertEquals(mapOf(IG to 4 * MINUTE), totals)
    }

    @Test
    fun `interleaved packages are tracked independently`() {
        val totals = UsageMath.foregroundMillis(
            listOf(
                resumed(IG, 0),
                ended(IG, 2 * MINUTE),
                resumed(YT, 2 * MINUTE),
                ended(YT, 3 * MINUTE),
                resumed(IG, 3 * MINUTE),
                ended(IG, 4 * MINUTE),
            ),
            now = 10 * MINUTE,
        )
        assertEquals(mapOf(IG to 3 * MINUTE, YT to MINUTE), totals)
    }

    @Test
    fun `a session that crossed midnight counts from midnight, not from nothing`() {
        // The caller now hands over events from before the window, so the resume is seen. The
        // session ran 8 minutes in total; only the 5 after midnight belong to today.
        val midnight = 10 * MINUTE
        val totals = UsageMath.foregroundMillis(
            listOf(resumed(IG, midnight - 3 * MINUTE), ended(IG, midnight + 5 * MINUTE)),
            now = midnight + 8 * MINUTE,
            windowStart = midnight,
        )
        assertEquals(mapOf(IG to 5 * MINUTE), totals)
    }

    @Test
    fun `an app still on screen since before midnight counts from midnight up to now`() {
        // The other half, and the worse one before the fix: no pause has arrived, so the only
        // thing that can credit it is the trailing pass over what is still on screen. Clipping
        // to the window start is what makes that pass right for a resume that predates it.
        val midnight = 10 * MINUTE
        val totals = UsageMath.foregroundMillis(
            listOf(
                resumed(IG, midnight - 40 * MINUTE),
                resumed(YT, midnight + 2 * MINUTE),
                ended(YT, midnight + 3 * MINUTE),
            ),
            now = midnight + 6 * MINUTE,
            windowStart = midnight,
        )
        assertEquals(mapOf(IG to 6 * MINUTE, YT to MINUTE), totals)
    }

    @Test
    fun `a session entirely before midnight contributes nothing to today`() {
        // The wider query will surface yesterday's finished sessions too. They must clip to
        // zero, and stay in the map at zero, rather than leak into today's total.
        val midnight = 10 * MINUTE
        val totals = UsageMath.foregroundMillis(
            listOf(resumed(IG, midnight - 9 * MINUTE), ended(IG, midnight - MINUTE)),
            now = midnight + 4 * MINUTE,
            windowStart = midnight,
        )
        assertEquals(mapOf(IG to 0L), totals)
    }

    @Test
    fun `a pause whose resume was never seen still contributes nothing`() {
        // If the lookback was not long enough to reach the resume, there is nothing honest to
        // credit. Guessing a start would over-count; nothing is the safe direction.
        val totals = UsageMath.foregroundMillis(
            listOf(ended(IG, 8 * MINUTE)),
            now = 10 * MINUTE,
            windowStart = 5 * MINUTE,
        )
        assertEquals(emptyMap<String, Long>(), totals)
    }

    @Test
    fun `a stop ends a session the same way a pause does`() {
        val paused = UsageMath.foregroundMillis(
            listOf(resumed(IG, 0), ForegroundEvent(IG, 2 * MINUTE, resumed = false)),
            now = 5 * MINUTE,
        )
        assertEquals(mapOf(IG to 2 * MINUTE), paused)
    }

    @Test
    fun `a second resume without a pause discards the earlier one`() {
        val totals = UsageMath.foregroundMillis(
            listOf(resumed(IG, 0), resumed(IG, 4 * MINUTE), ended(IG, 6 * MINUTE)),
            now = 10 * MINUTE,
        )
        assertEquals(mapOf(IG to 2 * MINUTE), totals)
    }

    @Test
    fun `a zero length session records zero rather than dropping the package`() {
        val totals = UsageMath.foregroundMillis(
            listOf(resumed(IG, MINUTE), ended(IG, MINUTE)),
            now = 5 * MINUTE,
        )
        assertEquals(mapOf(IG to 0L), totals)
    }
}

class UsageMathStartOfDayTest {

    private val utc: TimeZone = TimeZone.getTimeZone("UTC")
    private val day = 24 * 60 * MINUTE

    @Test
    fun `midnight itself is its own start of day`() {
        assertEquals(0L, UsageMath.startOfDay(0L, utc))
    }

    @Test
    fun `a moment inside the day rolls back to that day's midnight`() {
        assertEquals(day, UsageMath.startOfDay(day + 13 * 60 * MINUTE, utc))
    }

    @Test
    fun `the last millisecond of a day still belongs to that day`() {
        assertEquals(0L, UsageMath.startOfDay(day - 1, utc))
        assertEquals(day, UsageMath.startOfDay(day, utc))
    }

    @Test
    fun `start of day is a zoned boundary, not a UTC one`() {
        val tokyo = TimeZone.getTimeZone("Asia/Tokyo")
        // 1970-01-02T00:30Z is already 09:30 on the 2nd in Tokyo.
        val instant = day + 30 * MINUTE
        assertEquals(day, UsageMath.startOfDay(instant, utc))
        assertEquals(day - 9 * 60 * MINUTE, UsageMath.startOfDay(instant, tokyo))
    }

    @Test
    fun `start of day never runs ahead of now`() {
        val now = System.currentTimeMillis()
        assertTrue(UsageMath.startOfDay(now, utc) <= now)
    }
}
