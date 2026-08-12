package org.pictokeyboard.sentence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which sentence a given phone is told about sentence help.
 *
 * The reading of the three numbers needs a device; the judgement made from them
 * does not, and the judgement is the part that decides whether somebody gets the
 * feature. #171 was exactly a wrong judgement — "this phone cannot", said to a
 * phone that could — and nothing here could have caught it, because there was
 * nothing here.
 */
class DeviceCapabilityTest {

    private companion object {
        const val GB = 1024L * 1024 * 1024
        const val ROOM = ModelSpec.SIZE_BYTES * 2
        val ARM64 = listOf("arm64-v8a")
    }

    private fun judge(
        abis: List<String> = ARM64,
        ram: Long = 8 * GB,
        free: Long = ROOM,
    ) = DeviceCapability.judge(abis = abis, totalRamBytes = ram, freeBytes = free)

    @Test
    fun `a phone with room and a supported processor is simply ready`() {
        assertEquals(Capability.Ready, judge())
    }

    @Test
    fun `a 32-bit processor is the one answer nobody can act on`() {
        assertEquals(Capability.UnsupportedProcessor, judge(abis = listOf("armeabi-v7a", "armeabi")))
    }

    @Test
    fun `a full phone is refused, because the file genuinely does not fit`() {
        val verdict = judge(free = ModelSpec.SIZE_BYTES / 2)
        assertTrue("$verdict", verdict is Capability.NotEnoughStorage)
        assertFalse(verdict.isReady)
    }

    /** The headroom matters: the download lands in a `.part` beside its target. */
    @Test
    fun `exactly the size of the file is not enough room for it`() {
        assertTrue(judge(free = ModelSpec.SIZE_BYTES) is Capability.NotEnoughStorage)
    }

    // --- #171: memory warns, and a warning is still a yes --------------------

    @Test
    fun `a phone under the floor is warned and still offered the feature`() {
        val verdict = judge(ram = 2 * GB + GB / 2)
        assertEquals(Capability.TightMemory(2 * GB + GB / 2), verdict)
        assertTrue("a tight phone still gets sentence help", verdict.isReady)
    }

    @Test
    fun `the floor itself is comfortable`() {
        assertEquals(Capability.Ready, judge(ram = ModelSpec.TIGHT_TOTAL_RAM_BYTES))
    }

    /**
     * A system that will not say how much memory it has is not a system with
     * none. Warning on a missing number would put the sentence in front of every
     * phone whose `ActivityManager` was unavailable, which teaches people to
     * ignore it.
     */
    @Test
    fun `an unreadable memory figure is not a complaint`() {
        assertEquals(Capability.Ready, judge(ram = 0))
    }

    /**
     * Order, and it is the whole reason the `when` is written the way it is: a
     * phone that is both full and tight has one thing worth telling it, and the
     * warning it can overrule must not stand in front of the refusal it cannot.
     */
    @Test
    fun `a refusal outranks a warning when both are true`() {
        assertTrue(judge(ram = 2 * GB, free = 0) is Capability.NotEnoughStorage)
        assertEquals(Capability.UnsupportedProcessor, judge(abis = listOf("x86"), ram = 2 * GB, free = 0))
    }
}
