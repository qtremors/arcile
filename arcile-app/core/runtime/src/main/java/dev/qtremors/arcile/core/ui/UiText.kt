package dev.qtremors.arcile.core.ui

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource


@Composable
fun UiText.asString(): String =
    when (this) {
        is UiText.StringResource -> stringResource(resId, *args.toTypedArray())
        is UiText.PluralResource -> pluralStringResource(resId, quantity, *args.toTypedArray())
        is UiText.Dynamic -> value
    }

fun UiText.asString(context: Context): String =
    when (this) {
        is UiText.StringResource -> context.getString(resId, *args.toTypedArray())
        is UiText.PluralResource -> context.resources.getQuantityString(resId, quantity, *args.toTypedArray())
        is UiText.Dynamic -> value
    }
