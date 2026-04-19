package dev.qtremors.arcile.data.source

import java.io.File

internal object FileConflictNameGenerator {
    fun generateKeepBothTarget(destDir: File, sourceFile: File): File {
        val originalName = sourceFile.nameWithoutExtension
        val ext = if (sourceFile.extension.isNotEmpty()) ".${sourceFile.extension}" else ""

        val copyPattern = Regex("""^(.*?)(?: \((\d+)\))?$""")
        val matchResult = copyPattern.matchEntire(originalName)

        val baseName: String
        var copyIndex = 1

        if (matchResult != null) {
            val matchedBase = matchResult.groupValues[1]
            val matchedNumber = matchResult.groupValues[2]

            if (matchedNumber.isNotEmpty()) {
                baseName = matchedBase
                copyIndex = matchedNumber.toInt() + 1
            } else {
                baseName = originalName
            }
        } else {
            baseName = originalName
        }

        var target: File
        do {
            val suffix = " ($copyIndex)"
            target = File(destDir, "$baseName$suffix$ext")
            copyIndex++
        } while (target.exists())

        return target
    }
}
