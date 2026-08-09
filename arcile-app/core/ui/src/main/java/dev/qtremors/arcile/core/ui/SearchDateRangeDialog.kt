package dev.qtremors.arcile.core.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable

@Composable
internal fun SearchDateRangeDialog(
    initialStartMillis: Long?,
    initialEndMillis: Long?,
    onDismiss: () -> Unit,
    onConfirm: (Long?, Long?) -> Unit,
    contentInsets: WindowInsets = WindowInsets.safeDrawing.union(WindowInsets.ime)
) {
    DateRangePickerDialog(
        initialStartMillis = initialStartMillis,
        initialEndMillis = initialEndMillis,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
        contentInsets = contentInsets
    )
}
