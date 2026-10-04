package dev.qtremors.arcile.buildlogic

private val semanticsScopeStart = Regex(
    """\b(?:semantics|clearAndSetSemantics)\s*(?:\([^)]*\))?\s*\{|\bCustomAccessibilityAction\s*\(|\b(?:onClick|onLongClick)\s*\(\s*label\s*="""
)
private val kotlinScopeTokens = Regex(
    """//[^\n]*|/\*.*?\*/|""" + "\"\"\".*?\"\"\"|" + """"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|[{}()]""",
    RegexOption.DOT_MATCHES_ALL
)
private val stringInterpolation = Regex("""\$\{[^}]*}|\$[A-Za-z_]\w*""")

/** Checks spoken-label scopes, so unrelated animation labels stay outside this rule. */
internal fun hardcodedSemanticsLines(source: String): Set<Int> = buildSet {
    val quotedOrCommented = kotlinScopeTokens.findAll(source)
        .filter { it.value.startsWith('"') || it.value.startsWith('\'') || it.value.startsWith('/') }
        .map { it.range }.toList()
    for (scope in semanticsScopeStart.findAll(source)) {
        if (quotedOrCommented.any { scope.range.first in it }) continue
        val opening = if (source[scope.range.last] == '{') scope.range.last else source.indexOf('(', scope.range.first)
        val openingToken = source[opening].toString()
        val closingToken = if (openingToken == "{") "}" else ")"
        var depth = 0
        for (token in kotlinScopeTokens.findAll(source, opening)) {
            when (token.value) {
                openingToken -> depth++
                closingToken -> {
                    depth--
                    if (depth == 0) break
                }
                else -> if (depth > 0 && token.value.startsWith('"')) {
                    val literal = stringInterpolation.replace(token.value.trim('"'), "")
                    if (literal.any { it in 'A'..'Z' || it in 'a'..'z' }) {
                        add(source.take(token.range.first).count { it == '\n' })
                    }
                }
            }
        }
    }
}
