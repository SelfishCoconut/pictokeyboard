package org.pictokeyboard.ime

import android.content.Context
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.pictokeyboard.App
import org.pictokeyboard.R
import org.pictokeyboard.data.db.BorderStyles
import org.pictokeyboard.data.db.CategoryEntity
import org.pictokeyboard.data.db.PictoEntity
import org.pictokeyboard.data.prefs.Settings
import org.pictokeyboard.tts.TtsManager
import org.pictokeyboard.ui.MainActivity
import org.pictokeyboard.ui.theme.CategoryColors

/**
 * The pictogram keyboard. Left strip = colour-coded categories, right grid =
 * pictos framed in their category colour. Tapping a picto inserts its text into
 * the focused field and speaks it aloud.
 */
class PictoKeyboardService : InputMethodService() {

    /**
     * An uncaught exception in a `launch` here reaches the thread's default
     * handler and kills the process -- and for an IME that reads to the user as
     * "my keyboard crashed in every app", mid-sentence, with no way to finish
     * what they were saying. A communication aid failing closed is worse than
     * one failing quietly, so the handler logs and lets the keyboard stand.
     */
    private val crashGuard = CoroutineExceptionHandler { _, error ->
        Log.e(TAG, "Uncaught exception in keyboard scope; keeping the keyboard up", error)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + crashGuard)
    private val locator by lazy { App.locator() }
    private lateinit var tts: TtsManager
    private lateinit var imageSharer: PictoImageSharer

    private lateinit var categoryAdapter: CategoryAdapter
    private lateinit var pictoAdapter: PictoAdapter
    private lateinit var pictoGrid: RecyclerView
    private lateinit var keyboardBody: View
    private lateinit var emptyHint: TextView

    private lateinit var normalView: View
    private lateinit var blindView: BlindKeyboardView

    /**
     * Resources in the app's chosen language. Rebuilt whenever the setting
     * changes, because an InputMethodService gets no callback for it below
     * API 33 -- see [localizedFor].
     */
    private var uiContext: Context = this

    /** The language [uiContext] and the current input view were built for. */
    private var viewLanguage: String? = null

    private var categories: List<CategoryEntity> = emptyList()

    /** Category id -> resolved Coil model, kept so a rebuilt chip strip can be
     * repopulated without going back to disk. See [keyboardIconModel]. */
    private var categoryIcons: Map<String, Any?> = emptyMap()
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
        applyLanguage(currentAppLanguage())
        tts = TtsManager(this)
        imageSharer = PictoImageSharer(this)
        // Deliberately here and not in onCreateInputView. This feeds *service*
        // state -- `categories`, `selectedCategoryId` -- which outlives any one
        // input view, and onCreateInputView runs again on every rotation, dark
        // mode switch and font scale change. Started there, each recreation
        // stacked another permanent collector on a scope that lives until
        // onDestroy: N redundant Room queries and N refreshPictos() per emission,
        // on the typing thread of a keyboard.
        observeCategories()
    }

    override fun onCreateInputView(): View {
        // cloneInContext, not LayoutInflater.from(uiContext): the clone keeps the
        // service's theme while resolving strings against the chosen language.
        // Building a fresh inflater would drop the keyboard's styling.
        normalView = layoutInflater.cloneInContext(uiContext)
            .inflate(R.layout.keyboard_view, null)

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
        keyboardBody = normalView.findViewById(R.id.keyboard_body)
        emptyHint = normalView.findViewById(R.id.empty_hint)
        applyBodyHeight()

        normalView.findViewById<Button>(R.id.key_settings).setOnClickListener { openSettings() }
        // An ImageButton, not a Button -- the globe is a tinted vector now.
        normalView.findViewById<View>(R.id.key_switch).setOnClickListener { switchKeyboard() }
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
                tts.speak(uiContext.getString(R.string.blind_deleted), settings.defaultLanguage)
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

        // The collection lives in onCreate, so these adapters are new but the
        // data is not. Seed them from what is already held, or a rotation would
        // show an empty board until the next database emission -- which for a
        // board nobody is editing may never come.
        categoryAdapter.submit(categories, selectedCategoryId, categoryIcons)
        refreshPictos()

        viewLanguage = currentAppLanguage()
        applyMode()
        return container
    }

    /** Points [uiContext] at [language]. */
    private fun applyLanguage(language: String?) {
        uiContext = localizedFor(language)
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // The one place the keyboard can notice a language change. Below API 33
        // appcompat only applies the per-app locale to AppCompatActivity, so no
        // configuration change reaches this service and nothing else would ever
        // tell it. Rebuilding the whole input view is heavy-handed, but the key
        // captions are inflated from a layout and there is no lighter way to
        // re-resolve them -- and it happens only on a change the user just made.
        val language = currentAppLanguage()
        if (language != viewLanguage) {
            applyLanguage(language)
            setInputView(onCreateInputView())
        }
        // Re-read settings so config changes apply next time the keyboard opens.
        scope.launch {
            settings = locator.settings.current()
            tts.setParams(settings.ttsRate, settings.ttsPitch)
            // onCreateInputView is gated on onEvaluateInputViewShown() and this
            // is gated on mShowInputRequested -- different conditions, so with a
            // hardware keyboard attached you can reach onStartInputView with no
            // onCreateInputView in between and no pictoGrid to touch.
            if (::pictoGrid.isInitialized) {
                (pictoGrid.layoutManager as? GridLayoutManager)?.spanCount = settings.gridColumns
            }
            // Height follows the same settings read, so the slider takes effect
            // the next time the keyboard opens rather than only after a restart.
            applyBodyHeight()
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

    /**
     * Sizes the picto body from `gridRows`, which nothing read before this.
     *
     * The slider persisted a value and the body took 280dp from a dimension
     * resource regardless, so dragging it from 4 to 8 changed nothing.
     */
    private fun applyBodyHeight() {
        if (!::keyboardBody.isInitialized) return
        val metrics = resources.displayMetrics
        keyboardBody.updateLayoutParams {
            height = KeyboardMetrics.bodyHeightPx(
                screenWidthPx = metrics.widthPixels,
                screenHeightPx = metrics.heightPixels,
                categoryStripPx = resources.getDimensionPixelSize(R.dimen.kb_category_width),
                columns = settings.gridColumns,
                rows = settings.gridRows,
            )
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
            // Icons resolve upstream of the collector, so the filesystem stat
            // each one needs happens on IO once per emission rather than on the
            // main thread once per bind.
            .map { list -> list to list.associate { it.id to it.keyboardIconModel() } }
            .flowOn(Dispatchers.IO)
            .onEach { (list, icons) ->
                categories = list
                categoryIcons = icons
                if (selectedCategoryId == null || categories.none { it.id == selectedCategoryId }) {
                    selectedCategoryId = list.firstOrNull()?.id
                }
                // Guarded because this now runs from onCreate, which precedes the
                // first onCreateInputView -- and with a hardware keyboard attached
                // may precede it indefinitely.
                if (::categoryAdapter.isInitialized) {
                    categoryAdapter.submit(list, selectedCategoryId, icons)
                }
                refreshPictos()
            }
            .launchIn(scope)
    }

    private fun onCategorySelected(category: CategoryEntity) {
        if (category.id == selectedCategoryId) return
        selectedCategoryId = category.id
        categoryAdapter.submit(categories, selectedCategoryId, categoryIcons)
        refreshPictos()
    }

    private fun refreshPictos() {
        val categoryId = selectedCategoryId
        pictoJob?.cancel()
        // Reachable before the first onCreateInputView now that the category
        // collection starts in onCreate.
        if (!::pictoAdapter.isInitialized) return
        if (categoryId == null) {
            pictoAdapter.submit(
                pictos = emptyList(),
                imageModels = emptyMap(),
                style = PictoAdapter.Style(categoryColor = 0, showLabels = settings.showLabels),
            )
            applyCategoryWash(null)
            updateEmptyHint(true)
            return
        }
        val category = categories.firstOrNull { it.id == categoryId }
        // Pass the nullable through: applyCategoryWash maps null to transparent,
        // whereas 0 is opaque black and washes the grid 6% grey.
        applyCategoryWash(category?.colorArgb)
        val style = PictoAdapter.Style(
            categoryColor = category?.colorArgb ?: 0,
            showLabels = settings.showLabels,
            borderStyle = category?.borderStyle ?: BorderStyles.SOLID,
            borderWidthDp = category?.borderWidthDp ?: BorderStyles.DEFAULT_WIDTH_DP,
        )
        pictoJob = locator.pictoRepository.observePictos(categoryId)
            // Same reason as the category strip: the per-picto filesystem stat
            // belongs off the scrolling path.
            .map { pictos -> pictos to pictos.associate { it.id to it.keyboardImageModel() } }
            .flowOn(Dispatchers.IO)
            .onEach { (pictos, models) ->
                pictoAdapter.submit(pictos, models, style)
                updateEmptyHint(pictos.isEmpty())
            }
            .launchIn(scope)
    }

    /**
     * Floods the picto grid with a 6% wash of the selected category's colour.
     *
     * This is the signature of the design: tap *Comida* and the whole board reads
     * orange, tap *Acciones* and it reads green. A user who cannot read gets a
     * full-field, pre-linguistic signal of which context they are in — which is
     * precisely the job AAC colour coding exists to do, and was previously spent
     * on a 3dp frame.
     *
     * 6% is deliberately faint: it sits *under* white picto tiles and must not
     * touch their contrast, so it registers as a cast rather than as a colour.
     */
    private fun applyCategoryWash(colorArgb: Int?) {
        if (!::pictoGrid.isInitialized) return
        pictoGrid.setBackgroundColor(
            if (colorArgb == null) {
                android.graphics.Color.TRANSPARENT
            } else {
                CategoryColors.wash(colorArgb)
            },
        )
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
        recordUsage(picto)
    }

    private fun commit(text: String) {
        currentInputConnection?.commitText(text, 1)
    }

    /**
     * Remembers the picto for the "Suggested" category, unless the field it went
     * into asked not to be learned from — see [allowsUsageRecording].
     *
     * The field is read here rather than inside the coroutine on purpose. The
     * question is about the field the word was actually committed to, and by the
     * time a launched block runs the focus may have moved somewhere else; a
     * password box that loses focus first would otherwise be recorded against
     * whatever followed it.
     */
    private fun recordUsage(picto: PictoEntity) {
        if (!currentInputEditorInfo.allowsUsageRecording()) return
        scope.launch { locator.pictoRepository.recordUsage(picto) }
    }

    /**
     * The field being typed into right now, or null if the keyboard is not
     * attached to one. Read on demand so callers that suspend see the field the
     * user is actually in, not the one they started in.
     */
    private fun currentTarget(): PictoImageSharer.Target? {
        val connection = currentInputConnection
        val editorInfo = currentInputEditorInfo
        return if (connection != null && editorInfo != null) {
            PictoImageSharer.Target(connection, editorInfo)
        } else {
            null
        }
    }

    /**
     * Long-press: send the pictogram as an image into the focused field. The
     * caption is rendered onto the picture so the word travels with it; for
     * WhatsApp it arrives as a sticker, other apps get a PNG.
     *
     * ARASAAC-sourced pictos (arasaacId != null) carry a baked-on licence
     * credit. Imported images aren't ARASAAC's, so they carry none.
     */
    private fun sendPictoAsImage(picto: PictoEntity) {
        val frameColor = picto.colorArgbOverride
            ?: categories.firstOrNull { it.id == picto.categoryId }?.colorArgb
            ?: android.graphics.Color.LTGRAY
        val attribution =
            if (picto.arasaacId != null) uiContext.getString(R.string.arasaac_share_attribution) else null
        scope.launch {
            imageSharer.send(picto, frameColor, attribution, ::currentTarget) { resId ->
                android.widget.Toast.makeText(
                    this@PictoKeyboardService,
                    resId,
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            }
        }
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
        // A direct class reference, not Class.forName: the reflective form only
        // worked because isMinifyEnabled = false. The first release build with
        // R8 on would rename MainActivity and turn the gear key into a
        // ClassNotFoundException thrown from a click handler.
        val intent = Intent(this, MainActivity::class.java).apply {
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
            loadBlindCategory(extra = uiContext.getString(R.string.blind_on))
        } else {
            blindLoaded = false
            tts.speak(uiContext.getString(R.string.blind_off), settings.defaultLanguage)
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

    /**
     * The blind surface, or null before the first `onCreateInputView`.
     *
     * Blind mode is audio-first by design, so every caller here writes to the
     * view through this and speaks unconditionally: with no surface to draw on
     * the announcement is the entire interface, and suppressing it would be the
     * one failure mode a blind user could not work around.
     */
    private fun blindSurface(): BlindKeyboardView? =
        if (::blindView.isInitialized) blindView else null

    /** Loads the pictos for the current category and announces it + its first picto. */
    private fun loadBlindCategory(extra: String? = null) {
        val cat = categories.getOrNull(blindCatIndex)
        if (cat == null) {
            blindSurface()?.apply {
                setCaption("")
                setSurfaceColor(null)
                setHint(uiContext.getString(R.string.blind_no_board))
            }
            tts.speak(uiContext.getString(R.string.blind_no_board), settings.defaultLanguage)
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
        val surface = blindSurface()
        surface?.setHint(cat?.name ?: "")
        // The whole surface takes the category's hue, so the cue is unmissable
        // even to someone who can only make out large blocks of colour.
        surface?.setSurfaceColor(cat?.colorArgb)
        val parts = announcements.map { TtsManager.Part(it, settings.defaultLanguage) }.toMutableList()
        val picto = blindPictos.getOrNull(blindPictoIndex)
        if (picto == null) {
            val msg = uiContext.getString(R.string.blind_empty_category)
            surface?.setCaption(msg)
            parts += TtsManager.Part(msg, settings.defaultLanguage)
        } else {
            val label = picto.spokenText.ifBlank { picto.label }
            surface?.setCaption(label)
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
        recordUsage(picto)
    }

    override fun onDestroy() {
        scope.cancel()
        tts.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "PictoKeyboard"
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
