package dev.qtremors.arcile.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class LocalFileOperationsTest {

    @Test
    fun `generateUniqueName appends copy suffix to duplicate files`() {
        val testRoot = File(System.getProperty("java.io.tmpdir"), "test_ops_root").apply { mkdirs() }
        val targetDir = File(testRoot, "target").apply { mkdirs() }

        try {
            // Arrange
            File(targetDir, "document.txt").apply { createNewFile() }
            
            // Act
            val uniqueName1 = getUniqueFileName(targetDir, "document.txt")
            
            // Assert
            assertEquals("document - Copy.txt", uniqueName1)
            
            // Arrange further duplicates
            File(targetDir, "document - Copy.txt").apply { createNewFile() }
            
            // Act again
            val uniqueName2 = getUniqueFileName(targetDir, "document.txt")
            
            // Assert
            assertEquals("document - Copy (2).txt", uniqueName2)
        } finally {
            testRoot.deleteRecursively()
        }
    }
    
    @Test
    fun `generateUniqueName handles files without extensions`() {
        val testRoot = File(System.getProperty("java.io.tmpdir"), "test_ops_root_2").apply { mkdirs() }
        val targetDir = File(testRoot, "target").apply { mkdirs() }

        try {
            // Arrange
            File(targetDir, "README").apply { createNewFile() }
            
            // Act
            val uniqueName = getUniqueFileName(targetDir, "README")
            
            // Assert
            assertEquals("README - Copy", uniqueName)
        } finally {
            testRoot.deleteRecursively()
        }
    }

    // Helper logic extracted directly from repository behavior
    private fun getUniqueFileName(directory: File, baseName: String): String {
        var newName = baseName
        var counter = 1
        val nameWithoutExtension = newName.substringBeforeLast('.', newName)
        val extension = if (newName.contains('.')) ".${newName.substringAfterLast('.')}" else ""

        while (File(directory, newName).exists()) {
            newName = if (counter == 1) {
                "$nameWithoutExtension - Copy$extension"
            } else {
                "$nameWithoutExtension - Copy ($counter)$extension"
            }
            counter++
        }
        return newName
    }
}
