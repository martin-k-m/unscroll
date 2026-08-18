package me.blinkdev.unscroll.block

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import me.blinkdev.unscroll.data.Settings
import me.blinkdev.unscroll.data.SettingsStore
import me.blinkdev.unscroll.usage.UsageTracker

/**
 * Watches window changes, decides whether the current screen is something the user asked to
 * stay out of, and if so backs out of it.
 *
 * Everything here runs on the accessibility callback thread and must stay cheap. The two
 * expensive things (reading settings, reading usage stats) are cached: settings arrive on a
 * background flow, usage totals are recomputed at most once every [USAGE_REFRESH_MILLIS].
 */
class ScrollBlockerService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob())
    private lateinit var overlay: BlockOverlay
    private lateinit var usage: UsageTracker

    @Volatile
    private var settings = Settings()

    private var usageCache: Map<String, Long> = emptyMap()
    private var usageCachedAt = 0L
    private var lastCheckAt = 0L
    private var lastBlockAt = 0L
    private var lastBlockKey = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        overlay = BlockOverlay(this)
        usage = UsageTracker(this)
        val store = SettingsStore(this)
        scope.launch {
            store.settings.collectLatest { settings = it }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return
        if (packageName == this.packageName) return

        val now = System.currentTimeMillis()
        val snapshot = settings
        if (snapshot.isSnoozed(now)) return

        val forced = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        if (!forced && now - lastCheckAt < CHECK_INTERVAL_MILLIS) return
        lastCheckAt = now

        overLimit(packageName, snapshot, now)?.let { minutes ->
            block(packageName, "limit", "You are out of ${BlockRules.labelFor(packageName)} time today ($minutes min).", now)
            return
        }

        val matched = matchedSurface(packageName, snapshot) ?: return
        block(packageName, matched.id, "${matched.label} is switched off. Back to what you were doing.", now)
    }

    /** Returns the configured limit in minutes when the app has already used it up. */
    private fun overLimit(packageName: String, snapshot: Settings, now: Long): Int? {
        val limit = snapshot.dailyLimits[packageName] ?: return null
        if (now - usageCachedAt > USAGE_REFRESH_MILLIS) {
            usageCache = runCatching { usage.foregroundMillisToday(now) }.getOrDefault(usageCache)
            usageCachedAt = now
        }
        val usedMinutes = ((usageCache[packageName] ?: 0L) / 60_000L).toInt()
        return if (usedMinutes >= limit) limit else null
    }

    private fun matchedSurface(packageName: String, snapshot: Settings): Surface? {
        val candidates = BlockRules.surfacesFor(packageName)
            .filter { it.id in snapshot.enabledSurfaces }
        if (candidates.isEmpty()) return null

        candidates.firstOrNull { it.wholeApp }?.let { return it }

        val root = rootInActiveWindow ?: return null
        try {
            return BlockRules.match(
                packageName,
                snapshot.enabledSurfaces,
                { root.hasViewId(it) },
                { root.hasSelectedText(it) },
            )
        } finally {
            @Suppress("DEPRECATION")
            root.recycle()
        }
    }

    private fun block(packageName: String, reason: String, message: String, now: Long) {
        val key = "$packageName/$reason"
        val repeated = key == lastBlockKey && now - lastBlockAt < ESCALATE_WITHIN_MILLIS
        lastBlockKey = key
        lastBlockAt = now

        overlay.show(message)
        buzz()

        // Back usually leaves the feed. If we are right back here seconds later, the host app
        // is putting the feed on its own start destination, so leave the app entirely.
        if (repeated || reason == "limit") {
            performGlobalAction(GLOBAL_ACTION_HOME)
        } else {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }

    private fun buzz() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        runCatching {
            vibrator.vibrate(VibrationEffect.createOneShot(40L, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        scope.cancel()
        if (::overlay.isInitialized) overlay.hide()
        super.onDestroy()
    }

    private fun AccessibilityNodeInfo.hasViewId(viewId: String): Boolean {
        val hits = runCatching { findAccessibilityNodeInfosByViewId(viewId) }.getOrNull().orEmpty()
        val found = hits.isNotEmpty()
        @Suppress("DEPRECATION")
        hits.forEach { it.recycle() }
        return found
    }

    /**
     * Text signals such as "Reels" sit in the tab bar of every screen, so a plain text match
     * would fire constantly. Requiring the node to be selected narrows it to "this tab is the
     * one you are looking at".
     */
    private fun AccessibilityNodeInfo.hasSelectedText(text: String): Boolean {
        val hits = runCatching { findAccessibilityNodeInfosByText(text) }.getOrNull().orEmpty()
        val found = hits.any { node ->
            node.isSelected || node.parent?.isSelected == true
        }
        @Suppress("DEPRECATION")
        hits.forEach { it.recycle() }
        return found
    }

    private companion object {
        const val CHECK_INTERVAL_MILLIS = 400L
        const val USAGE_REFRESH_MILLIS = 20_000L
        const val ESCALATE_WITHIN_MILLIS = 4_000L
    }
}
