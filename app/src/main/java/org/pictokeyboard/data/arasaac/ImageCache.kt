package org.pictokeyboard.data.arasaac

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.UUID

/**
 * Downloads ARASAAC pictogram images (plain or customized) and copies
 * user-imported images into local files, so the keyboard keeps working fully
 * offline once a board has been set up.
 */
class ImageCache(context: Context, private val client: OkHttpClient) {
    private val appContext = context.applicationContext
    private val dir: File = File(appContext.filesDir, "pictos").apply { mkdirs() }

    fun fileForArasaac(id: Int): File = File(dir, "arasaac_$id.png")

    fun fileForCustom(id: String): File = File(dir, "custom_$id.png")

    /**
     * Where a photograph out of a `.pkb` lands.
     *
     * [digest] is content-addressed and validated by the archive before it gets
     * here, so it cannot contain a separator and this cannot be made to write
     * outside [dir]. Importing the same photo twice writes the same file, which
     * is the deduplication falling out of the naming rather than being arranged.
     */
    fun fileForImported(digest: String): File = File(dir, "pkb_$digest.png")

    /** A staging area for an import, emptied before each one and after it. */
    fun importStagingDir(): File = File(dir, "pkb-staging").apply { mkdirs() }

    /**
     * Downloads ARASAAC pictogram [id] (optionally [options]-customized) into the
     * cache and returns the absolute path, or null on failure. Each distinct
     * customization is cached under its own file. No-op if already present.
     */
    suspend fun downloadArasaac(id: Int, options: ArasaacOptions = ArasaacOptions()): String? =
        withContext(Dispatchers.IO) {
            val target = if (options.isCustomized) {
                File(dir, "arasaac_${id}_${options.cacheKey()}.png")
            } else {
                fileForArasaac(id)
            }
            if (target.exists() && target.length() > 0) return@withContext target.absolutePath
            val url = ArasaacUrls.customizedOrPlain(id, options)
            runCatching {
                client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext null
                    val body = resp.body ?: return@withContext null
                    target.outputStream().use { out -> body.byteStream().copyTo(out) }
                }
                target.absolutePath
            }.getOrNull()
        }

    /** Copies a user-picked image ([uri]) into the cache and returns its path. */
    suspend fun importFromUri(uri: Uri): String? = withContext(Dispatchers.IO) {
        val target = fileForCustom(UUID.randomUUID().toString())
        runCatching {
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { out -> input.copyTo(out) }
            } ?: return@withContext null
            target.absolutePath
        }.getOrNull()
    }

    /**
     * Decodes [uri] into a bitmap downsampled so its longest edge is at most
     * [maxDim] px (keeps cropping responsive and avoids OOM on large photos).
     */
    suspend fun loadDownsampled(uri: Uri, maxDim: Int = 1600): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            appContext.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            val longest = maxOf(bounds.outWidth, bounds.outHeight)
            var sample = 1
            while (longest / sample > maxDim) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            appContext.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
        }.getOrNull()
    }

    /** Saves [bitmap] as a PNG in the cache and returns its path (used by the cropper). */
    suspend fun saveBitmap(bitmap: Bitmap): String? = withContext(Dispatchers.IO) {
        val target = fileForCustom(UUID.randomUUID().toString())
        runCatching {
            target.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
            target.absolutePath
        }.getOrNull()
    }
}
