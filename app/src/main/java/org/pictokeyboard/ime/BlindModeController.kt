package org.pictokeyboard.ime

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.pictokeyboard.R
import org.pictokeyboard.data.db.CategoryEntity
import org.pictokeyboard.data.db.PictoEntity
import org.pictokeyboard.tts.TtsManager

/**
 * What the eyes-free keyboard needs from the keyboard it lives inside.
 *
 * Named as a group rather than passed as seven loose parameters, because the
 * list *is* the interesting thing: it is the whole of what the two keyboards
 * share, and it is short. Read as lambdas rather than as values because every
 * one of them changes under this class — the board is reloaded, the setting is
 * edited, the language is switched — between one swipe and the next.
 */
data class BlindHost(
    val categories: () -> List<CategoryEntity>,
    val language: () -> String,
    val strings: () -> Context,
    val pictosIn: suspend (String) -> List<PictoEntity>,
    val writePicto: (PictoEntity) -> Unit,
    val deleteWord: () -> Unit,
    val rememberEnabled: (Boolean) -> Unit,
)

/**
 * The eyes-free keyboard: a second keyboard, driven entirely by gestures.
 *
 * It shares the service's board data and its voice and nothing else — no grid,
 * no spine, no keys at all. Swipes change category and pictogram, a
 * tap says the current one, a double tap writes it, a long press takes the last
 * word back. The surface exists only so there is something to gesture on and
 * something to look at for a user with some sight left.
 *
 * Lifted out of `PictoKeyboardService` because it is genuinely a different
 * keyboard sharing a shell, and because the service had grown past the point
 * where the sighted keyboard's code could be read without stepping over it.
 *
 * **Everything here speaks unconditionally.** With no surface to draw on the
 * announcement is the entire interface, and suppressing it because a view was
 * not ready would be the one failure a blind user could not work around.
 */
class BlindModeController(
    private val host: BlindHost,
    private val tts: TtsManager,
    private val scope: CoroutineScope,
) {

    var enabled = false
        private set

    /** Whether a category has been loaded since the mode was last switched on. */
    private var loaded = false

    private var catIndex = 0
    private var pictoIndex = 0
    private var pictos: List<PictoEntity> = emptyList()

    /** Null before the first `onCreateInputView`, which audio does not wait for. */
    private var surface: BlindKeyboardView? = null

    fun attach(view: BlindKeyboardView) {
        surface = view.apply {
            onSwipeVertical = { down -> changeCategory(if (down) 1 else -1) }
            onSwipeHorizontal = { right -> changePicto(if (right) 1 else -1) }
            onSingleTap = { speakCurrent() }
            onDoubleTap = { writeCurrent() }
            onLongPress = { deleteWord() }
        }
    }

    /**
     * Adopts the stored setting when the keyboard opens.
     *
     * Silent, unlike [toggle]: the user did not just do anything, and announcing
     * the mode every time a text box is focused would be four seconds of speech
     * in front of every message.
     */
    fun resume(value: Boolean) {
        enabled = value
        if (enabled && !loaded) {
            catIndex = 0
            loaded = true
            loadCategory()
        }
    }

    /** The two-finger double tap. Persisted, and announced because it was asked for. */
    fun toggle() {
        enabled = !enabled
        host.rememberEnabled(enabled)
        if (enabled) {
            catIndex = 0
            loaded = true
            loadCategory(extra = host.strings().getString(R.string.blind_on))
        } else {
            loaded = false
            tts.speak(host.strings().getString(R.string.blind_off), host.language())
        }
    }

    fun changeCategory(direction: Int) {
        val categories = host.categories()
        if (categories.isEmpty()) {
            loadCategory()
            return
        }
        catIndex = (catIndex + direction).mod(categories.size)
        loadCategory()
    }

    fun changePicto(direction: Int) {
        if (pictos.isEmpty()) {
            speakCurrent()
            return
        }
        pictoIndex = (pictoIndex + direction).mod(pictos.size)
        speakCurrent()
    }

    /**
     * Updates the surface and speaks the current pictogram.
     *
     * [announcements] — a mode or category name — are spoken in the board's
     * language while the pictogram is spoken in its own, each as its own
     * utterance so the two voices do not bleed into one another.
     */
    fun speakCurrent(announcements: List<String> = emptyList()) {
        val category = host.categories().getOrNull(catIndex)
        surface?.setHint(category?.name.orEmpty())
        // The whole surface takes the category's hue, so the cue is unmissable
        // even to someone who can only make out large blocks of colour.
        surface?.setSurfaceColor(category?.colorArgb)
        val parts = announcements.map { TtsManager.Part(it, host.language()) }.toMutableList()
        val picto = pictos.getOrNull(pictoIndex)
        if (picto == null) {
            val message = host.strings().getString(R.string.blind_empty_category)
            surface?.setCaption(message)
            parts += TtsManager.Part(message, host.language())
        } else {
            val label = picto.spokenText.ifBlank { picto.label }
            surface?.setCaption(label)
            parts += TtsManager.Part(label, picto.language)
        }
        tts.speakSequence(parts)
    }

    fun writeCurrent() {
        val picto = pictos.getOrNull(pictoIndex) ?: return
        host.writePicto(picto)
    }

    private fun deleteWord() {
        host.deleteWord()
        tts.speak(host.strings().getString(R.string.blind_deleted), host.language())
    }

    /** Loads the current category's pictos and announces it and its first one. */
    private fun loadCategory(extra: String? = null) {
        val category = host.categories().getOrNull(catIndex)
        if (category == null) {
            val message = host.strings().getString(R.string.blind_no_board)
            surface?.apply {
                setCaption("")
                setSurfaceColor(null)
                setHint(message)
            }
            tts.speak(message, host.language())
            return
        }
        scope.launch {
            pictos = host.pictosIn(category.id)
            pictoIndex = 0
            speakCurrent(announcements = listOfNotNull(extra, category.name))
        }
    }
}
