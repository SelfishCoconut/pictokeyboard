package org.pictokeyboard.ime

import android.content.ClipDescription
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.pictokeyboard.App
import org.pictokeyboard.R
import org.pictokeyboard.data.db.CategoryEntity
import org.pictokeyboard.data.db.PictoEntity
import org.pictokeyboard.data.prefs.Settings
import org.pictokeyboard.tts.TtsManager

/**
 * The pictogram keyboard. Left strip = colour-coded categories, right grid =
 * pictos framed in their category colour. Tapping a picto inserts its text into
 * the focused field and speaks it aloud.
 */
class PictoKeyboardService : InputMethodService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val locator by lazy { App.locator() }
    private lateinit var tts: TtsManager

    private lateinit var categoryAdapter: CategoryAdapter
    private lateinit var pictoAdapter: PictoAdapter
    private lateinit var pictoGrid: RecyclerView
    private lateinit var emptyHint: TextView

    private lateinit var normalView: View
    private lateinit var blindView: BlindKeyboardView

    private var categories: List<CategoryEntity> = emptyList()
    private var selectedCategoryId: String? = null
    private var pictoJob: Job? = null
    private var settings: Settings = Settings()

    // --- Blind (eyes-free) mode state --------------------------------------
    private var blindMode = false
    private var blindLoaded = false
    private var blindCatIndex = 0
    private var blindPictoIndex = 0
    private var blindPictos: List<PictoEntity> = emptyList()

    override fun onCreate() {
        super.onCreate()
        tts = TtsManager(this)
    }

    override fun onCreateInputView(): View {
        normalView = layoutInflater.inflate(R.layout.keyboard_view, null)

        categoryAdapter = CategoryAdapter(onClick = ::onCategorySelected)
        pictoAdapter = PictoAdapter(onClick = ::onPictoTapped, onLongClick = ::sendPictoAsImage)

        normalView.findViewById<RecyclerView>(R.id.list_categories).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = categoryAdapter
        }
        pictoGrid = normalView.findViewById<RecyclerView>(R.id.grid_pictos).apply {
            layoutManager = GridLayoutManager(context, settings.gridColumns)
            adapter = pictoAdapter
        }
        emptyHint = normalView.findViewById(R.id.empty_hint)

        normalView.findViewById<Button>(R.id.key_settings).setOnClickListener { openSettings() }
        normalView.findViewById<Button>(R.id.key_switch).setOnClickListener { switchKeyboard() }
        normalView.findViewById<Button>(R.id.key_space).setOnClickListener { commit(" ") }
        normalView.findViewById<Button>(R.id.key_backspace).setOnClickListener { backspace() }
        normalView.findViewById<Button>(R.id.key_enter).setOnClickListener { onEnter() }

        blindView = BlindKeyboardView(this).apply {
            onSwipeVertical = { down -> changeBlindCategory(if (down) 1 else -1) }
            onSwipeHorizontal = { right -> changeBlindPicto(if (right) 1 else -1) }
            onSingleTap = { speakBlindCurrent() }
            onDoubleTap = { writeBlindCurrent() }
            onLongPress = {
                deleteLastWord()
                tts.speak(getString(R.string.blind_deleted), settings.defaultLanguage)
            }
        }

        val container = ModeSwitchFrameLayout(this).apply {
            onTwoFingerDoubleTap = { setBlindMode(!blindMode) }
            addView(
                normalView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
            addView(
                blindView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    resources.getDimensionPixelSize(R.dimen.kb_blind_height),
                ),
            )
        }

        observeCategories()
        applyMode()
        return container
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // Re-read settings so config changes apply next time the keyboard opens.
        scope.launch {
            settings = locator.settings.current()
            tts.setParams(settings.ttsRate, settings.ttsPitch)
            (pictoGrid.layoutManager as? GridLayoutManager)?.spanCount = settings.gridColumns
            blindMode = settings.blindMode
            applyMode()
            refreshPictos()
            if (blindMode && !blindLoaded) {
                blindCatIndex = 0
                blindLoaded = true
                loadBlindCategory()
            }
        }
    }

    /** Shows the keyboard for the active mode and hides the other. */
    private fun applyMode() {
        if (!::blindView.isInitialized) return
        normalView.visibility = if (blindMode) View.GONE else View.VISIBLE
        blindView.visibility = if (blindMode) View.VISIBLE else View.GONE
    }

    private fun observeCategories() {
        locator.pictoRepository.observeCategories()
            .onEach { list ->
                categories = list
                if (selectedCategoryId == null || categories.none { it.id == selectedCategoryId }) {
                    selectedCategoryId = list.firstOrNull()?.id
                }
                categoryAdapter.submit(list, selectedCategoryId)
                refreshPictos()
            }
            .launchIn(scope)
    }

    private fun onCategorySelected(category: CategoryEntity) {
        if (category.id == selectedCategoryId) return
        selectedCategoryId = category.id
        categoryAdapter.submit(categories, selectedCategoryId)
        refreshPictos()
    }

    private fun refreshPictos() {
        val categoryId = selectedCategoryId
        pictoJob?.cancel()
        if (categoryId == null) {
            pictoAdapter.submit(emptyList(), 0, settings.showLabels)
            updateEmptyHint(true)
            return
        }
        val category = categories.firstOrNull { it.id == categoryId }
        val color = category?.colorArgb ?: 0
        val borderStyle = category?.borderStyle ?: org.pictokeyboard.data.db.BorderStyles.SOLID
        val borderWidthDp = category?.borderWidthDp ?: org.pictokeyboard.data.db.BorderStyles.DEFAULT_WIDTH_DP
        pictoJob = locator.pictoRepository.observePictos(categoryId)
            .onEach { pictos ->
                pictoAdapter.submit(pictos, color, settings.showLabels, borderStyle, borderWidthDp)
                updateEmptyHint(pictos.isEmpty())
            }
            .launchIn(scope)
    }

    private fun updateEmptyHint(empty: Boolean) {
        if (!::emptyHint.isInitialized) return
        emptyHint.visibility = if (empty && categories.isEmpty()) View.VISIBLE else View.GONE
    }

    // --- Input actions -----------------------------------------------------

    private fun onPictoTapped(picto: org.pictokeyboard.data.db.PictoEntity) {
        val text = picto.spokenText.ifBlank { picto.label }
        if (text.isBlank()) return
        val toInsert = if (settings.addSpaceAfter) "$text " else text
        commit(toInsert)
        if (settings.speakOnTap) tts.speak(text, picto.language)
        scope.launch { locator.pictoRepository.recordUsage(picto) }
    }

    private fun commit(text: String) {
        currentInputConnection?.commitText(text, 1)
    }

    /**
     * Long-press: send the pictogram as an image into the focused field via the
     * Commit Content API (the mechanism keyboards use for GIFs/stickers). The
     * picto's caption is rendered onto the image — framed like the on-screen key
     * with the word across the bottom — so the text travels with the picture.
     * For WhatsApp it's a 512×512 WEBP so it arrives as a sticker; other apps get
     * a PNG. Falls back to a toast if the field can't accept rich content.
     */
    private fun sendPictoAsImage(picto: PictoEntity) {
        val ic = currentInputConnection ?: return
        val editorInfo = currentInputEditorInfo ?: return
        val source = picto.imagePath?.let { java.io.File(it) }
        if (source == null || !source.exists()) {
            toast(R.string.img_not_ready)
            return
        }
        val supported = EditorInfoCompat.getContentMimeTypes(editorInfo)
        if (supported.isEmpty()) {
            toast(R.string.img_unsupported)
            return
        }
        fun accepts(mime: String) = supported.any { ClipDescription.compareMimeTypes(mime, it) }
        val isWhatsApp = editorInfo.packageName?.startsWith("com.whatsapp") == true
        val caption = picto.label.ifBlank { picto.spokenText }.trim()
        val frameColor = picto.colorArgbOverride
            ?: categories.firstOrNull { it.id == picto.categoryId }?.colorArgb
            ?: android.graphics.Color.LTGRAY
        // ARASAAC's licence requires attribution to travel with the picture, so
        // ARASAAC-sourced pictos (arasaacId != null) get a small visible credit
        // baked on and the same text copied into the clip description. Imported
        // images aren't ARASAAC's, so they carry none.
        val attribution = if (picto.arasaacId != null) getString(R.string.arasaac_share_attribution) else null

        val mime = when {
            isWhatsApp && accepts("image/webp") -> "image/webp"
            accepts("image/png") -> "image/png"
            accepts("image/webp") -> "image/webp"
            accepts("image/*") -> "image/png"
            else -> null
        }
        val file = mime?.let { labeledImage(source, picto.id, caption, frameColor, attribution, it) }
        if (file == null || mime == null) {
            toast(R.string.img_unsupported)
            return
        }

        val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val clipLabel = picto.label.ifBlank { picto.spokenText }
        val description = ClipDescription(
            if (attribution != null) "$clipLabel — $attribution" else clipLabel,
            arrayOf(mime),
        )
        val content = InputContentInfoCompat(uri, description, null)
        InputConnectionCompat.commitContent(
            ic,
            editorInfo,
            content,
            InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION,
            null,
        )
    }

    /**
     * Builds a 512×512 card holding the pictogram with its [caption] written
     * across the bottom — so the word is part of the image that's sent — framed
     * in [frameColor] like the on-screen key (white fill, rounded, transparent
     * corners). When [attribution] is non-null (ARASAAC pictos) a small blue
     * credit line is drawn beneath the caption so the licence credit travels with
     * the picture. Saved as lossless WEBP (for WhatsApp stickers) or PNG per
     * [mime]. Returns null if the source image can't be decoded.
     */
    private fun labeledImage(
        source: java.io.File,
        id: String,
        caption: String,
        frameColor: Int,
        attribution: String?,
        mime: String,
    ): java.io.File? = runCatching {
        val src = android.graphics.BitmapFactory.decodeFile(source.absolutePath) ?: return null
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

        val dir = java.io.File(filesDir, "shared").apply { mkdirs() }
        val ext = if (mime == "image/webp") "webp" else "png"
        val file = java.io.File(dir, "send_$id.$ext")
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

    private fun toast(resId: Int) {
        android.widget.Toast.makeText(this, resId, android.widget.Toast.LENGTH_SHORT).show()
    }

    /**
     * Deletes the last whole word before the cursor (along with any whitespace
     * trailing it), since each picto inserts a full word. If text is selected,
     * the selection is removed instead. Shared with blind mode's long-press.
     */
    private fun deleteLastWord() {
        val ic = currentInputConnection ?: return
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            ic.commitText("", 1)
            return
        }
        val before = ic.getTextBeforeCursor(WORD_LOOKBACK, 0) ?: return
        val count = trailingWordLength(before)
        if (count > 0) ic.deleteSurroundingText(count, 0)
    }

    private fun backspace() = deleteLastWord()

    private fun onEnter() {
        val ic = currentInputConnection ?: return
        val action = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
        if (action != null && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action)
        } else {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }
    }

    private fun switchKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        val switched = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            switchToNextInputMethod(false)
        } else {
            false
        }
        if (!switched) imm.showInputMethodPicker()
    }

    private fun openSettings() {
        val intent = Intent(this, Class.forName("org.pictokeyboard.ui.MainActivity")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    // --- Blind (eyes-free) mode --------------------------------------------

    /** Switches blind mode on/off, persists it, and announces the new state. */
    private fun setBlindMode(enabled: Boolean) {
        blindMode = enabled
        scope.launch { locator.settings.setBlindMode(enabled) }
        applyMode()
        if (enabled) {
            blindCatIndex = 0
            blindLoaded = true
            loadBlindCategory(extra = getString(R.string.blind_on))
        } else {
            blindLoaded = false
            tts.speak(getString(R.string.blind_off), settings.defaultLanguage)
        }
    }

    private fun changeBlindCategory(direction: Int) {
        if (categories.isEmpty()) {
            loadBlindCategory()
            return
        }
        blindCatIndex = (blindCatIndex + direction).mod(categories.size)
        loadBlindCategory()
    }

    private fun changeBlindPicto(direction: Int) {
        if (blindPictos.isEmpty()) {
            speakBlindCurrent()
            return
        }
        blindPictoIndex = (blindPictoIndex + direction).mod(blindPictos.size)
        speakBlindCurrent()
    }

    /** Loads the pictos for the current category and announces it + its first picto. */
    private fun loadBlindCategory(extra: String? = null) {
        val cat = categories.getOrNull(blindCatIndex)
        if (cat == null) {
            blindView.setCaption("")
            blindView.setHint(getString(R.string.blind_no_board))
            tts.speak(getString(R.string.blind_no_board), settings.defaultLanguage)
            return
        }
        scope.launch {
            blindPictos = locator.pictoRepository.pictos(cat.id)
            blindPictoIndex = 0
            speakBlindCurrent(announcements = listOfNotNull(extra, cat.name))
        }
    }

    /**
     * Updates the blind surface caption and speaks the current picto. Any
     * [announcements] (mode/category names) are spoken in the board's default
     * language; the picto itself is spoken in its own language, each as its own
     * utterance so the voices don't bleed into one another.
     */
    private fun speakBlindCurrent(announcements: List<String> = emptyList()) {
        val cat = categories.getOrNull(blindCatIndex)
        blindView.setHint(cat?.name ?: "")
        val parts = announcements.map { TtsManager.Part(it, settings.defaultLanguage) }.toMutableList()
        val picto = blindPictos.getOrNull(blindPictoIndex)
        if (picto == null) {
            val msg = getString(R.string.blind_empty_category)
            blindView.setCaption(msg)
            parts += TtsManager.Part(msg, settings.defaultLanguage)
        } else {
            val label = picto.spokenText.ifBlank { picto.label }
            blindView.setCaption(label)
            parts += TtsManager.Part(label, picto.language)
        }
        tts.speakSequence(parts)
    }

    private fun writeBlindCurrent() {
        val picto = blindPictos.getOrNull(blindPictoIndex) ?: return
        val text = picto.spokenText.ifBlank { picto.label }
        if (text.isBlank()) return
        commit(if (settings.addSpaceAfter) "$text " else text)
        tts.speak(text, picto.language)
        scope.launch { locator.pictoRepository.recordUsage(picto) }
    }

    override fun onDestroy() {
        scope.cancel()
        tts.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val WORD_LOOKBACK = 128

        /**
         * Number of characters to delete to remove the last word from [text]:
         * the run of trailing whitespace plus the word before it.
         */
        fun trailingWordLength(text: CharSequence): Int {
            var i = text.length
            while (i > 0 && text[i - 1].isWhitespace()) i--
            while (i > 0 && !text[i - 1].isWhitespace()) i--
            return text.length - i
        }
    }
}
