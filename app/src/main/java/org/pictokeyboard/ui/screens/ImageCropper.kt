package org.pictokeyboard.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.pictokeyboard.R
import org.pictokeyboard.ui.ConfigViewModel

/**
 * Square image cropper: the picture is shown whole, and a framed square over it
 * marks the part that becomes the picto.
 *
 * **The frame is the feature.** This view used to be exactly the crop square, so
 * the image was scaled to cover it and clipped by the view bounds — which meant
 * there was no boundary to see and nothing outside it to compare against. On a
 * photo with a pale background, against a pale surface, there was no way to tell
 * where the square even was, and pinching to zoom was aiming at something
 * invisible. Now the view is taller than the square, the image is drawn across
 * the whole of it, and everything outside the square is dimmed: what is bright is
 * what you get, and what is dim is what you are cutting off.
 *
 * The image still cannot be zoomed out past covering the square, because a picto
 * with a bar of empty space down one side is not a picto.
 */
class CropImageView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private var bitmap: Bitmap? = null
    private var baseScale = 1f
    private var scale = 1f
    private var tx = 0f
    private var ty = 0f

    /**
     * Quarter turns applied to the image, 0–270.
     *
     * Rotating the *matrix* rather than the bitmap: a re-encoded copy of a
     * several-megapixel photo per tap is both slow and lossy, and the only thing
     * that has to come out rotated is the 512px square [getCroppedBitmap]
     * renders — which is drawn through this same matrix anyway.
     */
    private var rotation = 0
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val drawMatrix = Matrix()

    /** What is being cut away, dimmed rather than hidden so it can still be aimed at. */
    private val scrimPaint = Paint().apply { color = SCRIM_COLOR }

    /**
     * White with a dark hairline under it. A single-colour frame disappears
     * against a photograph of the same colour, and a photograph is exactly what
     * this is always drawn on top of.
     */
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = dp(FRAME_DP)
    }
    private val frameShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = FRAME_SHADOW_COLOR
        strokeWidth = dp(FRAME_DP + 2f)
    }

    /** Thirds, which is how people actually centre a face in a square. */
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = GRID_COLOR
        strokeWidth = dp(1f)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    /**
     * The square that becomes the picto: centred, and inset from the shorter edge
     * so there is always a margin of discarded image visible around it. Without
     * the inset a portrait view would put the square edge-to-edge horizontally and
     * there would be nothing to see at the sides.
     *
     * Held rather than recomputed, so the frame that is drawn, the bounds the drag
     * is clamped against and the region that is finally saved are read from one
     * value and cannot drift apart.
     */
    private val crop = RectF()

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                scale = (scale * detector.scaleFactor).coerceIn(baseScale, baseScale * 8f)
                clamp()
                invalidate()
                return true
            }
        },
    )

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
                tx -= dx
                ty -= dy
                clamp()
                invalidate()
                return true
            }
        },
    )

    fun setBitmap(bmp: Bitmap) {
        if (bitmap === bmp) return
        bitmap = bmp
        rotation = 0
        resetTransform()
        invalidate()
    }

    /**
     * Turns the image a quarter turn; [clockwise] false goes the other way.
     *
     * The zoom and position are reset with it rather than carried over. After a
     * quarter turn the framing the user had chosen no longer describes anything —
     * what was centred horizontally is now centred vertically — so preserving the
     * numbers would preserve a crop nobody asked for. Turning first and framing
     * second is also the order people work in.
     */
    fun rotate(clockwise: Boolean) {
        if (bitmap == null) return
        rotation = ((rotation + if (clockwise) QUARTER_TURN else -QUARTER_TURN) + FULL_TURN) % FULL_TURN
        resetTransform()
        invalidate()
    }

    /** The image's on-screen extent, which swaps axes on the odd quarter turns. */
    private val Bitmap.shownWidth: Int get() = if (rotation % HALF_TURN == 0) width else height

    private val Bitmap.shownHeight: Int get() = if (rotation % HALF_TURN == 0) height else width

    // No onMeasure override any more: the view takes whatever height the dialog
    // gives it, and being taller than the crop square is the point.

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val side = (minOf(w, h) - 2f * dp(CROP_INSET_DP)).coerceAtLeast(0f)
        val left = (w - side) / 2f
        val top = (h - side) / 2f
        crop.set(left, top, left + side, top + side)
        resetTransform()
    }

    /** Starts the image at the smallest scale that still fills the crop square. */
    private fun resetTransform() {
        val bmp = bitmap ?: return
        if (crop.width() <= 0f) return
        baseScale = maxOf(crop.width() / bmp.shownWidth, crop.height() / bmp.shownHeight)
        scale = baseScale
        tx = 0f
        ty = 0f
    }

    /**
     * Keeps the image covering the **crop square** — not the whole view, which is
     * deliberately larger than the image needs to fill. Clamping to the view
     * instead would forbid every position where a corner of the picture shows,
     * which is most of them once you have zoomed in on a face.
     */
    private fun clamp() {
        val bmp = bitmap ?: return
        val half = crop.width() / 2f
        val maxTx = (scale * bmp.shownWidth / 2f - half).coerceAtLeast(0f)
        val maxTy = (scale * bmp.shownHeight / 2f - half).coerceAtLeast(0f)
        tx = tx.coerceIn(-maxTx, maxTx)
        ty = ty.coerceIn(-maxTy, maxTy)
    }

    /** The image placed in view coordinates: centred on the crop square, then nudged. */
    private fun buildMatrix(into: Matrix) {
        val bmp = bitmap ?: return
        into.reset()
        // Centre first, so the rotation turns the image about its own middle
        // rather than swinging it out of frame around the top-left corner.
        into.postTranslate(-bmp.width / 2f, -bmp.height / 2f)
        into.postRotate(rotation.toFloat())
        into.postScale(scale, scale)
        into.postTranslate(width / 2f + tx, height / 2f + ty)
    }

    override fun onDraw(canvas: Canvas) {
        val bmp = bitmap ?: return
        buildMatrix(drawMatrix)
        canvas.drawBitmap(bmp, drawMatrix, paint)

        if (crop.width() <= 0f) return
        val w = width.toFloat()
        val h = height.toFloat()

        // Four bands rather than a clipped-out hole, which would need
        // Canvas.saveLayer and an Xfermode to punch through.
        canvas.drawRect(0f, 0f, w, crop.top, scrimPaint)
        canvas.drawRect(0f, crop.bottom, w, h, scrimPaint)
        canvas.drawRect(0f, crop.top, crop.left, crop.bottom, scrimPaint)
        canvas.drawRect(crop.right, crop.top, w, crop.bottom, scrimPaint)

        canvas.drawRect(crop, frameShadowPaint)
        canvas.drawRect(crop, framePaint)

        val step = crop.width() / THIRDS
        repeat(THIRDS - 1) { i ->
            val offset = step * (i + 1)
            canvas.drawLine(crop.left + offset, crop.top, crop.left + offset, crop.bottom, gridPaint)
            canvas.drawLine(crop.left, crop.top + offset, crop.right, crop.top + offset, gridPaint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return true
    }

    /**
     * Renders the framed square to an [out]×[out] bitmap on a white background.
     *
     * The matrix is the same one the frame is drawn against, moved so the square's
     * top-left is the origin and then scaled to the output size — so what is saved
     * is exactly what was inside the frame, and the two cannot drift apart.
     */
    fun getCroppedBitmap(out: Int = 512): Bitmap? {
        val bmp = bitmap ?: return null
        if (crop.width() <= 0f) return null
        val matrix = Matrix()
        buildMatrix(matrix)
        matrix.postTranslate(-crop.left, -crop.top)
        val outputScale = out.toFloat() / crop.width()
        matrix.postScale(outputScale, outputScale)
        val output = Bitmap.createBitmap(out, out, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(bmp, matrix, paint)
        return output
    }

    private companion object {
        /** Margin of discarded image kept visible around the square, in dp. */
        const val CROP_INSET_DP = 20f
        const val FRAME_DP = 2f

        /** Rule-of-thirds guides, which is how people actually centre a face. */
        const val THIRDS = 3

        const val QUARTER_TURN = 90
        const val HALF_TURN = 180
        const val FULL_TURN = 360
        const val SCRIM_COLOR = 0xB3000000.toInt()
        const val FRAME_SHADOW_COLOR = 0x66000000
        const val GRID_COLOR = 0x80FFFFFF.toInt()
    }
}

/** Full-screen square crop step shown before an imported image becomes a picto. */
@Composable
fun CropImageDialog(
    imageUri: Uri,
    viewModel: ConfigViewModel,
    onDismiss: () -> Unit,
    onCropped: (Bitmap) -> Unit,
) {
    var bitmap by remember(imageUri) { mutableStateOf<Bitmap?>(null) }
    var cropView by remember { mutableStateOf<CropImageView?>(null) }

    LaunchedEffect(imageUri) { bitmap = viewModel.decodeImage(imageUri) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.crop_title), style = MaterialTheme.typography.titleLarge)
                // Takes the room the trailing Spacer used to waste. The cropper
                // needs to be taller than its square: the band of image above and
                // below the frame is what shows the caregiver what they are
                // cutting off, and a view measured to the square exactly has
                // nowhere to show it.
                val cropDescription = stringResource(R.string.crop_a11y)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    val bmp = bitmap
                    if (bmp == null) {
                        CircularProgressIndicator()
                    } else {
                        AndroidView(
                            factory = { context ->
                                CropImageView(context).also {
                                    it.contentDescription = cropDescription
                                    cropView = it
                                }
                            },
                            update = { it.setBitmap(bmp) },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Text(
                    stringResource(R.string.crop_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CropActions(
                    enabled = bitmap != null,
                    onRotate = { clockwise -> cropView?.rotate(clockwise) },
                    onDismiss = onDismiss,
                    onUse = { cropView?.getCroppedBitmap()?.let(onCropped) },
                )
            }
        }
    }
}

/**
 * Turn the picture, or commit to the square.
 *
 * Rotation sits with the other controls rather than over the image: the crop
 * view is a gesture surface where every touch pans or zooms, and a button
 * floating on it would be a target the drag handler swallows.
 */
@Composable
private fun CropActions(
    enabled: Boolean,
    onRotate: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onUse: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onRotate(false) }, enabled = enabled) {
            Icon(
                Icons.AutoMirrored.Filled.RotateLeft,
                contentDescription = stringResource(R.string.crop_rotate_left),
            )
        }
        IconButton(onClick = { onRotate(true) }, enabled = enabled) {
            Icon(
                Icons.AutoMirrored.Filled.RotateRight,
                contentDescription = stringResource(R.string.crop_rotate_right),
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        Button(onClick = onUse, enabled = enabled) { Text(stringResource(R.string.crop_use)) }
    }
}
