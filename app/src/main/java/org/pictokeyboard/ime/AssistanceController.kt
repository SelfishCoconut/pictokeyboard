package org.pictokeyboard.ime

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

/**
 * The bell that calls somebody (#144).
 *
 * This keyboard can say "I want water". It could not say "come here, something
 * is wrong" to a person who is not in the room, and reaching them meant finding
 * the phone app, finding the contact and dialling — a chain of general-purpose
 * interface that the person holding this keyboard may not be able to work
 * through, least of all in the moment they need it.
 *
 * **A press starts a countdown, not a call.** A misfire on an AAC device is not
 * rare, and a caregiver who gets four accidental calls a day turns the feature
 * off, which is the same as not having it. So the keyboard says out loud who it
 * is about to ring and gives the same key back as the way to stop it — the
 * press-then-undo shape the Beautify key already uses, rather than a dialog
 * asking a question of somebody who may not read.
 *
 * The wait is deliberately short. This is a call for help, and every second of
 * confirmation is charged to the person who needed it.
 */
class AssistanceController(
    private val context: Context,
    private val onEvent: (CallEvent) -> Unit,
    private val onStateChanged: () -> Unit,
) {

    /** Who the bell rings, or null when the caregiver has not set one. */
    private var contact: Contact? = null

    private val handler = Handler(Looper.getMainLooper())

    /** True while the countdown is running and a second press would stop it. */
    var pending = false
        private set

    val isConfigured: Boolean get() = contact != null

    /**
     * Takes the contact from settings, which are re-read every time the keyboard
     * opens.
     *
     * A change cancels anything in flight: a countdown started for one number
     * must never ring a different one because the caregiver was editing the
     * field on another screen at the time.
     */
    fun setContact(name: String, number: String) {
        val next = if (number.isBlank()) null else Contact(name.ifBlank { number }, number)
        if (next != contact) cancel()
        contact = next
    }

    /** One press: start the countdown, or stop the one already running. */
    fun press() {
        val target = contact ?: return
        if (pending) {
            cancel()
            onEvent(CallEvent.Stopped)
        } else {
            pending = true
            handler.postDelayed(::place, COUNTDOWN_MS)
            onStateChanged()
            onEvent(CallEvent.Starting(target.name))
        }
    }

    /** Stops a countdown without saying anything, for a new field or a new key. */
    fun cancel() {
        if (!pending) return
        pending = false
        handler.removeCallbacksAndMessages(null)
        onStateChanged()
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        pending = false
    }

    /**
     * Rings, one of two ways.
     *
     * With `CALL_PHONE` granted the call is placed outright, which is the point:
     * the user pressed a button that means "call for help", and handing them a
     * dialler still asking to be pressed is handing back the problem. Without it
     * the dialler opens with the number already in — worth having, because
     * somebody else in the room can finish it, and far better than a bell that
     * does nothing while the permission is refused.
     *
     * Nothing here throws. An `ActivityNotFoundException` on a device with no
     * dialler at all, or a `SecurityException` from a permission revoked between
     * the check and the call, has to end as a message rather than as a keyboard
     * that dies in whatever app the user was talking to.
     */
    private fun place() {
        pending = false
        onStateChanged()
        val target = contact ?: return
        val direct = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED
        val intent = Intent(
            if (direct) Intent.ACTION_CALL else Intent.ACTION_DIAL,
            "tel:${Uri.encode(target.number)}".toUri(),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val started = runCatching { context.startActivity(intent) }.isSuccess
        if (!started) onEvent(CallEvent.Failed)
    }

    private data class Contact(val name: String, val number: String)

    private companion object {
        /**
         * Long enough to hear "calling Ana" and press again, short enough that
         * somebody who meant it is not left waiting. Spoken feedback is kept to
         * the name alone so it finishes inside this window; the full instruction
         * goes to the screen and to TalkBack, which do not take four seconds.
         */
        const val COUNTDOWN_MS = 4_000L
    }
}

/** What the bell did, in words the keyboard has to say out loud. */
sealed interface CallEvent {

    /** The countdown has started, and a second press stops it. */
    data class Starting(val name: String) : CallEvent

    data object Stopped : CallEvent

    /** Nothing on this device could take the call. */
    data object Failed : CallEvent
}
