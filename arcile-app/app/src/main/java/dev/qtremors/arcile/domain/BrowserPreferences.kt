package dev.qtremors.arcile.domain

import dev.qtremors.arcile.presentation.FileSortOption

data class BrowserPreferences(
    val globalSortOption: FileSortOption = FileSortOption.NAME_ASC,
    val pathSortOptions: Map<String, FileSortOption> = emptyMap()
) {
    fun getSortOptionForPath(path: String): FileSortOption {
        var currentPath = path.trimEnd('/')
        if (currentPath.isEmpty()) currentPath = "/"
        
        while (currentPath.isNotEmpty()) {
            if (pathSortOptions.containsKey(currentPath)) {
                return pathSortOptions[currentPath]!!
            }
            val lastSlash = currentPath.lastIndexOf('/')
            if (lastSlash > 0) {
                currentPath = currentPath.substring(0, lastSlash)
            } else if (lastSlash == 0) {
                if (pathSortOptions.containsKey("/")) {
                    return pathSortOptions["/"]!!
                }
                break
            } else {
                break
            }
        }
        return globalSortOption
    }

    fun getSortOptionForCategory(categoryName: String): FileSortOption {
        return pathSortOptions["category_$categoryName"] ?: FileSortOption.DATE_NEWEST
    }
}
