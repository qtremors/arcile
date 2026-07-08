package dev.qtremors.arcile.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import java.text.DateFormat
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
        val locale = configuration.primaryLocale()
        SimpleDateFormat(pattern, locale)
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

fun getPresetRanges(anyLabel: String): List<Pair<String, Pair<Long?, Long?>>> {
    val cal = java.util.Calendar.getInstance()
    
    fun toMidnight(c: java.util.Calendar) {
        c.set(java.util.Calendar.HOUR_OF_DAY, 0)
        c.set(java.util.Calendar.MINUTE, 0)
        c.set(java.util.Calendar.SECOND, 0)
        c.set(java.util.Calendar.MILLISECOND, 0)
    }

    val dayMillis = 24L * 60 * 60 * 1000

    val calToday = java.util.Calendar.getInstance()
    toMidnight(calToday)
    val todayStart = calToday.timeInMillis
    val todayEnd = todayStart + dayMillis - 1

    val yesterdayStart = todayStart - dayMillis
    val yesterdayEnd = todayStart - 1

    val calThisWeek = java.util.Calendar.getInstance()
    toMidnight(calThisWeek)
    val dayOfWeek = calThisWeek.get(java.util.Calendar.DAY_OF_WEEK)
    val daysFromMonday = if (dayOfWeek == java.util.Calendar.SUNDAY) 6 else dayOfWeek - java.util.Calendar.MONDAY
    calThisWeek.add(java.util.Calendar.DAY_OF_YEAR, -daysFromMonday)
    val thisWeekStart = calThisWeek.timeInMillis

    val lastWeekStart = thisWeekStart - 7 * dayMillis
    val lastWeekEnd = thisWeekStart - 1

    val calThisMonth = java.util.Calendar.getInstance()
    toMidnight(calThisMonth)
    calThisMonth.set(java.util.Calendar.DAY_OF_MONTH, 1)
    val thisMonthStart = calThisMonth.timeInMillis

    val calLastMonth = java.util.Calendar.getInstance()
    toMidnight(calLastMonth)
    calLastMonth.set(java.util.Calendar.DAY_OF_MONTH, 1)
    calLastMonth.add(java.util.Calendar.MONTH, -1)
    val lastMonthStart = calLastMonth.timeInMillis
    
    val calLastMonthEnd = java.util.Calendar.getInstance()
    toMidnight(calLastMonthEnd)
    calLastMonthEnd.set(java.util.Calendar.DAY_OF_MONTH, 1)
    val lastMonthEnd = calLastMonthEnd.timeInMillis - 1

    val currentYear = calToday.get(java.util.Calendar.YEAR)
    fun getQuarterRange(quarter: Int): Pair<Long, Long> {
        val cStart = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.YEAR, currentYear)
            set(java.util.Calendar.MONTH, (quarter - 1) * 3)
            set(java.util.Calendar.DAY_OF_MONTH, 1)
            toMidnight(this)
        }
        val cEnd = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.YEAR, currentYear)
            set(java.util.Calendar.MONTH, quarter * 3)
            set(java.util.Calendar.DAY_OF_MONTH, 1)
            toMidnight(this)
            add(java.util.Calendar.MILLISECOND, -1)
        }
        return cStart.timeInMillis to cEnd.timeInMillis
    }
    val q1 = getQuarterRange(1)
    val q2 = getQuarterRange(2)
    val q3 = getQuarterRange(3)
    val q4 = getQuarterRange(4)

    val months = (0..11).map { mIndex ->
        val cMonthStart = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.YEAR, currentYear)
            set(java.util.Calendar.MONTH, mIndex)
            set(java.util.Calendar.DAY_OF_MONTH, 1)
            toMidnight(this)
        }
        val cMonthEnd = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.YEAR, currentYear)
            set(java.util.Calendar.MONTH, mIndex + 1)
            set(java.util.Calendar.DAY_OF_MONTH, 1)
            toMidnight(this)
            add(java.util.Calendar.MILLISECOND, -1)
        }
        val sdf = java.text.SimpleDateFormat("MMMM", java.util.Locale.getDefault())
        sdf.format(cMonthStart.time) to (cMonthStart.timeInMillis to cMonthEnd.timeInMillis)
    }

    val calThisYearStart = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.YEAR, currentYear)
        set(java.util.Calendar.MONTH, java.util.Calendar.JANUARY)
        set(java.util.Calendar.DAY_OF_MONTH, 1)
        toMidnight(this)
    }
    val thisYearStart = calThisYearStart.timeInMillis

    val years = (0..3).map { offset ->
        val targetYear = currentYear - offset
        val cYearStart = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.YEAR, targetYear)
            set(java.util.Calendar.MONTH, java.util.Calendar.JANUARY)
            set(java.util.Calendar.DAY_OF_MONTH, 1)
            toMidnight(this)
        }
        val cYearEnd = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.YEAR, targetYear + 1)
            set(java.util.Calendar.MONTH, java.util.Calendar.JANUARY)
            set(java.util.Calendar.DAY_OF_MONTH, 1)
            toMidnight(this)
            add(java.util.Calendar.MILLISECOND, -1)
        }
        targetYear.toString() to (cYearStart.timeInMillis to cYearEnd.timeInMillis)
    }

    val presets = mutableListOf<Pair<String, Pair<Long?, Long?>>>()
    presets.add(anyLabel to (null to null))
    presets.add("Today" to (todayStart to todayEnd))
    presets.add("Yesterday" to (yesterdayStart to yesterdayEnd))
    presets.add("This Week" to (thisWeekStart to null))
    presets.add("Last Week" to (lastWeekStart to lastWeekEnd))
    presets.add("This Month" to (thisMonthStart to null))
    presets.add("Last Month" to (lastMonthStart to lastMonthEnd))
    presets.add("Q1" to q1)
    presets.add("Q2" to q2)
    presets.add("Q3" to q3)
    presets.add("Q4" to q4)
    
    months.forEach { presets.add(it) }
    
    presets.add("This Year" to (thisYearStart to null))
    years.forEach {
        if (it.first != currentYear.toString()) {
            presets.add(it.first to it.second)
        }
    }
    
    return presets
}

fun getPresetSizes(anyLabel: String): List<Pair<String, Pair<Long?, Long?>>> {
    return listOf(
        anyLabel to (null to null),
        "Tiny (< 100 KB)" to (null to 100L * 1024),
        "Small (100 KB - 1 MB)" to (100L * 1024 to 1L * 1024 * 1024),
        "Medium (1 MB - 50 MB)" to (1L * 1024 * 1024 to 50L * 1024 * 1024),
        "Large (50 MB - 500 MB)" to (50L * 1024 * 1024 to 500L * 1024 * 1024),
        "Huge (> 500 MB)" to (500L * 1024 * 1024 to null)
    )
}
