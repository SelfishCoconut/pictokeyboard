package org.pictokeyboard.ui.theme

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

/**
 * The three renders every screen has to survive, as one annotation.
 *
 * Previews are the cheapest regression test Compose offers, and the two that
 * actually catch things are the two that are easiest to forget. Dark mode is
 * where a hardcoded colour finally shows itself, and a 200% font scale is where a
 * fixed height around text clips the words — which on this app means clipping the
 * words someone is trying to say.
 *
 * Applying them as a set rather than by hand means a new screen cannot quietly
 * ship with only the light, default-scale case checked.
 */
@Preview(name = "1 · light", showBackground = true)
@Preview(name = "2 · dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "3 · large text", showBackground = true, fontScale = 2f)
annotation class ScreenPreviews
