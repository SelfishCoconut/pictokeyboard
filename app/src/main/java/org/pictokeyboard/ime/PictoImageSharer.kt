package org.pictokeyboard.ime

import android.content.ClipDescription
import android.content.Context
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.pictokeyboard.R
import org.pictokeyboard.data.db.PictoEntity
import java.io.File

/**
 * Sends a pictogram into the focused field as an image, via the Commit Content
 * API -- the mechanism keyboards use for GIFs and stickers.
 *
 * This lives outside [PictoKeyboardService] because rendering a 512x512 card
 * and writing it to disk has no business happening on the main thread of an
 * InputMethodService: a slow write janks the keyboard of a user who may already
 * struggle to tap accurately.
 */
class PictoImageSharer(private val context: Context) {

    /** The field currently being typed into. */
    data class Target(val connection: InputConnection, val editorInfo: EditorInfo)

    /**
     * Renders [picto] as a captioned card and commits it to the field named by
     * [currentTarget]. [frameColor] frames the card like the on-screen key.
     * [attribution], when non-null, is drawn beneath the caption and copied into
     * the clip description so the ARASAAC licence credit travels with the picture.
     *
     * [currentTarget] is a lookup rather than a value because rendering suspends:
     * the user can move to another field in the meantime, and a captured
     * connection would by then be pointing at a field that no longer has focus.
     *
     * Calls [onError] with a string resource when the picto has no image yet or
     * the field cannot accept rich content.
     */
    suspend fun send(
        picto: PictoEntity,
        frameColor: Int,
        attribution: String?,
        currentTarget: () -> Target?,
        onError: (Int) -> Unit,
    ) {
        val editorInfo = currentTarget()?.editorInfo ?: return
        val source = picto.imagePath?.let { File(it) }
        if (source == null || !source.exists()) {
            onError(R.string.img_not_ready)
            return
        }
        val supported = EditorInfoCompat.getContentMimeTypes(editorInfo)
        if (supported.isEmpty()) {
            onError(R.string.img_unsupported)
            return
        }
        fun accepts(mime: String) = supported.any { ClipDescription.compareMimeTypes(mime, it) }
        val isWhatsApp = editorInfo.packageName?.startsWith("com.whatsapp") == true
        val mime = when {
            isWhatsApp && accepts("image/webp") -> "image/webp"
            accepts("image/png") -> "image/png"
            accepts("image/webp") -> "image/webp"
            accepts("image/*") -> "image/png"
            else -> null
        }
        if (mime == null) {
            onError(R.string.img_unsupported)
            return
        }

        val caption = picto.label.ifBlank { picto.spokenText }.trim()
        val file = labeledImage(source, picto.id, caption, frameColor, attribution, mime)
        if (file == null) {
            onError(R.string.img_unsupported)
            return
        }

        // Rendering suspended, so re-read the focused field rather than commit
        // through the connection we negotiated with: it may have gone away.
        val target = currentTarget() ?: return

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val clipLabel = picto.label.ifBlank { picto.spokenText }
        val description = ClipDescription(
            if (attribution != null) "$clipLabel — $attribution" else clipLabel,
            arrayOf(mime),
        )
        val committed = InputConnectionCompat.commitContent(
            target.connection,
            target.editorInfo,
            InputContentInfoCompat(uri, description, null),
            InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION,
            null,
        )
        // Say so rather than leaving a long-press that silently did nothing.
        if (!committed) onError(R.string.img_unsupported)
    }

    /**
     * Builds a 512x512 card holding the pictogram with its [caption] written
     * across the bottom -- so the word is part of the image that's sent --
     * framed in [frameColor] like the on-screen key (white fill, rounded,
     * transparent corners). When [attribution] is non-null (ARASAAC pictos) a
     * small blue credit line is drawn beneath the caption so the licence credit
     * travels with the picture. Saved as lossless WEBP (for WhatsApp stickers)
     * or PNG per [mime]. Returns null if the source image can't be decoded.
     *
     * The size ratios are load-bearing for the WhatsApp sticker format; do not
     * adjust them.
     */
    private suspend fun labeledImage(
        source: File,
        id: String,
        caption: String,
        frameColor: Int,
        attribution: String?,
        mime: String,
    ): File? = withContext(Dispatchers.IO) {
        runCatching {
            val src = android.graphics.BitmapFactory.decodeFile(source.absolutePath)
                ?: return@runCatching null
            val size = 512
            val pad = size * 0.06f
            val corner = size * 0.10f
            val strokeWidth = size * 0.045f
            val out = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(out)

            // Rounded white card with a coloured frame (corners left transparent).
            val rect = android.graphics.RectF(
                strokeWidth / 2f,
                strokeWidth / 2f,
                size - strokeWidth / 2f,
                size - strokeWidth / 2f,
            )
            val fill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
            }
            canvas.drawRoundRect(rect, corner, corner, fill)

            // Caption band across the bottom; shrink the word until it fits the width.
            val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.BLACK
                textAlign = android.graphics.Paint.Align.CENTER
                typeface =
                    android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            }
            // Small blue ARASAAC attribution line drawn beneath the caption.
            val attrPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF1565C0.toInt()
                textAlign = android.graphics.Paint.Align.CENTER
            }
            var captionHeight = 0f
            if (caption.isNotBlank()) {
                textPaint.textSize = size * 0.16f
                while (textPaint.textSize > size * 0.07f && textPaint.measureText(caption) > size - 2 * pad) {
                    textPaint.textSize -= 2f
                }
                captionHeight = textPaint.fontSpacing
            }
            var attrHeight = 0f
            if (attribution != null) {
                attrPaint.textSize = size * 0.052f
                while (attrPaint.textSize > size * 0.032f && attrPaint.measureText(attribution) > size - 2 * pad) {
                    attrPaint.textSize -= 1f
                }
                attrHeight = attrPaint.fontSpacing
            }
            val bandHeight = if (captionHeight > 0f || attrHeight > 0f) captionHeight + attrHeight + pad else 0f

            // Fit the pictogram (preserving aspect) into the area above the band.
            val areaW = size - 2 * pad
            val areaH = size - 2 * pad - bandHeight
            val scale = minOf(areaW / src.width, areaH / src.height)
            val drawW = src.width * scale
            val drawH = src.height * scale
            val dst = android.graphics.RectF(
                pad + (areaW - drawW) / 2f,
                pad + (areaH - drawH) / 2f,
                pad + (areaW + drawW) / 2f,
                pad + (areaH + drawH) / 2f,
            )
            canvas.drawBitmap(src, null, dst, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))

            // Bottom text block: caption first, then the attribution line beneath it.
            var baseY = size - pad
            if (attribution != null) {
                canvas.drawText(attribution, size / 2f, baseY - attrPaint.fontMetrics.descent, attrPaint)
                baseY -= attrHeight
            }
            if (caption.isNotBlank()) {
                canvas.drawText(caption, size / 2f, baseY - textPaint.fontMetrics.descent, textPaint)
            }

            // Coloured frame on top of everything.
            val border = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                this.strokeWidth = strokeWidth
                color = frameColor
            }
            canvas.drawRoundRect(rect, corner, corner, border)

            val dir = File(context.filesDir, "shared").apply { mkdirs() }
            val ext = if (mime == "image/webp") "webp" else "png"
            val file = File(dir, "send_$id.$ext")
            file.outputStream().use { os ->
                val format = when {
                    mime != "image/webp" -> android.graphics.Bitmap.CompressFormat.PNG
                    android.os.Build.VERSION.SDK_INT >= 30 -> android.graphics.Bitmap.CompressFormat.WEBP_LOSSLESS
                    else -> {
                        @Suppress("DEPRECATION")
                        android.graphics.Bitmap.CompressFormat.WEBP
                    }
                }
                out.compress(format, 100, os)
            }
            file
        }.getOrNull()
    }
}
