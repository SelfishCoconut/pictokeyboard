package org.pictokeyboard.ime

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.pictokeyboard.App
import org.pictokeyboard.R
import org.pictokeyboard.data.db.BoardEntity
import org.pictokeyboard.data.db.BorderStyles
import org.pictokeyboard.data.db.CategoryEntity
import org.pictokeyboard.data.db.PictoEntity
import org.pictokeyboard.data.prefs.Settings
import org.pictokeyboard.ime.PressFeedback.confirmPress
import org.pictokeyboard.sentence.TypedWord
import org.pictokeyboard.tts.TtsManager
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
    private lateinit var boardTabAdapter: BoardTabAdapter
    private lateinit var pictoGrid: RecyclerView
    private lateinit var boardStrip: RecyclerView
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

    /** Rebuilt whenever settings or the night configuration change. */
    private var palette: KeyboardPalette? = null

    /**
     * The board in use, and the source of every layout value the grid needs.
     *
     * Null only in the window before the first database read lands — during
     * which the accessors below stand in the schema defaults, which is what the
     * grid would have drawn anyway. A keyboard must never wait on a query to
     * put keys on screen.
     */
    private var board: BoardEntity? = null
    private val boardColumns get() = board?.columns ?: BoardEntity.DEFAULT_COLUMNS
    private val boardRows get() = board?.rows ?: BoardEntity.DEFAULT_ROWS
    private val boardShowLabels get() = board?.showLabels ?: true

    /** Boards offered as tabs, and their pictos, kept as [categories] is. */
    private var boards: List<BoardEntity> = emptyList()
    private var boardIcons: Map<String, Any?> = emptyMap()

    /**
     * The strip earns its height only when there is a choice to make. One board
     * is everyone on day one, and a tab strip with a single tab in it is 52dp
     * spent saying nothing — taken from the grid, which is the whole product.
     */
    private val showBoardTabs get() = boards.size >= 2

    /**
     * The phrase written so far and the arrows that walk it (#143). A record of
     * what reached the field, never a buffer holding it back — see [Sentence].
     *
     * Nothing draws it since #148. It is held by the service rather than by the
     * view anyway, because the input view is rebuilt on every rotation and
     * dark-mode switch, and 🔊 forgetting the sentence because somebody turned
     * their phone would be worse than not offering it.
     */
    private val phrase by lazy {
        PhraseController(
            connection = { currentInputConnection },
            tts = tts,
            fallbackLanguage = { board?.language ?: settings.defaultLanguage },
            announce = ::announce,
        )
    }

    /**
     * Beautify's own state: the binder to the model process, and the exact
     * characters this keyboard put in the field (#46).
     *
     * Beside [phrase] rather than inside it because [Sentence] holds words --
     * trimmed, each with its language, for speech -- while this has to track the
     * literal string in the host's editor, spacing and all, or a swap cannot be
     * checked before it is made.
     */
    private val beautify by lazy {
        BeautifyController(
            context = this,
            connection = { currentInputConnection },
            onStateChanged = ::renderBeautifyKey,
            announce = ::announce,
        )
    }

    /**
     * What the keyboard says on screen, drawn in its own window (#149).
     *
     * Not a Toast: a Toast from this app is silently dropped, because Android
     * suppresses them for apps whose notifications are off and this one has no
     * reason to ask for that permission.
     */
    private val message = KeyboardMessage()

    /** The bell, and the countdown a second press cancels (#144). */
    private val assistance by lazy {
        AssistanceController(
            context = this,
            onEvent = ::onCallEvent,
            onStateChanged = ::renderAssistanceKey,
        )
    }

    /**
     * How much of the keyboard the navigation bar is sitting on, in pixels.
     *
     * Targeting SDK 35 or above puts the input view edge to edge, and the
     * navigation bar then draws *over* its bottom strip rather than below it.
     * That strip is the action row — space, enter and the globe — so the row a
     * thumb reaches for first was the row the phone's own buttons had covered.
     *
     * Kept as state rather than read on demand because it arrives from the
     * framework asynchronously, after the view is already laid out, and because
     * [chromeHeightPx] has to include it: the padding is height the grid does
     * not get, exactly like the phrase keys above it. Zero on gesture
     * navigation and on every release before 15, where the window is placed
     * above the bar and there is nothing to pay for.
     */
    private var navigationBarInsetPx = 0

    /**
     * The eyes-free keyboard, which is a second keyboard sharing this shell.
     *
     * It has no keys and no grid — only gestures and a voice —
     * so it lives in its own class and reaches back through [BlindHost] for the
     * board data and the few field operations it shares with the sighted one.
     */
    private val blind by lazy {
        BlindModeController(
            host = BlindHost(
                categories = { categories },
                language = { settings.defaultLanguage },
                strings = { uiContext },
                pictosIn = { locator.pictoRepository.pictos(it) },
                writePicto = ::writeStraightToField,
                deleteWord = ::deleteLastWord,
                rememberEnabled = { on -> scope.launch { locator.settings.setBlindMode(on) } },
            ),
            tts = tts,
            scope = scope,
        )
    }

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
        observeBoards()
    }

    /**
     * The grid is shown even when a hardware keyboard is attached.
     *
     * The default hides a soft keyboard whenever the system thinks the user has
     * a physical one, on the reasonable assumption that a person with keys under
     * their fingers does not need keys on their screen. That assumption does not
     * survive contact with this app's users. The person here does not type on
     * the hardware keyboard at all — they may not be able to — and very often
     * the "keyboard" the system has detected is not one: switch interfaces,
     * head-pointer and eye-gaze receivers and adapted keypads all present as
     * ordinary HID keyboards, and connecting one is *more* likely for an AAC user
     * than for anybody else.
     *
     * Left to the default, plugging in the device that lets somebody operate the
     * phone is what takes their pictograms away. The words are their voice; the
     * recovery is a system setting several screens deep that a caregiver would
     * have to know exists. So the grid stays.
     *
     * Every general-purpose keyboard does the same thing for its own reasons.
     * The cost is a keyboard on screen that somebody with a real keyboard did not
     * want, and they can dismiss it with the close key that is already there.
     */
    override fun onEvaluateInputViewShown(): Boolean {
        // Called for its own sake, not for its answer. The base implementation is
        // annotated @CallSuper because it lazily installs the observer that
        // watches the "show on-screen keyboard" setting; skipping it leaves that
        // machinery uninitialised. Its verdict is then discarded on purpose.
        super.onEvaluateInputViewShown()
        return true
    }

    override fun onCreateInputView(): View {
        // cloneInContext, not LayoutInflater.from(uiContext): the clone keeps the
        // service's theme while resolving strings against the chosen language.
        // Building a fresh inflater would drop the keyboard's styling.
        normalView = layoutInflater.cloneInContext(uiContext)
            .inflate(R.layout.keyboard_view, null)

        bindLists()
        bindKeys()

        blindView = BlindKeyboardView(this).also(blind::attach)

        val container = ModeSwitchFrameLayout(this).apply {
            onTwoFingerDoubleTap = {
                blind.toggle()
                applyMode()
            }
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
            // Added last so it draws over both keyboards.
            message.attach(this)
        }

        // The collection lives in onCreate, so these adapters are new but the
        // data is not. Seed them from what is already held, or a rotation would
        // show an empty board until the next database emission -- which for a
        // board nobody is editing may never come. The sentence goes the same way:
        // it belongs to the conversation, not to this instance of the view.
        categoryAdapter.submit(categories, selectedCategoryId, categoryIcons)
        applyBoardTabs()
        refreshPictos()

        viewLanguage = currentAppLanguage()
        applyMode()
        keepClearOfNavigationBar(container)
        return container
    }

    /**
     * Pads [root] out from under the navigation bar, and pays for it.
     *
     * The listener rather than a one-off read: insets are dispatched after the
     * view is attached, they change when the phone is rotated or the user
     * switches between gesture and three-button navigation, and an IME's view
     * outlives all of those. Returning the insets unconsumed leaves the rest of
     * the hierarchy free to read them too.
     *
     * [applyBodyHeight] is called again because the padding is chrome: without
     * it the keyboard would simply grow by the height of the navigation bar and
     * break the 60% ceiling that stops a tall board eating the screen.
     */
    private fun keepClearOfNavigationBar(root: View) {
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            if (bottom != navigationBarInsetPx) {
                navigationBarInsetPx = bottom
                view.updatePadding(bottom = bottom)
                applyBodyHeight()
            }
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    /** The three lists the keyboard draws: boards across, categories down, pictos in the grid. */
    private fun bindLists() {
        // `{ settings.hapticFeedback }`, not `settings.hapticFeedback`: the
        // adapters are built once, here, and the caregiver may turn haptics off
        // from the app long afterwards while this service is still alive. A
        // captured Boolean would hold whatever the setting was the last time the
        // keyboard was created, which for an IME can be days.
        categoryAdapter = CategoryAdapter(
            onClick = ::onCategorySelected,
            haptics = { settings.hapticFeedback },
            palette = { palette },
        )
        pictoAdapter = PictoAdapter(
            onClick = ::onPictoTapped,
            onLongClick = ::sendPictoAsImage,
            haptics = { settings.hapticFeedback },
            palette = { palette },
        )
        boardTabAdapter = BoardTabAdapter(
            onClick = ::onBoardSelected,
            haptics = { settings.hapticFeedback },
            palette = { palette },
        )

        normalView.findViewById<RecyclerView>(R.id.list_categories).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = categoryAdapter
        }
        boardStrip = normalView.findViewById<RecyclerView>(R.id.list_boards).apply {
            layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
            adapter = boardTabAdapter
        }
        pictoGrid = normalView.findViewById<RecyclerView>(R.id.grid_pictos).apply {
            layoutManager = GridLayoutManager(context, boardColumns)
            adapter = pictoAdapter
        }
        keyboardBody = normalView.findViewById(R.id.keyboard_body)
        emptyHint = normalView.findViewById(R.id.empty_hint)
        applyBodyHeight()
    }

    /**
     * The keys: the phrase's own row, and three in the action row.
     *
     * Looked up as [View] rather than as `Button`, because half of them are not
     * buttons: the globe, the speaker and the ✕ are `AppCompatImageButton`s so
     * their glyphs take the key's text colour instead of an emoji font's own
     * palette. A `findViewById<Button>` on one of those is a ClassCastException
     * thrown from `onCreateInputView` — which is to say, a keyboard that dies the
     * moment it is opened, in every app.
     */
    private fun bindKeys() {
        listOf(
            R.id.key_switch to { switchKeyboard() },
            R.id.key_space to { commit(" ") },
            R.id.key_prev_word to { phrase.step(forward = false) },
            R.id.key_next_word to { phrase.step(forward = true) },
            R.id.key_backspace to { backspace() },
            R.id.key_enter to { onEnter() },
            R.id.key_beautify to { beautify.press(typedWords()) },
            R.id.key_speak to { speakPhrase() },
            R.id.key_assistance to { assistance.press() },
        ).forEach { (id, action) ->
            normalView.findViewById<View>(id).setOnClickListener {
                it.confirmPress(settings.hapticFeedback)
                // A live selection plus any key that commits text is a
                // replacement: pressing space with `galleta` highlighted would
                // otherwise overwrite it. Everything except the arrows and the
                // backspace that acts on the selection lets go of it first.
                if (id !in KEEPS_SELECTION) phrase.navigator.stop()
                // Moving on from a rephrase accepts it and closes the undo
                // window (#166) -- but only the keys that actually move on
                // (#173).
                if (id !in KEEPS_UNDO) beautify.onAccepted()
                action()
            }
        }
    }

    /**
     * Repaints the chrome the layout could not, for the current settings (#109).
     *
     * Everything the caregiver's own choices already drive -- tile frames,
     * category hues -- is painted by the adapters from the same palette. What is
     * left is the surface the layout hard-codes to `@color/...`: the keyboard's
     * own background, its rules and the keys.
     *
     * Done here rather than by swapping a theme because an IME's input view is
     * created once and reused across every app the user types in. A theme
     * overlay needs the hierarchy rebuilt, which could mean the setting the user
     * just changed does not take effect until they unlock the phone somewhere
     * else -- and the user changing this one is the least able to tolerate that.
     */
    private fun applyPalette() {
        if (!::normalView.isInitialized) return
        val skin = KeyboardPalette.of(normalView.context, settings.highContrast)
        palette = skin

        normalView.setBackgroundColor(skin.paper)
        normalView.findViewById<View>(R.id.sentence_bar)?.setBackgroundColor(skin.paper)
        normalView.findViewById<TextView>(R.id.empty_hint)?.setTextColor(skin.inkSoft)

        // The two hairlines. `line` is decorative by design and becomes `ink` in
        // high contrast, which is the point: a divider nobody needs to see
        // becomes one that anybody can.
        listOf(R.id.list_boards, R.id.keyboard_body).forEach { id ->
            normalView.findViewById<View>(id)?.let { view ->
                if (view.background is android.graphics.drawable.ColorDrawable) {
                    view.setBackgroundColor(skin.paper)
                }
            }
        }

        ViewStyles.applyKeyColors(normalView, skin)
        message.repaint(skin)

        // The grid and spine are already bound; their rows must repaint with the
        // new stroke widths and label weights rather than waiting for a scroll.
        pictoAdapter.notifyDataSetChanged()
        categoryAdapter.notifyDataSetChanged()
        boardTabAdapter.notifyDataSetChanged()
    }

    /** Points [uiContext] at [language]. */
    private fun applyLanguage(language: String?) {
        uiContext = localizedFor(language)
    }

    /**
     * A new field, so a new phrase.
     *
     * The bar must never carry a sentence across an app boundary: what was said
     * in one conversation appearing above the next one is a privacy failure as
     * much as a correctness one. `restarting` is the framework's own word for
     * "same editor, same session", so only its absence clears — otherwise the
     * phrase would vanish every time the host app rebuilt its field underneath
     * the user mid-sentence.
     */
    override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        super.onStartInput(info, restarting)
        if (!restarting) {
            phrase.clear()
            // A generation for the field the user just left must never land in
            // the one they just entered (#46).
            beautify.onTargetChanged()
            // A word selected in the previous field is not a word in this one,
            // and the caret this field starts at is the only position the
            // arrows can trust until the framework reports another.
            phrase.navigator.onStartInput(info)
            // A countdown started while writing a message must not follow the
            // user into the next app (#144).
            assistance.cancel()
        }
    }

    /**
     * The caret moved, wherever it came from.
     *
     * The arrows set the selection themselves and hear their own change back
     * through here, which is harmless; what matters is the other case. A user
     * who taps into their own text has moved off the highlighted word, and a
     * highlight left behind would point at a word they are no longer on — and
     * backspace would then delete it.
     */
    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        phrase.navigator.onSelectionChanged(newSelStart, newSelEnd)
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // The one place the keyboard can notice a language change. Below API 33
        // appcompat only applies the per-app locale to AppCompatActivity, so no
        // configuration change reaches this service and nothing else would ever
        // tell it. Rebuilding the whole input view is heavy-handed, but the key
        // captions are inflated from a layout and there is no lighter way to
        // re-resolve them -- and it happens only on a change the user just made.
        //
        // Guarded on there already being a view: onStartInputView can run with no
        // onCreateInputView before it -- the two are gated on different
        // conditions, which is #27's whole failure mode -- and building one here
        // would put a keyboard on screen that the framework deliberately did not
        // ask for.
        val language = currentAppLanguage()
        if (language != viewLanguage && ::normalView.isInitialized) {
            applyLanguage(language)
            setInputView(onCreateInputView())
        }
        // Re-read settings so config changes apply next time the keyboard opens.
        scope.launch {
            settings = locator.settings.current()
            // `board` is not re-read here: observeBoards keeps it current, and a
            // one-shot read racing that collector is how the grid ends up drawn
            // for one board and washed in another's colour.
            tts.setParams(settings.ttsRate, settings.ttsPitch)
            // Binds or releases the model process, and shows or hides the key.
            // Done on every open so switching the setting off in the app takes
            // effect the next time the keyboard appears, without the caregiver
            // having to restart anything.
            beautify.setEnabled(settings.sentenceHelp)
            // Debug builds only, and inert everywhere else: the model process
            // puts it through ValidatorBypass.allowed before it means anything
            // (#167).
            beautify.unvalidated = settings.sentenceUnvalidated
            renderBeautifyKey()
            // Same reason: the caregiver may have set, changed or removed the
            // number since the keyboard was last on screen, and the bell has to
            // appear or disappear without anything being restarted.
            assistance.setContact(settings.assistanceName, settings.assistanceNumber)
            renderAssistanceKey()
            applyPalette()
            // onCreateInputView is gated on onEvaluateInputViewShown() and this
            // is gated on mShowInputRequested -- different conditions, so with a
            // hardware keyboard attached you can reach onStartInputView with no
            // onCreateInputView in between and no pictoGrid to touch.
            if (::pictoGrid.isInitialized) {
                (pictoGrid.layoutManager as? GridLayoutManager)?.spanCount = boardColumns
            }
            // Height follows the same settings read, so the slider takes effect
            // the next time the keyboard opens rather than only after a restart.
            applyBodyHeight()
            blind.resume(settings.blindMode)
            applyMode()
            refreshPictos()
        }
    }

    /**
     * Sizes the picto body from the board's `rows`, which nothing read before
     * this.
     *
     * The slider persisted a value and the body took 280dp from a dimension
     * resource regardless, so dragging it from 4 to 8 changed nothing. The
     * value moved from global settings onto the board in #31; what it drives
     * here is unchanged.
     *
     * The chrome above and below the board is subtracted from the *ceiling*,
     * never from the grid: the keyboard grows to carry the tab strip and the
     * phrase keys, and only the 60% cap stops it. Taking the space out of the
     * board instead would spend the product on its own furniture.
     */
    private fun applyBodyHeight() {
        if (!::keyboardBody.isInitialized) return
        val metrics = resources.displayMetrics
        keyboardBody.updateLayoutParams {
            height = KeyboardMetrics.bodyHeightPx(
                screen = KeyboardMetrics.Screen(metrics.widthPixels, metrics.heightPixels),
                categoryStripPx = resources.getDimensionPixelSize(R.dimen.kb_category_width),
                chromePx = chromeHeightPx(),
                grid = KeyboardMetrics.Grid(
                    columns = boardColumns,
                    rows = boardRows,
                    // Captions live inside the row, so turning them on costs
                    // height rather than picture -- and "visible rows" only
                    // means what it says if the count includes them.
                    captionPx = if (boardShowLabels) {
                        resources.getDimensionPixelSize(R.dimen.kb_caption_height)
                    } else {
                        0
                    },
                ),
            )
        }
    }

    /**
     * Everything stacked above and below the board, in pixels.
     *
     * Read from the same dimensions the layout is built from rather than
     * hardcoded, so a change to the key row's height cannot silently cost
     * the grid a row.
     *
     * [navigationBarInsetPx] is in here for the same reason the others are: it
     * is height the keyboard occupies and the board does not get.
     */
    private fun chromeHeightPx(): Int {
        val tabs = if (showBoardTabs) resources.getDimensionPixelSize(R.dimen.kb_tab_height) else 0
        return tabs +
            resources.getDimensionPixelSize(R.dimen.kb_sentence_bar_height) +
            resources.getDimensionPixelSize(R.dimen.kb_action_row_height) +
            resources.getDimensionPixelSize(R.dimen.kb_hairline) +
            navigationBarInsetPx
    }

    /** Shows the keyboard for the active mode and hides the other. */
    private fun applyMode() {
        if (!::blindView.isInitialized) return
        normalView.visibility = if (blind.enabled) View.GONE else View.VISIBLE
        blindView.visibility = if (blind.enabled) View.VISIBLE else View.GONE
    }

    private fun observeCategories() {
        locator.pictoRepository.observeActiveBoardCategories()
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

    /**
     * The boards offered as tabs, and which one is in use.
     *
     * Two queries combined rather than one: the active board is already observed
     * for its layout, and the strip needs to repaint whenever *either* the list
     * or the selection changes.
     */
    private fun observeBoards() {
        combine(
            locator.pictoRepository.observeKeyboardBoards(),
            locator.pictoRepository.observeActiveBoard(),
        ) { visible, active -> visible to active }
            // Same reason as the category strip: each board's picto costs a
            // filesystem stat, which does not belong on the typing thread.
            .map { (visible, active) ->
                Triple(visible, active, visible.associate { it.id to it.keyboardIconModel() })
            }
            .flowOn(Dispatchers.IO)
            .onEach { (visible, active, icons) ->
                boards = visible
                boardIcons = icons
                board = active
                applyBoardTabs()
                // A board switch changes the grid's shape as well as its
                // contents. Guarded because this collection starts in onCreate,
                // which precedes the first onCreateInputView -- and with a
                // hardware keyboard attached may precede it indefinitely.
                if (::pictoGrid.isInitialized) {
                    (pictoGrid.layoutManager as? GridLayoutManager)?.spanCount = boardColumns
                }
                applyBodyHeight()
            }
            .launchIn(scope)
    }

    /** Draws the strip, or removes it when there is no choice to make. */
    private fun applyBoardTabs() {
        if (!::boardStrip.isInitialized) return
        boardStrip.visibility = if (showBoardTabs) View.VISIBLE else View.GONE
        if (showBoardTabs) boardTabAdapter.submit(boards, board?.id, boardIcons)
    }

    /**
     * Switching board is navigation, not configuration.
     *
     * It writes the active flag and nothing else: the category collection
     * follows the active board, so the grid, the spine and the wash all change
     * from that one write rather than from four coordinated ones.
     */
    private fun onBoardSelected(selected: BoardEntity) {
        if (selected.id == board?.id) return
        scope.launch { locator.pictoRepository.setActiveBoard(selected.id) }
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
                style = PictoAdapter.Style(categoryColor = 0, showLabels = boardShowLabels),
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
            showLabels = boardShowLabels,
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

    /**
     * A tapped pictogram goes into the field, and into the bar that mirrors it.
     *
     * While the arrows are in use it goes *where they are* rather than at the
     * end — after the highlighted word, or into the hole a delete just left,
     * which is the repair the arrows exist to make possible (#143). Reach the
     * wrong word, remove it, tap the right one, and the phrase reads correctly
     * without the words after it having been retyped.
     *
     * That is why Beautify is told two different things. Appended text extends
     * the range it tracks; a word placed in the middle destroys it, because its
     * whole method is knowing the literal characters at the *end* of the field.
     */
    private fun onPictoTapped(picto: PictoEntity) {
        val text = picto.spokenText.ifBlank { picto.label }
        if (text.isBlank()) return
        // Checked before the word joins the phrase, not after: if the field has
        // moved on -- a message sent, the box emptied by its own app -- this word
        // begins a new phrase rather than extending one that is no longer there
        // (#159).
        forgetPhraseIfFieldMovedOn()
        val committed = phrase.add(text, picto.language, settings.addSpaceAfter)
        if (committed != null) beautify.onCommitted(committed) else beautify.onPhraseCleared()
        if (settings.speakOnTap) tts.speak(text, picto.language)
        recordUsage(picto)
    }

    // --- Beautify (#46) -----------------------------------------------------

    /** The phrase as the model needs it: each word still carrying its own language. */
    private fun typedWords(): List<TypedWord> =
        phrase.sentence.parts().map { TypedWord(it.text, it.language) }

    /**
     * Says what happened, on screen and to TalkBack.
     *
     * Carries more weight since #148 took the phrase row: several keys now
     * change nothing the user can see — ✕ forgets a phrase that was never drawn,
     * a rephrase rewrites text the user may not be able to read back — and this
     * is the only thing that says so.
     *
     * [long] is for the bell alone, whose message has to outlast a four-second
     * countdown rather than a moment's confirmation.
     */
    private fun announce(resId: Int, vararg args: Any, long: Boolean = false) {
        if (!::normalView.isInitialized) return
        val text = uiContext.getString(resId, *args)
        normalView.announceForAccessibility(text)
        message.show(text, long)
    }

    /** Hidden when there is no model, and Undo once a rephrase is in place. */
    private fun renderBeautifyKey() {
        if (!::normalView.isInitialized) return
        val key = normalView.findViewById<Button>(R.id.key_beautify) ?: return

        val available = settings.sentenceHelp && beautify.isAvailable
        key.visibility = if (available) View.VISIBLE else View.GONE
        key.isEnabled = !beautify.working
        val label = when {
            beautify.working -> R.string.kb_beautify_working
            beautify.edit.canUndo -> R.string.kb_beautify_undo
            else -> R.string.kb_beautify
        }
        key.contentDescription = uiContext.getString(label)
        // Three states, three glyphs. The key used to keep its ✨ while it
        // worked and say so only in its content description, which left a
        // disabled tint as the entire visible answer to "did that do anything?"
        // -- for 1.7 s warm, and 3.9 s on the cold start that #157 established
        // is the common case (#166).
        key.text = uiContext.getString(
            when {
                beautify.working -> R.string.kb_beautify_working_icon
                beautify.edit.canUndo -> R.string.kb_beautify_undo_icon
                else -> R.string.kb_beautify_icon
            },
        )
    }

    // --- Calling for help (#144) --------------------------------------------

    /** Present only when there is a number to ring, and Stop while it counts. */
    private fun renderAssistanceKey() {
        if (!::normalView.isInitialized) return
        val key = normalView.findViewById<View>(R.id.key_assistance) ?: return
        key.visibility = if (assistance.isConfigured) View.VISIBLE else View.GONE
        key.contentDescription = uiContext.getString(
            if (assistance.pending) R.string.kb_assistance_stop else R.string.kb_assistance,
        )
        // The pressed face already answers "did my tap land?"; this answers the
        // longer question of whether a call is on its way.
        key.isActivated = assistance.pending
    }

    /**
     * Says what the bell is doing, on three channels at once.
     *
     * It used to take the phrase row for the whole four seconds; #148 took that
     * row away, and "a call is starting and you can still stop it" is the one
     * message on this keyboard where missing it costs somebody else's afternoon.
     * So it is carried by all of:
     *
     * - a **long** toast, which lasts about three and a half of the four seconds
     * - the **bell itself**, which stays in its armed face until the countdown
     *   ends however it ends — that one is the only cue that covers the whole
     *   window, and the only one still there if the toast is missed
     * - the name **spoken aloud**, for the user who cannot read either
     */
    private fun onCallEvent(event: CallEvent) {
        when (event) {
            is CallEvent.Starting -> {
                announce(R.string.kb_assistance_calling, event.name, long = true)
                tts.speak(
                    uiContext.getString(R.string.kb_assistance_calling_spoken, event.name),
                    settings.defaultLanguage,
                )
            }

            CallEvent.Stopped -> announce(R.string.kb_assistance_stopped)
            CallEvent.Failed -> announce(R.string.kb_assistance_failed)
        }
    }

    // --- The phrase ---------------------------------------------------------

    /**
     * Reads the phrase back, after checking the field still holds it (#159).
     *
     * The check is here rather than inside [PhraseController] because only the
     * service can see both halves. Immediately after a rephrase the field holds
     * the model's sentence while the record holds the words the user tapped —
     * they disagree deliberately, and undo depends on the record surviving — so
     * a blind check would throw away exactly the state that makes undo work.
     */
    private fun speakPhrase() {
        forgetPhraseIfFieldMovedOn()
        phrase.speakAll()
    }

    /**
     * Drops a phrase the field no longer holds, unless a rephrase is applied.
     *
     * Both halves go together, exactly as ✕ used to take them: the record that
     * 🔊 reads, and the literal characters Beautify tracks. Forgetting only the
     * first leaves Beautify comparing a string from the abandoned phrase against
     * the field, which refuses every rephrase from then on.
     */
    private fun forgetPhraseIfFieldMovedOn() {
        if (beautify.edit.canUndo) return
        if (phrase.forgetIfFieldMovedOn()) beautify.onPhraseCleared()
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
            // Through the same channel, and for the same reason: a Toast here
            // was dropped too, so "this field can't receive images" was a
            // long-press that appeared to do nothing at all (#149).
            imageSharer.send(picto, frameColor, attribution, ::currentTarget, ::announce)
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
            // An arbitrary span just left the field, and the bar has no way to
            // know how much of its phrase went with it. Emptying it is the
            // honest answer: a mirror that has lost track must say so rather
            // than keep showing a phrase the field no longer holds.
            phrase.clear()
            beautify.onPhraseCleared()
            return
        }
        val before = ic.getTextBeforeCursor(WORD_LOOKBACK, 0) ?: return
        val count = trailingWordLength(before)
        if (count > 0) {
            ic.deleteSurroundingText(count, 0)
            phrase.dropLast()
            beautify.onDeleted(count)
        }
    }

    /**
     * Backspace, which has two meanings and picks between them by whether a word
     * is selected.
     *
     * Deleting the highlighted word rather than the last one is the whole point
     * of the arrows; without it they would only be a way to listen. Beautify's
     * range goes either way, because a word removed from the middle leaves the
     * end of the field holding something it never recorded putting there.
     */
    private fun backspace() {
        if (phrase.removeSelected()) beautify.onPhraseCleared() else deleteLastWord()
    }

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

    // The keyboard no longer opens the caregiver app. The ⚙ key that did it sat
    // permanently under the thumb of the person least able to find their way
    // back out of a full settings app opened mid-conversation, and there was no
    // route back to what they were saying (#16, #36). Configuration is the app's
    // job; the keyboard's job is to talk.

    /**
     * Writes a pictogram straight to the field, with no phrase record involved.
     *
     * The eyes-free keyboard's way in. It has no 🔊 and no ✨ to feed, so there
     * is nothing for a record to be for — and a phrase accumulating behind a
     * keyboard nobody can see is exactly the buffer this app refuses to have.
     */
    private fun writeStraightToField(picto: PictoEntity) {
        val text = picto.spokenText.ifBlank { picto.label }
        if (text.isBlank()) return
        commit(if (settings.addSpaceAfter) "$text " else text)
        tts.speak(text, picto.language)
        recordUsage(picto)
    }

    override fun onDestroy() {
        scope.cancel()
        // A countdown outliving the keyboard would place a call from a service
        // the user has already dismissed.
        assistance.release()
        message.release()
        // Released explicitly: an unbound service is what lets Android reclaim
        // the model's process, and leaking the binding would keep several
        // hundred megabytes alive after the keyboard is gone.
        beautify.release()
        tts.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "PictoKeyboard"
        private const val WORD_LOOKBACK = 128

        /**
         * The keys that may be pressed while a word is selected (#143).
         *
         * Everything else collapses the selection first, because a selection
         * plus a key that commits text is a replacement rather than an
         * insertion. The arrows move it; backspace is the reason it exists.
         */
        private val KEEPS_SELECTION = setOf(
            R.id.key_prev_word,
            R.id.key_next_word,
            R.id.key_backspace,
        )

        /**
         * The keys that leave a rephrase undoable (#173).
         *
         * These are the ones that only *read* the phrase. 🔊 speaks it back and
         * the arrows walk it word by word saying each one (#143) — which is how
         * somebody who cannot read the field checks what the model did. Closing
         * the undo window there would mean the act of checking destroys the
         * thing being checked. The bell is not about the sentence at all: it
         * calls for help (#144), and losing your own words because you rang for
         * somebody is not a trade anybody would choose.
         *
         * The asymmetry is what settles it. Undoing by accident costs one
         * rephrase — press ✨ again and the model has another go. Losing the
         * undo costs the words the user tapped, and nothing brings those back.
         *
         * Everything absent from this set still ends the window: the send, the
         * switch to the letter keyboard, and — through [BeautifyEdit.plus] and
         * [BeautifyEdit.minus] rather than through here — space, backspace and
         * every picto tap.
         */
        private val KEEPS_UNDO = setOf(
            R.id.key_beautify,
            R.id.key_speak,
            R.id.key_assistance,
            R.id.key_prev_word,
            R.id.key_next_word,
        )

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
