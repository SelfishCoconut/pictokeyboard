package org.pictokeyboard.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.pictokeyboard.R
import org.pictokeyboard.data.arasaac.ArasaacOptions

// The skin-tone and hair-colour swatches for ARASAAC's render parameters: the
// control, the colours it shows, and the names it announces. Split out of
// AddPictosDialogs.kt, which was over detekt's per-file function budget once the
// names arrived -- and which is about dialogs, not about swatch data.

/** The three ARASAAC render parameters: skin tone, hair colour, and colour on/off. */
@Composable
internal fun ArasaacCustomization(
    skin: String?,
    hair: String?,
    color: Boolean,
    onSkin: (String?) -> Unit,
    onHair: (String?) -> Unit,
    onColor: (Boolean) -> Unit,
) {
    Text(stringResource(R.string.picto_customize), style = MaterialTheme.typography.labelLarge)
    SwatchRow(
        label = stringResource(R.string.picto_skin),
        values = ArasaacOptions.SKIN_TONES,
        selected = skin,
        colorFor = ::skinSwatch,
        nameFor = ::skinName,
        onSelect = onSkin,
    )
    SwatchRow(
        label = stringResource(R.string.picto_hair),
        values = ArasaacOptions.HAIR_COLORS,
        selected = hair,
        colorFor = ::hairSwatch,
        nameFor = ::hairName,
        onSelect = onHair,
    )
    SwitchRow(stringResource(R.string.picto_color), color, onColor)
}

/**
 * One row of swatches for an ARASAAC parameter.
 *
 * [nameFor] returns a string resource rather than a `String` because the name has
 * to be resolved inside this composable -- `stringResource` cannot be called from
 * a plain lambda. (`@StringRes` cannot be written on it: the annotation applies to
 * the parameter, which is a function type, not to what the function returns.)
 */
@Composable
private fun SwatchRow(
    label: String,
    values: List<String>,
    selected: String?,
    colorFor: (String) -> Color,
    nameFor: (String) -> Int,
    onSelect: (String?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Swatch(
                fill = MaterialTheme.colorScheme.surfaceVariant,
                // The "none" option's only content was a bare em dash, which
                // TalkBack reads as nothing at all.
                name = stringResource(R.string.picto_option_none),
                isSelected = selected == null,
                isNone = true,
            ) { onSelect(null) }
            values.forEach { value ->
                Swatch(
                    fill = colorFor(value),
                    name = stringResource(nameFor(value)),
                    isSelected = selected == value,
                    isNone = false,
                ) { onSelect(value) }
            }
        }
    }
}

@Composable
private fun Swatch(fill: Color, name: String, isSelected: Boolean, isNone: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .background(fill, CircleShape)
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
                shape = CircleShape,
            )
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = name },
    ) {
        if (isNone) {
            Text("—", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun skinSwatch(value: String): Color = SKIN_SWATCHES[value] ?: SWATCH_UNKNOWN

private fun hairSwatch(value: String): Color = HAIR_SWATCHES[value] ?: SWATCH_UNKNOWN

/**
 * What each swatch announces.
 *
 * These describe the *tone*, not ARASAAC's parameter key. The keys name
 * ethnicities -- "mulatto", "aztec", "assian" -- and are not fit to be read
 * aloud to anyone; what the control actually offers is a colour, so a colour is
 * what it says. An unrecognised key falls back to the "Default" label rather
 * than announcing nothing, which is the failure this whole change is about.
 */
@StringRes
private fun skinName(value: String): Int = when (value) {
    "white" -> R.string.skin_light
    "mulatto" -> R.string.skin_medium
    "aztec" -> R.string.skin_medium_dark
    "black" -> R.string.skin_dark
    "assian" -> R.string.skin_warm_light
    else -> R.string.picto_option_none
}

@StringRes
private fun hairName(value: String): Int = when (value) {
    "blonde" -> R.string.hair_blonde
    "brown" -> R.string.hair_brown
    "darkBrown" -> R.string.hair_dark_brown
    "gray" -> R.string.hair_grey
    "darkGray" -> R.string.hair_dark_grey
    "red" -> R.string.hair_red
    "black" -> R.string.hair_black
    else -> R.string.picto_option_none
}

/**
 * Approximations of ARASAAC's skin and hair option values, used only to tint the
 * picker swatches -- the pictogram itself is recoloured by ARASAAC's own API, so
 * these need to be recognisable rather than exact. The keys are ARASAAC's
 * spellings, including "assian", and must match them to keep the API happy.
 */
private val SKIN_SWATCHES = mapOf(
    // These are the only legitimate colour literals in the UI layer: they are not
    // theme, they are *data* -- an approximation of what ARASAAC's own skin and
    // hair parameters produce, so the swatch shows the caregiver what they are
    // choosing. They must not follow the light/dark scheme, because the pictogram
    // they describe does not either.
    "white" to Color(0xFFF1C9A5),
    "mulatto" to Color(0xFFD49E7A),
    "aztec" to Color(0xFFB87A4B),
    "black" to Color(0xFF6B4423),
    "assian" to Color(0xFFF0C27B),
)

private val HAIR_SWATCHES = mapOf(
    "blonde" to Color(0xFFE6C76E),
    "brown" to Color(0xFF8B5A2B),
    "darkBrown" to Color(0xFF4B2E1E),
    "gray" to Color(0xFFBDBDBD),
    "darkGray" to Color(0xFF616161),
    "red" to Color(0xFFB5482E),
    "black" to Color(0xFF1A1A1A),
)

/**
 * Stand-in for an ARASAAC skin or hair value we have no swatch for. A literal for
 * the same reason the swatches above are: it stands in for a colour the remote
 * renderer will produce, so it must not shift with the light/dark scheme. Only
 * the ring around it is themed.
 */
private val SWATCH_UNKNOWN = Color(0xFFBDBDBD)
