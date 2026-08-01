package org.pictokeyboard.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo

/**
 * The class+variation pairs Android uses to mark a field as secret. Compared as
 * whole values rather than by variation alone because the variation is only
 * meaningful next to its class: 0x10 is a password under [InputType.TYPE_CLASS_NUMBER]
 * and a URI under [InputType.TYPE_CLASS_TEXT].
 */
private val PASSWORD_INPUT_TYPES = setOf(
    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
    InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD,
)

/**
 * Whether what is typed into this field may be remembered in the `usage` table.
 *
 * That table is not private scratch data: it becomes the "Suggested" category,
 * which is shown in the config app to the caregiver rather than to the person
 * who typed. A word recorded here can resurface on someone else's screen, so the
 * two signals Android gives an IME for "do not learn from this" have to be
 * honoured — [EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING], which is how an
 * incognito tab or a private chat asks, and the password input types.
 *
 * Returns false when there is no field. Nothing can have been typed in that
 * state, so the only cost is a suggestion that never gets made — whereas
 * guessing the other way costs a secret, permanently and invisibly.
 */
fun EditorInfo?.allowsUsageRecording(): Boolean {
    if (this == null) return false
    // Both masked rather than compared whole. imeOptions packs the action
    // (Send, Go, ...) into its low bits alongside the flags, and hosts OR flags
    // such as TYPE_TEXT_FLAG_NO_SUGGESTIONS on top of the input variation, so
    // an equality check against either bare constant misses real fields.
    val optedOut = (imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0
    val kind = inputType and (InputType.TYPE_MASK_CLASS or InputType.TYPE_MASK_VARIATION)
    return !optedOut && kind !in PASSWORD_INPUT_TYPES
}
