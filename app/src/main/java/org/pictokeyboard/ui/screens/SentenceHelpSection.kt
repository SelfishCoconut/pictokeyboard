package org.pictokeyboard.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.pictokeyboard.R
import org.pictokeyboard.data.prefs.SentenceSpeed
import org.pictokeyboard.sentence.Capability
import org.pictokeyboard.sentence.DownloadState
import org.pictokeyboard.sentence.ModelSpec
import org.pictokeyboard.ui.theme.Spacing
import java.util.Locale

/**
 * **Sentence help**: on or off, the model, and where the data goes.
 *
 * Kept to what shipping an optional several-hundred-megabyte model actually
 * requires. No tone control, no length control, no intent row — those were
 * settings standing in for a decision nobody asked to make, and one good default
 * beats two dropdowns a caregiver has to reason about while a child waits (#48).
 */
@Composable
internal fun SentenceHelpSection(
    enabled: Boolean,
    model: SentenceModelState,
    speed: SentenceSpeed?,
    onEnabled: (Boolean) -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onBenchmark: () -> Unit,
) {
    val capability = model.capability
    if (capability !is Capability.Ready) {
        // Said instead of offering a download that would fail, which is the
        // whole point of checking before rather than after 347 MB.
        UnavailableHere(capability)
        return
    }

    SwitchRow(stringResource(R.string.settings_sentence_help), enabled, onEnabled)
    Text(
        stringResource(R.string.settings_sentence_help_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    // Said plainly, and where the switch is (#145). It is off by default
    // already; what was missing was the sentence admitting that this is new,
    // that it may be slow, and that switching it off costs nothing.
    Text(
        stringResource(R.string.settings_sentence_experimental),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    // The sentence a parent actually wants, and the reason the model is on the
    // device rather than on a server.
    Text(
        stringResource(R.string.settings_sentence_help_privacy),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    ModelRow(model = model, onDownload = onDownload, onCancel = onCancel, onDelete = onDelete)
    if (model.installed) SpeedRow(speed, model.benchmarking, onBenchmark)
}

/**
 * How fast it actually is here, and nothing else (#145).
 *
 * The number never disables anything. A phone over #44's two-second budget is
 * *told*, because a caregiver with no other way to build a sentence may well
 * decide four seconds is worth it, and that decision is not this screen's to
 * make.
 */
@Composable
private fun SpeedRow(speed: SentenceSpeed?, running: Boolean, onBenchmark: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        when {
            running -> Text(
                stringResource(R.string.settings_sentence_testing),
                style = MaterialTheme.typography.bodySmall,
            )

            speed == null -> Unit

            // Measured and failed is a different sentence from measured and
            // slow, and asks for a different thing.
            !speed.measured -> Text(
                stringResource(R.string.settings_sentence_speed_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )

            else -> Measurement(speed)
        }
        if (!running) {
            TextButton(onClick = onBenchmark) { Text(stringResource(R.string.settings_sentence_test)) }
        }
    }
}

@Composable
private fun Measurement(speed: SentenceSpeed) {
    Text(
        stringResource(R.string.settings_sentence_speed, seconds(speed.generateMillis)),
        style = MaterialTheme.typography.bodyMedium,
    )
    if (speed.generateMillis > ModelSpec.BUDGET_MILLIS) {
        Text(
            stringResource(R.string.settings_sentence_speed_slow),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    if (speed.loadMillis > 0) {
        Text(
            stringResource(R.string.settings_sentence_speed_first, seconds(speed.loadMillis)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The model's own row: what it is, how big, and what can be done about it. */
@Composable
private fun ModelRow(
    model: SentenceModelState,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            stringResource(R.string.settings_sentence_model, ModelSpec.DISPLAY_NAME),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            stringResource(R.string.settings_sentence_model_size, megabytes(ModelSpec.SIZE_BYTES)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        when (val state = model.download) {
            is DownloadState.Running -> DownloadProgress(state, onCancel)
            DownloadState.Verifying -> Text(
                stringResource(R.string.settings_sentence_verifying),
                style = MaterialTheme.typography.bodySmall,
            )

            is DownloadState.Failed -> Text(
                stringResource(state.reason.message()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )

            else -> Unit
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (model.installed) {
                TextButton(onClick = onDelete) {
                    Text(stringResource(R.string.settings_sentence_delete, megabytes(model.bytesOnDisk)))
                }
            } else if (model.download !is DownloadState.Running && model.download != DownloadState.Verifying) {
                Button(onClick = onDownload) {
                    Text(
                        stringResource(
                            // A resumed download is a different promise from a
                            // fresh one, and on a metered connection the
                            // difference is the whole decision.
                            if (model.resumable) {
                                R.string.settings_sentence_resume
                            } else {
                                R.string.settings_sentence_download
                            },
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadProgress(state: DownloadState.Running, onCancel: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        LinearProgressIndicator(
            progress = { state.fraction },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                stringResource(
                    R.string.settings_sentence_progress,
                    megabytes(state.bytes),
                    megabytes(state.total),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        }
    }
}

/** Why this phone is not being offered the feature, in words it can act on. */
@Composable
private fun UnavailableHere(capability: Capability) {
    val text = when (capability) {
        Capability.UnsupportedProcessor -> stringResource(R.string.settings_sentence_no_processor)
        is Capability.NotEnoughMemory -> stringResource(R.string.settings_sentence_no_memory)
        is Capability.NotEnoughStorage -> stringResource(
            R.string.settings_sentence_no_storage,
            megabytes(capability.neededBytes),
        )

        Capability.Ready -> return
    }
    Text(stringResource(R.string.settings_sentence_help), style = MaterialTheme.typography.titleMedium)
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun DownloadState.Reason.message(): Int = when (this) {
    DownloadState.Reason.NETWORK -> R.string.settings_sentence_failed_network
    DownloadState.Reason.DISK -> R.string.settings_sentence_failed_disk
    DownloadState.Reason.CORRUPT -> R.string.settings_sentence_failed_corrupt
}

/** Megabytes, because nobody reads 347251840. */
private const val BYTES_PER_MB = 1024 * 1024

private fun megabytes(bytes: Long): Int = (bytes / BYTES_PER_MB).toInt()

private const val MILLIS_PER_SECOND = 1000f

/**
 * Milliseconds as seconds to one decimal, in the reader's own number format —
 * `1,8` in Spanish and `1.8` in English.
 */
private fun seconds(millis: Int): String =
    String.format(Locale.getDefault(), "%.1f", millis / MILLIS_PER_SECOND)
