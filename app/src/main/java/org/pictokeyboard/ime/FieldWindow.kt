package org.pictokeyboard.ime

import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection

/**
 * As much of the host's field as this keyboard can see, in the field's own
 * character positions.
 *
 * [offset] is where [text] begins in the field, so every position handed to
 * `setSelection` is absolute and every index into [text] is relative. Keeping
 * both in one object is the point: mixing the two is how a keyboard deletes the
 * wrong six characters of somebody's message.
 */
data class FieldWindow(
    val text: CharSequence,
    val offset: Int,
    val selStart: Int,
    val selEnd: Int,
    /** False when the window stopped short of the end of the field. */
    val tailComplete: Boolean,
) {

    fun spans(): List<WordSpan> = FieldWords.spans(text, offset, tailComplete)

    fun textOf(span: WordSpan): String =
        text.subSequence(span.start - offset, span.end - offset).toString()

    /** True when [at] is a position this window can actually reason about. */
    fun holds(at: Int): Boolean = at >= offset && at <= offset + text.length
}

/**
 * Getting that window out of an app this keyboard knows nothing about.
 *
 * Two ways, because there is no single call every host implements:
 *
 * - [ExtractedTextRequest] is the good one. It hands back the field's whole
 *   text *and* where the selection is inside it, from the app itself, so nothing
 *   has to be inferred.
 * - Failing that, the two calls every host does implement — text before the
 *   cursor and text after it — stitched back together around the selection the
 *   framework last reported. That is a window rather than the field, which is
 *   why [FieldWords.spans] drops words touching a cut edge.
 */
object FieldReader {

    fun read(connection: InputConnection, trackedStart: Int, trackedEnd: Int): FieldWindow? =
        extracted(connection) ?: around(connection, trackedStart, trackedEnd)

    private fun extracted(ic: InputConnection): FieldWindow? {
        val extracted = ic.getExtractedText(ExtractedTextRequest(), 0)
        val text = extracted?.text
        if (text == null || extracted.selectionStart < 0 || extracted.selectionEnd < 0) return null
        val offset = extracted.startOffset.coerceAtLeast(0)
        return FieldWindow(
            text = text,
            offset = offset,
            selStart = offset + minOf(extracted.selectionStart, extracted.selectionEnd),
            selEnd = offset + maxOf(extracted.selectionStart, extracted.selectionEnd),
            // -1 in either partial offset means the app sent the whole field.
            tailComplete = extracted.partialEndOffset < 0,
        )
    }

    private fun around(ic: InputConnection, trackedStart: Int, trackedEnd: Int): FieldWindow? {
        val usable = trackedStart >= 0 && trackedEnd >= 0
        val before = if (usable) ic.getTextBeforeCursor(FieldWords.WINDOW, 0) else null
        if (before == null) return null
        val after = ic.getTextAfterCursor(FieldWords.WINDOW, 0) ?: ""
        val selected = ic.getSelectedText(0) ?: ""
        val start = minOf(trackedStart, trackedEnd)
        return FieldWindow(
            text = "$before$selected$after",
            offset = (start - before.length).coerceAtLeast(0),
            selStart = start,
            selEnd = maxOf(trackedStart, trackedEnd),
            // A full window is the one case where more text may be waiting.
            tailComplete = after.length < FieldWords.WINDOW,
        )
    }
}
