package dev.qtremors.arcile.core.storage.domain

import java.util.Locale

/**
 * Scope defining which part of the filename is modified during batch rename.
 */
enum class RenameTargetScope {
    NAME_ONLY,
    EXTENSION_ONLY,
    BOTH
}

/**
 * Letter case transformations applied during batch rename.
 */
enum class CaseTransform {
    NONE,
    UPPERCASE,
    LOWERCASE,
    TITLE_CASE
}

/**
 * Configuration for adding sequential numbering to renamed files.
 */
@Immutable
data class EnumerationConfig(
    val enabled: Boolean = false,
    val start: Int = 1,
    val step: Int = 1,
    val padding: Int = 2,
    val prefix: String = "",
    val suffix: String = ""
)

/**
 * Rule configuration for batch renaming operations.
 */
@Immutable
data class BatchRenameRule(
    val findQuery: String = "",
    val replacement: String = "",
    val useRegex: Boolean = false,
    val matchCase: Boolean = false,
    val targetScope: RenameTargetScope = RenameTargetScope.NAME_ONLY,
    val caseTransform: CaseTransform = CaseTransform.NONE,
    val enumeration: EnumerationConfig = EnumerationConfig()
)

/**
 * Errors that prevent a proposed filename from being applied.
 */
enum class BatchRenameError {
    EMPTY_NAME,
    INVALID_CHARACTERS,
    DUPLICATE_IN_BATCH,
    EXISTS_ON_DISK
}

/**
 * Individual file rename preview result evaluated by [BatchRenameEngine].
 */
@Immutable
data class BatchRenameItem(
    val file: FileModel,
    val proposedName: String,
    val error: BatchRenameError? = null
) {
    val isChanged: Boolean get() = file.name != proposedName
    val isValid: Boolean get() = error == null
}

/**
 * Pure domain engine to evaluate batch file renaming rules and live previews.
 */
object BatchRenameEngine {

    private val FORBIDDEN_CHARS_REGEX = Regex("[\u0000/:*?\"<>|]")

    fun evaluate(
        files: List<FileModel>,
        rule: BatchRenameRule,
        existingFolderNames: Set<String> = emptySet()
    ): List<BatchRenameItem> = evaluateByFolder(
        files = files,
        rule = rule,
        folderKey = { "" },
        unselectedNamesByFolder = mapOf("" to (existingFolderNames - files.map { it.name }.toSet()))
    )

    fun evaluateByFolder(
        files: List<FileModel>,
        rule: BatchRenameRule,
        folderKey: (FileModel) -> String,
        unselectedNamesByFolder: Map<String, Set<String>>,
        nameKey: (String) -> String? = { it }
    ): List<BatchRenameItem> {
        val results = mutableListOf<BatchRenameItem>()
        val batchNamesByFolder = mutableMapOf<String, MutableSet<String>>()
        val existingKeysByFolder = unselectedNamesByFolder.mapValues { (_, names) ->
            names.mapNotNullTo(mutableSetOf(), nameKey)
        }

        files.forEachIndexed { index, file ->
            val folder = folderKey(file)
            val batchNames = batchNamesByFolder.getOrPut(folder) { mutableSetOf() }
            val proposedName = applyRule(file, rule, index)
            val proposedKey = nameKey(proposedName)
            val error = validateProposedName(
                proposedName = proposedName,
                proposedKey = proposedKey,
                batchNames = batchNames,
                existingFolderNames = existingKeysByFolder[folder].orEmpty()
            )

            if (error == null) {
                batchNames.add(requireNotNull(proposedKey))
            }

            results.add(
                BatchRenameItem(
                    file = file,
                    proposedName = proposedName,
                    error = error
                )
            )
        }

        return results
    }

    private fun applyRule(file: FileModel, rule: BatchRenameRule, index: Int): String {
        val originalName = file.name
        if (file.isDirectory || !originalName.contains('.')) {
            // Treat as single string without extension
            return processPart(originalName, rule, index, isExtension = false)
        }

        val dotIndex = originalName.lastIndexOf('.')
        val baseName = originalName.substring(0, dotIndex)
        val extension = originalName.substring(dotIndex + 1)

        return when (rule.targetScope) {
            RenameTargetScope.NAME_ONLY -> {
                val newBase = processPart(baseName, rule, index, isExtension = false)
                if (extension.isEmpty()) newBase else "$newBase.$extension"
            }
            RenameTargetScope.EXTENSION_ONLY -> {
                val newExt = processPart(extension, rule, index, isExtension = true)
                if (newExt.isEmpty()) baseName else "$baseName.$newExt"
            }
            RenameTargetScope.BOTH -> {
                processPart(originalName, rule, index, isExtension = false)
            }
        }
    }

    private fun processPart(
        input: String,
        rule: BatchRenameRule,
        index: Int,
        isExtension: Boolean
    ): String {
        var result = input

        // 1. Find & Replace
        if (rule.findQuery.isNotEmpty()) {
            result = if (rule.useRegex) {
                try {
                    val options = if (rule.matchCase) emptySet() else setOf(RegexOption.IGNORE_CASE)
                    Regex(rule.findQuery, options).replace(result, rule.replacement)
                } catch (_: Exception) {
                    result
                }
            } else {
                result.replace(rule.findQuery, rule.replacement, ignoreCase = !rule.matchCase)
            }
        }

        // 2. Case Transform
        result = when (rule.caseTransform) {
            CaseTransform.NONE -> result
            CaseTransform.UPPERCASE -> result.uppercase(Locale.getDefault())
            CaseTransform.LOWERCASE -> result.lowercase(Locale.getDefault())
            CaseTransform.TITLE_CASE -> toTitleCase(result)
        }

        // 3. Enumeration (Numbering)
        if (rule.enumeration.enabled && !isExtension) {
            val num = rule.enumeration.start + (index * rule.enumeration.step)
            val paddedNum = num.toString().padStart(rule.enumeration.padding, '0')
            val counterStr = "${rule.enumeration.prefix}$paddedNum${rule.enumeration.suffix}"

            result = if (result.contains("{n}") || result.contains("{counter}")) {
                result.replace("{n}", counterStr).replace("{counter}", counterStr)
            } else {
                "${result}_$counterStr"
            }
        }

        return result
    }

    private fun toTitleCase(text: String): String {
        return text.split(" ").joinToString(" ") { word ->
            word.lowercase(Locale.getDefault())
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
    }

    private fun validateProposedName(
        proposedName: String,
        proposedKey: String?,
        batchNames: Set<String>,
        existingFolderNames: Set<String>
    ): BatchRenameError? {
        val trimmed = proposedName.trim()
        if (trimmed.isEmpty()) return BatchRenameError.EMPTY_NAME
        if (proposedKey == null || FORBIDDEN_CHARS_REGEX.containsMatchIn(proposedName)) {
            return BatchRenameError.INVALID_CHARACTERS
        }
        if (batchNames.contains(proposedKey)) return BatchRenameError.DUPLICATE_IN_BATCH
        if (existingFolderNames.contains(proposedKey)) return BatchRenameError.EXISTS_ON_DISK
        return null
    }
}
