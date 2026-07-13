package dev.qtremors.arcile.feature.browser

import androidx.lifecycle.SavedStateHandle

internal class BrowserScrollPositionStore(
    private val savedStateHandle: SavedStateHandle
) {
    private val positions = decode(
        savedStateHandle.get<Array<String>>(SAVED_SCROLL_POSITIONS_KEY)?.toList().orEmpty()
    ).toMutableMap()

    fun get(key: String): BrowserScrollPosition? = positions[key]

    fun save(key: String, position: BrowserScrollPosition) {
        positions[key] = position
        persist()
    }

    fun clear(key: String) {
        if (positions.remove(key) != null) persist()
    }

    private fun persist() {
        savedStateHandle[SAVED_SCROLL_POSITIONS_KEY] = positions.entries
            .toList()
            .takeLast(MAX_SAVED_BROWSER_SCROLL_ENTRIES)
            .map { entry -> encode(entry.key, entry.value) }
            .toTypedArray()
    }

    private fun decode(entries: List<String>): Map<String, BrowserScrollPosition> =
        entries.mapNotNull { entry ->
            val key = entry.key() ?: return@mapNotNull null
            val position = entry.position() ?: return@mapNotNull null
            key to position
        }.toMap()

    private fun encode(key: String, position: BrowserScrollPosition): String = buildString {
        append(key.length)
        append(':')
        append(key)
        append(':')
        append(position.listIndex)
        append(':')
        append(position.listOffset)
        append(':')
        append(position.gridIndex)
        append(':')
        append(position.gridOffset)
    }

    private fun String.key(): String? {
        val separatorIndex = indexOf(':')
        if (separatorIndex <= 0) return null
        val keyLength = substring(0, separatorIndex).toIntOrNull() ?: return null
        val keyStart = separatorIndex + 1
        val keyEnd = keyStart + keyLength
        if (keyEnd > length) return null
        return substring(keyStart, keyEnd)
    }

    private fun String.position(): BrowserScrollPosition? {
        val key = key() ?: return null
        val valuesStart = indexOf(':') + 1 + key.length
        if (valuesStart >= length || this[valuesStart] != ':') return null
        val values = substring(valuesStart + 1).split(':')
        if (values.size != 4) return null
        return BrowserScrollPosition(
            listIndex = values[0].toIntOrNull() ?: return null,
            listOffset = values[1].toIntOrNull() ?: return null,
            gridIndex = values[2].toIntOrNull() ?: return null,
            gridOffset = values[3].toIntOrNull() ?: return null
        )
    }

    private companion object {
        const val SAVED_SCROLL_POSITIONS_KEY = "browserScrollPositions"
        const val MAX_SAVED_BROWSER_SCROLL_ENTRIES = 32
    }
}
