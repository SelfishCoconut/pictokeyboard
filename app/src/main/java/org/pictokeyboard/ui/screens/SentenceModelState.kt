package org.pictokeyboard.ui.screens

import org.pictokeyboard.sentence.Capability
import org.pictokeyboard.sentence.DownloadState

/**
 * Everything Settings needs to know about the model on this phone (#48).
 *
 * [capability] is read once and separately from [download], because it answers a
 * different question: whether this phone should ever be offered the feature at
 * all, rather than how far a transfer has got.
 */
data class SentenceModelState(
    val capability: Capability = Capability.Ready,
    val installed: Boolean = false,
    val download: DownloadState = DownloadState.Idle,
    val bytesOnDisk: Long = 0,
)
