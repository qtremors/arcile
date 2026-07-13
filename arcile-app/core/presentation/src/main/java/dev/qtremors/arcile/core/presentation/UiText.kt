package dev.qtremors.arcile.core.presentation

sealed interface UiText {
    data class StringResource(
        val resId: Int,
        val args: List<Any> = emptyList()
    ) : UiText

    data class PluralResource(
        val resId: Int,
        val quantity: Int,
        val args: List<Any> = emptyList()
    ) : UiText

    data class Dynamic(val value: String) : UiText
}
