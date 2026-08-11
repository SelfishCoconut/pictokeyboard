package org.pictokeyboard.sentence

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.core.content.getSystemService

/**
 * Whether this phone can run sentence help at all, and if not, which fact to
 * tell the person holding it.
 *
 * #44 and #48 both turn on this: a device that cannot hold the model must be
 * told so **before** it is offered a 347 MB download, not after. The three
 * reasons are kept apart because they are different sentences — "your phone is
 * not powerful enough" and "you need to free up some space" ask for different
 * things, and only one of them is worth acting on.
 */
sealed interface Capability {

    /** The model can be downloaded and run here. */
    data object Ready : Capability

    /** No native library for this processor. Nothing the owner can do. */
    data object UnsupportedProcessor : Capability

    /** Not enough memory to hold the model beside everything else. */
    data class NotEnoughMemory(val totalBytes: Long) : Capability

    /** Enough memory, but nowhere to put the file today. Recoverable. */
    data class NotEnoughStorage(val freeBytes: Long, val neededBytes: Long) : Capability

    val isReady: Boolean get() = this is Ready
}

/**
 * Reads the three facts that decide [Capability].
 *
 * Storage is checked last and separately from the rest, because it is the only
 * one that changes: a phone that is full today can be cleared tonight, while a
 * 32-bit processor is permanent. Keeping them in that order means the recoverable
 * answer is the one a user sees when it is the only thing wrong.
 */
class DeviceCapability(private val context: Context) {

    fun check(): Capability {
        val totalRam = totalRamBytes()
        // Headroom on top of the file itself: the download lands in a `.part`
        // beside its destination, and a filesystem with exactly 347 MB left has
        // nowhere to put the rename.
        val needed = ModelSpec.SIZE_BYTES * STORAGE_HEADROOM_PERCENT / PERCENT
        val free = context.filesDir.usableSpace

        // Storage is judged last, because it is the only one of the three that
        // changes: a phone that is full today can be cleared tonight, while a
        // 32-bit processor is permanent. Ordering them this way means the
        // recoverable answer is the one a user sees when it is all that is wrong.
        return when {
            Build.SUPPORTED_ABIS.none { it in ModelSpec.SUPPORTED_ABIS } ->
                Capability.UnsupportedProcessor

            totalRam in 1 until ModelSpec.MIN_TOTAL_RAM_BYTES ->
                Capability.NotEnoughMemory(totalRam)

            free < needed -> Capability.NotEnoughStorage(free, needed)
            else -> Capability.Ready
        }
    }

    /**
     * Total physical RAM, or 0 when the system will not say.
     *
     * Total rather than *available*: available memory is whatever the moment
     * happens to look like, and refusing the feature because something else was
     * busy when the caregiver opened Settings would be a coin toss. What is being
     * decided here is whether this phone is the right size for the job.
     */
    private fun totalRamBytes(): Long {
        val manager = context.getSystemService<ActivityManager>() ?: return 0
        return ActivityManager.MemoryInfo().also(manager::getMemoryInfo).totalMem
    }

    private companion object {
        const val STORAGE_HEADROOM_PERCENT = 110
        const val PERCENT = 100
    }
}
