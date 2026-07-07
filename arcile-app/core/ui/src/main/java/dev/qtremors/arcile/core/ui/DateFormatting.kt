package dev.qtremors.arcile.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun rememberDateFormatter(pattern: String): SimpleDateFormat {
    val configuration = LocalConfiguration.current
    return remember(pattern, configuration) {
        SimpleDateFormat(pattern, configuration.primaryLocale())
    }
}

@Composable
fun rememberDateTimeFormatter(): DateFormat {
    val configuration = LocalConfiguration.current
    return remember(configuration) {
        DateFormat.getDateTimeInstance(
            DateFormat.MEDIUM,
            DateFormat.SHORT,
            configuration.primaryLocale()
        )
    }
}

@Composable
fun rememberDateOnlyFormatter(): DateFormat {
    val configuration = LocalConfiguration.current
    return remember(configuration) {
        DateFormat.getDateInstance(DateFormat.MEDIUM, configuration.primaryLocale())
    }
}

private fun android.content.res.Configuration.primaryLocale(): Locale {
    val locales = androidx.core.os.ConfigurationCompat.getLocales(this)
    return if (!locales.isEmpty) locales.get(0) ?: Locale.getDefault() else Locale.getDefault()
}
