package com.example.cemo.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/** Unwraps a (possibly wrapped) context to its backing Activity, if there is one. */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * A context that keeps its Activity as the base — so startActivity(), credential
 * flows and unwrapping to the Activity still work — but serves resources in a
 * chosen locale. Context.getString/getText are final and route through
 * getResources(), so overriding that is enough for localized lookups.
 */
private class LocalizedContextWrapper(base: Context, locale: Locale) : ContextWrapper(base) {
    private val localizedResources: Resources = run {
        val config = Configuration(base.resources.configuration).apply { setLocale(locale) }
        base.createConfigurationContext(config).resources
    }

    override fun getResources(): Resources = localizedResources
}

/**
 * Applies [language] to resources in-place. Because the app switches language by
 * overriding this composition-local context rather than recreating the Activity,
 * both stringResource() and resource-backed TranslatedText update instantly with
 * no black-screen flicker.
 */
@Composable
fun ProvideLocalizedResources(language: String, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val localizedContext = remember(context, language) {
        val locale = if (language == "Tagalog") Locale("fil") else Locale.ENGLISH
        LocalizedContextWrapper(context, locale)
    }
    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedContext.resources.configuration,
        content = content
    )
}
