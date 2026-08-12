package org.pictokeyboard.sentence

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.core.content.getSystemService

/**
 * Whether this phone can run sentence help, and which fact to tell the person
 * holding it.
 *
 * #44 and #48 both turn on this: a device that cannot hold the model must be
 * told so **before** it is offered a 347 MB download, not after. The reasons are
 * kept apart because they are different sentences — "your phone's processor
 * cannot do this" and "you need to free up some space" ask for different things,
 * and only one of them is worth acting on.
 *
 * **A refusal has to be a fact, not a guess** (#171). Two of these are checked
 * facts: there is no 32-bit ARM build of the runtime, and 347 MB does not go
 * into 100 MB of free space. Memory was neither — 3 GB was reasoned about rather
 * than measured, and then a 2.4 GB emulator ran the model repeatedly while
 * Settings told its owner the phone could not. So memory is now [TightMemory]:
 * still said out loud, still said before the download, but said as a warning the
 * caregiver may overrule rather than as a door that is shut.
 */
sealed interface Capability {

    /** The model can be downloaded and run here. */
    data object Ready : Capability

    /**
     * It will run, but there is not much room around it (#171).
     *
     * Ready as far as anything that gates on [isReady] is concerned. What this
     * adds is a sentence: below the comfortable floor the model may be slow, and
     * Android may reclaim `:llm` when other apps get busy. That is a real cost
     * and worth knowing before spending 347 MB of somebody's data allowance —
     * and it is also a cost a caregiver with no other way to build a sentence may
     * well decide is worth paying, which is the same judgement #145 made about
     * a phone that measures over the two-second budget.
     */
    data class TightMemory(val totalBytes: Long) : Capability

    /** No native library for this processor. Nothing the owner can do. */
    data object UnsupportedProcessor : Capability

    /** Nowhere to put the file today. Recoverable. */
    data class NotEnoughStorage(val freeBytes: Long, val neededBytes: Long) : Capability

    /** Whether the feature may be offered at all. A warning is still a yes. */
    val isReady: Boolean get() = this is Ready || this is TightMemory
}

/**
 * Reads the three facts that decide [Capability].
 *
 * The reading is here and the judgement is in [DeviceCapability.judge], which
 * takes the three numbers and no `Context`. `ActivityManager` and
 * `Build.SUPPORTED_ABIS` cannot be had in a unit test without a device, and the
 * part worth testing is which sentence a given phone gets — so that part is
 * pure, and this class is the thin bit that asks the system.
 */
class DeviceCapability(private val context: Context) {

    fun check(): Capability = judge(
        abis = Build.SUPPORTED_ABIS.orEmpty().toList(),
        totalRamBytes = totalRamBytes(),
        freeBytes = context.filesDir.usableSpace,
    )

    /**
     * Total physical RAM, or 0 when the system will not say.
     *
     * Total rather than *available*: available memory is whatever the moment
     * happens to look like, and judging the feature by what something else was
     * doing when the caregiver opened Settings would be a coin toss. What is
     * being decided here is whether this phone is the right size for the job.
     */
    private fun totalRamBytes(): Long {
        val manager = context.getSystemService<ActivityManager>() ?: return 0
        return ActivityManager.MemoryInfo().also(manager::getMemoryInfo).totalMem
    }

    companion object {
        private const val STORAGE_HEADROOM_PERCENT = 110
        private const val PERCENT = 100

        /**
         * The three facts, in the order they are worth hearing.
         *
         * Storage is judged after the processor because it is the only one that
         * changes: a phone that is full today can be cleared tonight, while a
         * 32-bit processor is permanent. Memory comes last because it is the
         * only one that is not a refusal — a phone that is merely tight still
         * gets the feature, so its sentence must not displace one that says no.
         *
         * @param totalRamBytes 0 when the system would not say, which is treated
         *   as no complaint rather than as the worst case. Refusing to warn is
         *   the right way round for a number that is missing rather than small.
         */
        fun judge(abis: List<String>, totalRamBytes: Long, freeBytes: Long): Capability {
            // Headroom on top of the file itself: the download lands in a
            // `.part` beside its destination, and a filesystem with exactly
            // 347 MB left has nowhere to put the rename.
            val needed = ModelSpec.SIZE_BYTES * STORAGE_HEADROOM_PERCENT / PERCENT
            return when {
                abis.none { it in ModelSpec.SUPPORTED_ABIS } -> Capability.UnsupportedProcessor
                freeBytes < needed -> Capability.NotEnoughStorage(freeBytes, needed)
                totalRamBytes in 1 until ModelSpec.TIGHT_TOTAL_RAM_BYTES ->
                    Capability.TightMemory(totalRamBytes)

                else -> Capability.Ready
            }
        }
    }
}
