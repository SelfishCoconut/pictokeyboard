package org.pictokeyboard.ime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import org.pictokeyboard.R
import org.pictokeyboard.ui.theme.CategoryColors
import kotlin.math.abs

/**
 * Eyes-free keyboard surface: a full-bleed field of the current category's colour
 * that the user drives entirely by touch gestures. It only detects and reports
 * gestures; the service owns all navigation and speech. A large caption of the
 * current picto is drawn for low vision / a sighted helper, but the surface works
 * with no vision at all.
 *
 * The surface used to be a fixed blue. Taking the category's own hue at full
 * saturation applies the design's thesis at maximum scale: a low-vision user gets
 * a full-screen colour cue of which category they are in, and the caption
 * auto-contrasts against whatever that hue turns out to be.
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

    /**
     * Caption and hint are sized in **sp**, not dp. They were in dp, which meant
     * the one piece of text a low-vision user leans on was the one piece that
     * ignored the system font-size setting they had already turned up.
     */
    private val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = sp(CAPTION_SP)
        typeface = ResourcesCompat.getFont(context, R.font.atkinson_hyperlegible_bold)
            ?: Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = sp(HINT_SP)
        typeface = ResourcesCompat.getFont(context, R.font.atkinson_hyperlegible_regular)
            ?: Typeface.DEFAULT
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
        setSurfaceColor(null)
        isFocusable = true
        isClickable = true
    }

    /**
     * Floods the surface with [colorArgb] — the current category's hue at full
     * saturation — and repicks the text colours to contrast against it.
     *
     * Passing null falls back to the themed accent, which is what the surface
     * shows before any category has been reached.
     */
    fun setSurfaceColor(colorArgb: Int?) {
        val surface = colorArgb?.let { CategoryColors.fill(it) }
            ?: ContextCompat.getColor(context, R.color.accent)
        setBackgroundColor(surface)
        // Both at full opacity. The hint was previously white at 80%, which trades
        // away contrast to signal hierarchy -- but the size difference already says
        // "secondary", and a 14sp line still owes the reader 4.5:1.
        val onSurface = CategoryColors.contrastText(surface)
        captionPaint.color = onSurface
        hintPaint.color = onSurface
        invalidate()
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
            canvas.drawText(hint, width / 2f, HINT_BASELINE_DP * density, hintPaint)
        }
        if (caption.isNotBlank()) {
            val y = height / 2f - (captionPaint.descent() + captionPaint.ascent()) / 2f
            canvas.drawText(caption, width / 2f, y, captionPaint)
        }
    }

    private fun sp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    private companion object {
        const val CAPTION_SP = 34f
        const val HINT_SP = 14f
        const val HINT_BASELINE_DP = 28f
    }
}
