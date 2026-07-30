package org.pictokeyboard.ime

import android.content.Context
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout

/**
 * Container that toggles between the normal and blind keyboards on a two-finger
 * double-tap. It only steals multi-finger gestures, so ordinary single-finger
 * touches still reach the children (the normal keyboard's buttons/lists or the
 * blind surface's swipes). This is what lets a blind user flip the mode without
 * needing to find a button.
 */
class ModeSwitchFrameLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    FrameLayout(context, attrs) {

    var onTwoFingerDoubleTap: () -> Unit = {}

    private var sawTwoFingers = false
    private var lastTwoFingerTapUp = 0L

    private val tapTimeout = ViewConfiguration.getDoubleTapTimeout().toLong()
    private val maxTapDuration = 400L

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> sawTwoFingers = false
            MotionEvent.ACTION_POINTER_DOWN -> if (ev.pointerCount >= 2) sawTwoFingers = true
        }
        // Once a second finger is down, take over the gesture stream.
        return sawTwoFingers
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> if (ev.pointerCount >= 2) sawTwoFingers = true
            MotionEvent.ACTION_UP -> {
                if (sawTwoFingers && ev.eventTime - ev.downTime < maxTapDuration) {
                    val now = SystemClock.uptimeMillis()
                    if (now - lastTwoFingerTapUp < tapTimeout) {
                        lastTwoFingerTapUp = 0L
                        onTwoFingerDoubleTap()
                    } else {
                        lastTwoFingerTapUp = now
                    }
                }
                sawTwoFingers = false
            }
            MotionEvent.ACTION_CANCEL -> sawTwoFingers = false
        }
        return true
    }
}
