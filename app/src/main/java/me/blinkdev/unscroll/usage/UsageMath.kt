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

    /** [events] must be in ascending timestamp order, which is how the platform reports them. */
    fun foregroundMillis(events: List<ForegroundEvent>, now: Long): Map<String, Long> {
        val totals = mutableMapOf<String, Long>()
        val lastResumed = mutableMapOf<String, Long>()

        for (event in events) {
            if (event.resumed) {
                lastResumed[event.packageName] = event.timestampMillis
            } else {
                val resumedAt = lastResumed.remove(event.packageName) ?: continue
                totals[event.packageName] =
                    (totals[event.packageName] ?: 0L) + (event.timestampMillis - resumedAt)
            }
        }

        // Whatever is on screen right now has been resumed but not paused yet.
        lastResumed.forEach { (pkg, resumedAt) ->
            totals[pkg] = (totals[pkg] ?: 0L) + (now - resumedAt)
        }
        return totals
    }
}
