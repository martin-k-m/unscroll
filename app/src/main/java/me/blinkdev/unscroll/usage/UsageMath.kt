package me.blinkdev.unscroll.usage

import java.util.Calendar
import java.util.TimeZone

/** One foreground transition for a package, with the Android event type already resolved. */
data class ForegroundEvent(
    val packageName: String,
    val timestampMillis: Long,
    val resumed: Boolean,
)

/** The day accounting, kept free of Android types so it can be reasoned about on its own. */
internal object UsageMath {

    fun startOfDay(now: Long, timeZone: TimeZone = TimeZone.getDefault()): Long =
        Calendar.getInstance(timeZone).apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    /**
     * Foreground time per package between [windowStart] and [now].
     *
     * [events] must be in ascending timestamp order, which is how the platform reports them,
     * and may begin before [windowStart]. A session that was already on screen at
     * [windowStart] counts from [windowStart], not from its resume, and not from nothing.
     * That is what makes a daily total honest about a session that crossed midnight: the
     * caller has to hand over events from before the boundary, and this clips them to it.
     */
    fun foregroundMillis(
        events: List<ForegroundEvent>,
        now: Long,
        windowStart: Long = 0L,
    ): Map<String, Long> {
        val totals = mutableMapOf<String, Long>()
        val lastResumed = mutableMapOf<String, Long>()

        fun credit(pkg: String, from: Long, to: Long) {
            val start = maxOf(from, windowStart)
            totals[pkg] = (totals[pkg] ?: 0L) + maxOf(0L, to - start)
        }

        for (event in events) {
            if (event.resumed) {
                lastResumed[event.packageName] = event.timestampMillis
            } else {
                val resumedAt = lastResumed.remove(event.packageName) ?: continue
                credit(event.packageName, resumedAt, event.timestampMillis)
            }
        }

        // Whatever is on screen right now has been resumed but not paused yet.
        lastResumed.forEach { (pkg, resumedAt) -> credit(pkg, resumedAt, now) }
        return totals
    }
}
