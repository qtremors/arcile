package dev.qtremors.arcile.presentation.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Creates and remembers a [SimpleDateFormat] instance keyed to the current configuration's locales,
 * ensuring that the formatter is properly recreated if the system language/locale changes.
 */
@Composable
fun rememberDateFormatter(pattern: String): SimpleDateFormat {
    val configuration = LocalConfiguration.current
    return remember(pattern, configuration) {
        val locales = androidx.core.os.ConfigurationCompat.getLocales(configuration)
        val locale = if (!locales.isEmpty) locales.get(0) else Locale.getDefault()
        SimpleDateFormat(pattern, locale)
    }
}
