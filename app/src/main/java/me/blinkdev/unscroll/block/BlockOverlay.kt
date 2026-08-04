package me.blinkdev.unscroll.block

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView

/**
 * A full-screen curtain drawn over the offending app while we back out of it.
 *
 * It uses TYPE_ACCESSIBILITY_OVERLAY, which an accessibility service may draw without the
 * "display over other apps" permission. It also swallows touches, so a stray flick during
 * the back navigation cannot land on the feed underneath.
 */
class BlockOverlay(private val context: Context) {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var view: View? = null

    fun show(message: String, durationMillis: Long = 1_600L) {
        handler.post {
            if (view != null) return@post
            val curtain = buildView(message)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT,
            )
            runCatching { windowManager.addView(curtain, params) }
                .onSuccess {
                    view = curtain
                    handler.postDelayed(::hide, durationMillis)
                }
        }
    }

    fun hide() {
        handler.post {
            val current = view ?: return@post
            view = null
            runCatching { windowManager.removeView(current) }
        }
    }

    private fun buildView(message: String): View {
        val text = TextView(context).apply {
            this.text = message
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            val pad = (32 * context.resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        return FrameLayout(context).apply {
            setBackgroundColor(Color.parseColor("#F212151C"))
            isClickable = true
            addView(
                text,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
        }
    }
}
