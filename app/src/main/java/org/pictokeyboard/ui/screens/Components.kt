package org.pictokeyboard.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import org.pictokeyboard.ui.theme.Spacing

/**
 * The "add" button, shared by the category and picto screens.
 *
 * It names its colours because a Material FAB defaults to `primaryContainer`,
 * which in this palette resolves to the near-invisible `line` — and the one
 * button on the screen that creates things must not be the quietest thing on it.
 */
@Composable
fun AddFab(contentDescription: String, onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Icon(Icons.Filled.Add, contentDescription = contentDescription)
    }
}

/**
 * Spanish / English voice-and-text language selector, shared across screens.
 *
 * The colours are named rather than left to Material. `FilterChip` takes its
 * selected fill from `secondaryContainer` and its unselected border from
 * `outlineVariant`, both of which resolve to the decorative `line` here — so the
 * chosen language was a 1.19:1 fill beside a 1.19:1 outline and the selection was
 * invisible. A check mark goes with it, so the state is not carried by colour
 * alone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageChips(selected: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        LanguageChip("es", "Español", selected, onSelect)
        LanguageChip("en", "English", selected, onSelect)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageChip(tag: String, label: String, selected: String, onSelect: (String) -> Unit) {
    val isSelected = selected == tag
    FilterChip(
        selected = isSelected,
        onClick = { onSelect(tag) },
        label = { Text(label) },
        leadingIcon = if (isSelected) {
            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
        } else {
            null
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = isSelected,
            borderColor = MaterialTheme.colorScheme.outline,
        ),
    )
}

/** Opens the Android system input-method settings page. */
fun openInputMethodSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

/** Shows the keyboard picker so the user can switch to PictoKeyboard. */
fun showKeyboardPicker(context: Context) {
    (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
        .showInputMethodPicker()
}
