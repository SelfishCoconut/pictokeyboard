package org.pictokeyboard.ime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/**
 * Eyes-free keyboard surface: a plain blue rectangle the user drives entirely by
 * touch gestures. It only detects and reports gestures; the service owns all
 * navigation and speech. A large caption of the current picto is drawn for low
 * vision / a sighted helper, but the surface works with no vision at all.
 *
 *  - vertical swipe   → previous/next category
 *  - horizontal swipe → previous/next picto in the category
 *  - single tap       → repeat the current picto aloud
 *  - double tap       → write the current picto
 *  - long press       → delete the last word
 *
 * (The two-finger double-tap that toggles the mode is handled one level up, by
 * [ModeSwitchFrameLayout], so it works in both normal and blind keyboards.)
 */
class BlindKeyboardView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    View(context, attrs) {

    var onSwipeVertical: (down: Boolean) -> Unit = {}
    var onSwipeHorizontal: (right: Boolean) -> Unit = {}
    var onSingleTap: () -> Unit = {}
    var onDoubleTap: () -> Unit = {}
    var onLongPress: () -> Unit = {}

    private var caption: String = ""
    private var hint: String = ""

    private val density = resources.displayMetrics.density
    private val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 34 * density
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCCFFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        textSize = 14 * density
    }

    private val swipeThreshold = 60 * density

    private val detector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                onSingleTap()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                onDoubleTap()
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                onLongPress()
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float,
            ): Boolean {
                val dx = e2.x - (e1?.x ?: e2.x)
                val dy = e2.y - (e1?.y ?: e2.y)
                if (abs(dx) > abs(dy)) {
                    if (abs(dx) > swipeThreshold) onSwipeHorizontal(dx > 0)
                } else {
                    if (abs(dy) > swipeThreshold) onSwipeVertical(dy > 0)
                }
                return true
            }
        },
    )

    init {
        setBackgroundColor(0xFF1565C0.toInt()) // blue
        isFocusable = true
        isClickable = true
    }

    /** Sets the large current-picto caption (drawn for low-vision users). */
    fun setCaption(text: String) {
        caption = text
        invalidate()
    }

    /** Sets the small helper line drawn at the top. */
    fun setHint(text: String) {
        hint = text
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        detector.onTouchEvent(event)
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (hint.isNotBlank()) {
            canvas.drawText(hint, width / 2f, 28 * density, hintPaint)
        }
        if (caption.isNotBlank()) {
            val y = height / 2f - (captionPaint.descent() + captionPaint.ascent()) / 2f
            canvas.drawText(caption, width / 2f, y, captionPaint)
        }
    }
}
