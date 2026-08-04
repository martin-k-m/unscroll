package me.blinkdev.unscroll.usage

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import java.util.Calendar

/**
 * Foreground time per package for the current calendar day.
 *
 * Built from raw usage events rather than [UsageStatsManager.queryUsageStats], because the
 * daily buckets that call returns are aligned to the device's own reset time and routinely
 * include time from before midnight.
 */
class UsageTracker(context: Context) {

    private val appContext = context.applicationContext
    private val manager =
        appContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    fun hasPermission(): Boolean {
        val appOps = appContext.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            appContext.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Milliseconds spent in the foreground today, keyed by package name. */
    fun foregroundMillisToday(now: Long = System.currentTimeMillis()): Map<String, Long> {
        val start = startOfDay(now)
        val totals = mutableMapOf<String, Long>()
        val lastResumed = mutableMapOf<String, Long>()

        val events = manager.queryEvents(start, now)
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> lastResumed[pkg] = event.timeStamp
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED -> {
                    val resumedAt = lastResumed.remove(pkg) ?: continue
                    totals[pkg] = (totals[pkg] ?: 0L) + (event.timeStamp - resumedAt)
                }
            }
        }

        // Whatever is on screen right now has been resumed but not paused yet.
        lastResumed.forEach { (pkg, resumedAt) ->
            totals[pkg] = (totals[pkg] ?: 0L) + (now - resumedAt)
        }
        return totals
    }

    fun minutesToday(packageName: String, now: Long = System.currentTimeMillis()): Int =
        ((foregroundMillisToday(now)[packageName] ?: 0L) / 60_000L).toInt()

    private fun startOfDay(now: Long): Long = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
