package org.pictokeyboard.sentence

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.RandomAccessFile

/** How a download is going, for the progress a caregiver watches (#44, #48). */
sealed interface DownloadState {
    data object Idle : DownloadState
    data class Running(val bytes: Long, val total: Long) : DownloadState {
        val fraction: Float get() = if (total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else 0f
    }

    data object Verifying : DownloadState
    data object Done : DownloadState

    /** [canRetry] false means the bytes on disk were wrong and were thrown away. */
    data class Failed(val reason: Reason, val canRetry: Boolean = true) : DownloadState

    enum class Reason { NETWORK, DISK, CORRUPT }
}

/**
 * Fetches the weights, resumably, into [ModelStore.partial].
 *
 * **Resume is the whole design.** This is 347 MB onto a phone that may be on a
 * train, and a download that starts from zero every time it is interrupted never
 * finishes at all. Progress goes to a `.part` file and every attempt asks for
 * `Range: bytes=<what we already have>-`, so a cancelled or dropped transfer
 * costs only what was in flight.
 *
 * Cancellation is cooperative: collecting this in a scope and cancelling the
 * scope stops the transfer and leaves the partial file exactly where it is,
 * which is also what "resume later" needs.
 */
class ModelDownloader(private val store: ModelStore, private val client: OkHttpClient) {

    fun download(): Flow<DownloadState> = flow {
        if (store.isDownloaded()) {
            emit(DownloadState.Done)
            return@flow
        }
        store.prepareDirectory()

        val alreadyHave = store.partialBytes()
        // A partial longer than the file itself is a leftover from a different
        // revision. Starting again is cheaper than reasoning about it.
        val from = if (alreadyHave > ModelSpec.SIZE_BYTES) 0L else alreadyHave
        emit(DownloadState.Running(from, ModelSpec.SIZE_BYTES))

        val request = Request.Builder()
            .url(ModelSpec.URL)
            .apply { if (from > 0) header("Range", "bytes=$from-") }
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    emit(DownloadState.Failed(DownloadState.Reason.NETWORK))
                    return@flow
                }
                // A 200 to a ranged request means the server ignored the range
                // and is sending the whole file, so anything already on disk has
                // to be overwritten from the start rather than appended to.
                val resuming = response.code == HTTP_PARTIAL && from > 0
                val startAt = if (resuming) from else 0L
                val body = response.body ?: run {
                    emit(DownloadState.Failed(DownloadState.Reason.NETWORK))
                    return@flow
                }
                emitAll(this, body.byteStream(), startAt)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            // The partial file is deliberately left alone: it is what makes the
            // next attempt cheap, and a dropped connection is the ordinary case.
            emit(DownloadState.Failed(reasonFor(e)))
            return@flow
        }

        emit(DownloadState.Verifying)
        if (store.installIfVerified()) {
            emit(DownloadState.Done)
        } else {
            // installIfVerified has already deleted the bytes, because a file
            // whose digest is wrong is not a starting point for a resume.
            emit(DownloadState.Failed(DownloadState.Reason.CORRUPT, canRetry = true))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Streams the body into the partial file, reporting progress as it goes.
     *
     * `RandomAccessFile` with an explicit seek rather than an appending stream:
     * the offset written has to be the offset the server was asked for, and
     * "append to whatever is there" quietly does the wrong thing on the 200 path
     * above, where the server chose to resend from zero.
     */
    private suspend fun emitAll(
        collector: kotlinx.coroutines.flow.FlowCollector<DownloadState>,
        input: java.io.InputStream,
        startAt: Long,
    ) {
        RandomAccessFile(store.partial, "rw").use { out ->
            out.seek(startAt)
            if (startAt == 0L) out.setLength(0)
            val buffer = ByteArray(BUFFER)
            var written = startAt
            var lastReported = 0L
            while (currentCoroutineContext().isActive) {
                val read = input.read(buffer)
                if (read <= 0) break
                out.write(buffer, 0, read)
                written += read
                // Throttled: a progress bar cannot show 8 KB, and emitting per
                // chunk would put tens of thousands of recompositions between
                // here and a caregiver watching a number climb.
                if (written - lastReported >= REPORT_EVERY) {
                    lastReported = written
                    collector.emit(DownloadState.Running(written, ModelSpec.SIZE_BYTES))
                }
            }
        }
    }

    private fun reasonFor(e: IOException): DownloadState.Reason =
        if (e.message?.contains("space", ignoreCase = true) == true) {
            DownloadState.Reason.DISK
        } else {
            DownloadState.Reason.NETWORK
        }

    private companion object {
        const val HTTP_PARTIAL = 206
        const val BUFFER = 1 shl 16
        const val REPORT_EVERY = 1L shl 21
    }
}
