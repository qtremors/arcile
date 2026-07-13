package dev.qtremors.arcile.core.presentation

object SelectionReducer {
    fun toggle(selected: Set<String>, path: String): Set<String> =
        if (path in selected) selected - path else selected + path

    fun add(selected: Set<String>, paths: Iterable<String>): Set<String> =
        selected + paths

    fun all(paths: Iterable<String>): Set<String> = paths.toSet()

    fun invert(selected: Set<String>, candidates: Iterable<String>): Set<String> =
        candidates.filterNot(selected::contains).toSet()

    fun retain(selected: Set<String>, validPaths: Iterable<String>): Set<String> =
        selected.intersect(validPaths.toSet())

    fun remove(selected: Set<String>, paths: Iterable<String>): Set<String> =
        selected - paths.toSet()
}
