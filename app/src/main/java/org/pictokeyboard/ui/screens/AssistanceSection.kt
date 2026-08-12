package org.pictokeyboard.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import org.pictokeyboard.R
import org.pictokeyboard.ui.theme.Spacing

/**
 * **Call for help**: who the keyboard's bell rings (#144).
 *
 * Two fields and nothing else. It is deliberately not a contact picker: reading
 * somebody's address book is a great deal to ask of a keyboard in exchange for
 * eleven digits that can be typed once, and the permission would be asked of
 * every caregiver rather than only the ones who want a bell.
 *
 * Empty is the default and means the feature does not exist — no bell on the
 * keyboard, and no call permission ever requested. That is why the permission
 * button only appears once a number has been entered.
 */
@Composable
internal fun AssistanceSection(
    name: String,
    number: String,
    canDialDirectly: Boolean,
    onContact: (String, String) -> Unit,
    onRequestPermission: () -> Unit,
) {
    // Held locally and written through, so a DataStore round trip cannot move
    // the cursor in a field somebody is in the middle of typing into.
    var draftName by rememberSaveable { mutableStateOf(name) }
    var draftNumber by rememberSaveable { mutableStateOf(number) }

    Text(
        stringResource(R.string.settings_assistance_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    OutlinedTextField(
        value = draftName,
        onValueChange = {
            draftName = it
            onContact(it, draftNumber)
        },
        label = { Text(stringResource(R.string.settings_assistance_name)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        stringResource(R.string.settings_assistance_name_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    OutlinedTextField(
        value = draftNumber,
        // Filtered rather than validated: a caregiver pasting a number from a
        // message brings the surrounding text with it, and a bell that dials a
        // name is a bell that does nothing.
        onValueChange = {
            draftNumber = it.filter { c -> c.isDigit() || c in DIALLABLE }
            onContact(draftName, draftNumber)
        },
        label = { Text(stringResource(R.string.settings_assistance_number)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        modifier = Modifier.fillMaxWidth(),
    )

    if (draftNumber.isNotBlank()) {
        Text(
            stringResource(R.string.settings_assistance_countdown),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CallPermission(canDialDirectly, onRequestPermission)
        TextButton(
            onClick = {
                draftName = ""
                draftNumber = ""
                onContact("", "")
            },
        ) {
            Text(stringResource(R.string.settings_assistance_remove))
        }
    }
}

/**
 * Whether the bell dials, or only opens the dialler.
 *
 * Asked for here rather than the first time the bell is pressed, because an
 * `InputMethodService` has no activity and therefore no way to ask — and
 * because the moment somebody presses a call-for-help button is the worst
 * possible moment to be shown a permission dialog.
 */
@Composable
private fun CallPermission(granted: Boolean, onRequest: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        if (granted) {
            Text(
                stringResource(R.string.settings_assistance_allowed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                stringResource(R.string.settings_assistance_allow_why),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onRequest) {
                Text(stringResource(R.string.settings_assistance_allow))
            }
        }
    }
}

/** Everything that can legally appear in a dialled number besides digits. */
private const val DIALLABLE = "+ -()#*"
