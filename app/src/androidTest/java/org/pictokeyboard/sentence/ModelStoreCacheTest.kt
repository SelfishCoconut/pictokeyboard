package org.pictokeyboard.sentence

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The weight cache has somewhere to live, and goes when the model goes (#155).
 *
 * The bug this pins: `LiteRtEngine` handed the runtime a `cache` directory path
 * that nothing ever created, so XNNPACK could neither read nor write the
 * repacked weights and every cold start of `:llm` paid the full repacking cost
 * again — measured at six seconds before the first sentence.
 *
 * It survived to a real-weights run because **it is silent from Kotlin's side**.
 * LiteRT logs the failure to logcat and loads the model anyway, so nothing threw,
 * nothing returned false, and the only symptom was being slow. Hence a test that
 * asserts on the directory rather than on any exception.
 */
@RunWith(AndroidJUnit4::class)
class ModelStoreCacheTest {

    private lateinit var store: ModelStore
    private lateinit var files: File

    /**
     * A files directory of its own, **not** the real one.
     *
     * `ModelStore(targetContext)` would point at the installed app's own storage,
     * and this test deletes what it finds — so running the suite on a phone that
     * had the feature turned on would throw away somebody's 347 MB download.
     */
    private class TempFiles(base: Context, private val files: File) : ContextWrapper(base) {
        override fun getFilesDir(): File = files
    }

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        files = File(context.cacheDir, "model-store-${System.nanoTime()}").apply { mkdirs() }
        store = ModelStore(TempFiles(context, files))
    }

    @After
    fun tearDown() {
        files.deleteRecursively()
    }

    @Test
    fun theCacheDirectoryIsMadeBeforeTheEngineIsToldAboutIt() {
        assertFalse("nothing should exist before preparing", store.cacheDirectory.isDirectory)
        assertTrue(store.prepareCache())
        assertTrue("the engine is given this path and will not create it", store.cacheDirectory.isDirectory)
    }

    @Test
    fun preparingTwiceIsFine() {
        // The engine loads more than once over a process lifetime, and an
        // existing directory is success, not a collision.
        assertTrue(store.prepareCache())
        assertTrue(store.prepareCache())
    }

    @Test
    fun deletingTheModelTakesTheCacheWithIt() {
        store.prepareCache()
        val repacked = File(store.cacheDirectory, "weights.xnnpack_cache").apply { writeBytes(ByteArray(SIZE)) }

        store.delete()

        assertFalse(
            "somebody who pressed a button offering to free the space would still be short of it",
            repacked.exists(),
        )
        assertFalse(store.cacheDirectory.exists())
    }

    @Test
    fun theSpaceTheCacheUsesIsCountedAsTheFeaturesOwn() {
        store.prepareCache()
        File(store.cacheDirectory, "weights.xnnpack_cache").writeBytes(ByteArray(SIZE))

        // Settings offers to delete "the model (N MB)". The cache is part of
        // what that press gives back, so leaving it out understates the offer.
        assertEquals(SIZE.toLong(), store.bytesOnDisk())
    }

    private companion object {
        const val SIZE = 4096
    }
}
