package org.pictokeyboard.ime

import android.view.HapticFeedbackConstants
import android.view.View

/**
 * The touch half of "did my tap land?".
 *
 * The visual half lives in [ViewStyles] and in `drawable/bg_key_*.xml`. This is
 * the channel that does not depend on vision or literacy, which on this keyboard
 * is not a detail: the person holding it often cannot read the text field to
 * check, and the pictogram they just pressed is frequently the only word they
 * have for what they want.
 *
 * Two settings gate it and both are honoured, deliberately:
 *
 *  - **The system haptic setting**, by calling [View.performHapticFeedback]
 *    without `FLAG_IGNORE_GLOBAL_SETTING`. Someone who has turned haptics off
 *    device-wide has usually done so for a reason — a tremor that a buzz makes
 *    worse, or a phone whose motor is loud enough to be conspicuous in a
 *    classroom.
 *  - **[org.pictokeyboard.data.prefs.Settings.hapticFeedback]**, so a caregiver
 *    can turn it off for this keyboard alone without touching the phone's own
 *    setting, which may be the only feedback its owner gets elsewhere.
 */
object PressFeedback {

    /**
     * Confirm a keypress on [this], if [enabled].
     *
     * [HapticFeedbackConstants.KEYBOARD_TAP] rather than a raw vibration: it is
     * the constant the platform maps to whatever a keypress should feel like on
     * this device, it is shorter and quieter than a generic buzz, and it is
     * exempt from the vibrate permission.
     *
     * [enabled] is read at the moment of the tap rather than captured when the
     * view was built, so turning the setting off takes effect on the next key —
     * not the next time the keyboard is recreated, which for an IME may not
     * happen until the phone is unlocked in another app.
     */
    fun View.confirmPress(enabled: Boolean) {
        if (enabled) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }
}
