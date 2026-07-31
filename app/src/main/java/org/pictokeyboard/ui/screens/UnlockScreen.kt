package org.pictokeyboard.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.pictokeyboard.R
import org.pictokeyboard.ui.theme.PictoKeyboardTheme

/**
 * Already stateless: the caller supplies verification, and the typed PIN is
 * local UI state that must not outlive the screen.
 */
@Composable
fun UnlockScreen(
    verify: suspend (String) -> Boolean,
    onUnlocked: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // This screen replaces the whole navigation graph, so no Scaffold insets it.
    // Under edge-to-edge it has to hold itself off the system bars, and safeDrawing
    // keeps the unlock button above the soft keyboard raised by the PIN field.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.pin_enter_title), style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = pin,
            onValueChange = {
                pin = it.filter(Char::isDigit)
                error = false
            },
            label = { Text(stringResource(R.string.pin_field)) },
            isError = error,
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        )
        if (error) {
            Text(stringResource(R.string.pin_wrong), color = MaterialTheme.colorScheme.error)
        }
        Button(
            onClick = {
                scope.launch {
                    if (verify(pin)) {
                        onUnlocked()
                    } else {
                        error = true
                        pin = ""
                    }
                }
            },
            enabled = pin.length >= 4,
        ) {
            Text(stringResource(R.string.pin_unlock))
        }
    }
}

@Preview(name = "Unlock", showBackground = true)
@Composable
private fun UnlockScreenPreview() {
    PictoKeyboardTheme {
        UnlockScreen(verify = { false }, onUnlocked = {})
    }
}
