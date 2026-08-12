package org.pictokeyboard.ime

import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import org.pictokeyboard.R

/**
 * What the keyboard says on screen (#149).
 *
 * It used to be a `Toast`, and **the toast never appeared.** Android suppresses
 * text toasts from an app whose notifications are disabled, and since API 33
 * notifications are off until `POST_NOTIFICATIONS` is granted — which this app
 * has no reason to ask for, having no notifications. So every Beautify outcome
 * and every image-share failure was dropped in silence for anybody not running
 * TalkBack. Measured: no Toast window is ever created.
 *
 * Drawing it inside the keyboard's own window fixes that for good. An IME is on
 * screen whenever any of these messages fire, so there is always somewhere to
 * put them; it needs no permission and no setting can suppress it; and unlike a
 * bottom-gravity toast it cannot end up *behind* the keyboard.
 *
 * **It costs no height.** The view floats over the board and disappears again —
 * never a row that pushes the grid down, which is the whole objection that took
 * the phrase strip away in #148.
 */
class KeyboardMessage {

    private val handler = Handler(Looper.getMainLooper())
    private var view: TextView? = null

    /**
     * Builds the message view into [parent] and returns it.
     *
     * Centred on the board rather than tucked at an edge: these are things the
     * user has to notice — a call starting, a rephrase that did not happen — and
     * the pictos it briefly covers are the one part of this keyboard that can
     * afford to be hidden for two seconds.
     */
    fun attach(parent: FrameLayout): TextView {
        val message = TextView(parent.context).apply {
            visibility = View.GONE
            gravity = Gravity.CENTER
            setTextAppearanceForKeyboard()
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ).apply {
                val margin = (parent.resources.displayMetrics.density * SIDE_MARGIN_DP).toInt()
                marginStart = margin
                marginEnd = margin
            }
        }
        parent.addView(message)
        view = message
        return message
    }

    /**
     * Shows [text] for a couple of seconds, or for [long] enough to outlast the
     * bell's four-second countdown.
     *
     * A new message replaces the one before it rather than queueing: what the
     * keyboard has to say now is always more useful than what it had to say a
     * moment ago.
     */
    fun show(text: CharSequence, long: Boolean) {
        val message = view ?: return
        handler.removeCallbacksAndMessages(null)
        message.text = text
        message.visibility = View.VISIBLE
        handler.postDelayed({ message.visibility = View.GONE }, if (long) LONG_MS else SHORT_MS)
    }

    /** Repaints from the palette, so high contrast reaches this too (#109). */
    fun repaint(skin: KeyboardPalette) {
        val message = view ?: return
        val density = message.resources.displayMetrics.density
        message.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = density * CORNER_DP
            setColor(skin.ink)
        }
        message.setTextColor(skin.paper)
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        view = null
    }

    private fun TextView.setTextAppearanceForKeyboard() {
        val density = resources.displayMetrics.density
        val padH = (density * PADDING_H_DP).toInt()
        val padV = (density * PADDING_V_DP).toInt()
        setPadding(padH, padV, padH, padV)
        textSize = TEXT_SP
        typeface = androidx.core.content.res.ResourcesCompat.getFont(context, R.font.atkinson_hyperlegible)
    }

    private companion object {
        const val SHORT_MS = 2_000L

        /** Longer than [AssistanceController]'s countdown, so it outlasts it. */
        const val LONG_MS = 4_500L

        const val CORNER_DP = 12f
        const val PADDING_H_DP = 16f
        const val PADDING_V_DP = 12f
        const val SIDE_MARGIN_DP = 24f
        const val TEXT_SP = 16f
    }
}
