package org.pictokeyboard.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
 * Square image cropper. The image is shown at a "cover" scale (so the square
 * viewport is always filled) and the user can pinch to zoom and drag to position
 * it. [getCroppedBitmap] renders the visible square to a bitmap.
 */
class CropImageView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private var bitmap: Bitmap? = null
    private var baseScale = 1f
    private var scale = 1f
    private var tx = 0f
    private var ty = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val drawMatrix = Matrix()

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
        resetTransform()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(size, size)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        resetTransform()
    }

    private fun resetTransform() {
        val bmp = bitmap ?: return
        if (width == 0 || height == 0) return
        baseScale = maxOf(width.toFloat() / bmp.width, height.toFloat() / bmp.height)
        scale = baseScale
        tx = 0f
        ty = 0f
    }

    /** Keeps the image covering the whole square viewport. */
    private fun clamp() {
        val bmp = bitmap ?: return
        val maxTx = (scale * bmp.width / 2f - width / 2f).coerceAtLeast(0f)
        val maxTy = (scale * bmp.height / 2f - height / 2f).coerceAtLeast(0f)
        tx = tx.coerceIn(-maxTx, maxTx)
        ty = ty.coerceIn(-maxTy, maxTy)
    }

    private fun buildMatrix(outputScale: Float, into: Matrix) {
        val bmp = bitmap ?: return
        into.reset()
        into.postTranslate(-bmp.width / 2f, -bmp.height / 2f)
        into.postScale(scale, scale)
        into.postTranslate(width / 2f + tx, height / 2f + ty)
        if (outputScale != 1f) into.postScale(outputScale, outputScale)
    }

    override fun onDraw(canvas: Canvas) {
        val bmp = bitmap ?: return
        buildMatrix(1f, drawMatrix)
        canvas.drawBitmap(bmp, drawMatrix, paint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return true
    }

    /** Renders the visible square region to an [out]×[out] bitmap on a white background. */
    fun getCroppedBitmap(out: Int = 512): Bitmap? {
        val bmp = bitmap ?: return null
        if (width == 0) return null
        val matrix = Matrix()
        buildMatrix(out.toFloat() / width, matrix)
        val output = Bitmap.createBitmap(out, out, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(bmp, matrix, paint)
        return output
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    val bmp = bitmap
                    if (bmp == null) {
                        CircularProgressIndicator()
                    } else {
                        AndroidView(
                            factory = { context -> CropImageView(context).also { cropView = it } },
                            update = { it.setBitmap(bmp) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Text(
                    stringResource(R.string.crop_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.weight(1f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    Button(
                        onClick = { cropView?.getCroppedBitmap()?.let(onCropped) },
                        enabled = bitmap != null,
                    ) { Text(stringResource(R.string.crop_use)) }
                }
            }
        }
    }
}
