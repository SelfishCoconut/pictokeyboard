package org.pictokeyboard.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * Wraps the original (Activity) context so its resources resolve in the chosen
 * locale, while keeping the Activity reachable up the [ContextWrapper] chain.
 * That last part matters: things like `rememberLauncherForActivityResult` walk
 * the context to find the Activity, so we must not replace it with a detached
 * `createConfigurationContext`, only re-skin its [Resources].
 */
private class LocaleContextWrapper(base: Context, private val localizedResources: Resources) : ContextWrapper(base) {
    override fun getResources(): Resources = localizedResources
    override fun getAssets(): AssetManager = localizedResources.assets
}

/**
 * Forces the Compose UI to render in [language] — the in-app "Default language"
 * setting — regardless of the device's system locale, so the whole app matches
 * the board's language instead of mixing English chrome with Spanish content.
 * Re-localizes live when the setting changes.
 */
@Composable
fun ProvideAppLocale(language: String, content: @Composable () -> Unit) {
    val context = LocalContext.current
    // Read the configuration through LocalConfiguration, not
    // context.resources.configuration: only the composition local is observable,
    // so the latter would not re-derive the localized context when the
    // configuration changes (font scale, orientation, dark mode).
    val configuration = LocalConfiguration.current
    val localizedContext = remember(language, context, configuration) {
        val locale = Locale.forLanguageTag(language)
        val config = Configuration(configuration).apply { setLocale(locale) }
        val configContext = context.createConfigurationContext(config)
        LocaleContextWrapper(context, configContext.resources)
    }
    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedContext.resources.configuration,
        content = content,
    )
}
