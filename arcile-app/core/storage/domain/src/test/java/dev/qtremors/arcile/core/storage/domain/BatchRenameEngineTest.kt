package dev.qtremors.arcile.core.storage.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchRenameEngineTest {

    private val sampleFiles = listOf(
        FileModel(name = "IMG_001.jpg", reference = "/storage/emulated/0/DCIM/IMG_001.jpg"),
        FileModel(name = "IMG_002.jpg", reference = "/storage/emulated/0/DCIM/IMG_002.jpg"),
        FileModel(name = "photo_test.png", reference = "/storage/emulated/0/DCIM/photo_test.png")
    )

    @Test
    fun testLiteralSearchAndReplace() {
        val rule = BatchRenameRule(
            findQuery = "IMG_",
            replacement = "Vacation_",
            targetScope = RenameTargetScope.NAME_ONLY
        )

        val results = BatchRenameEngine.evaluate(sampleFiles, rule)

        assertEquals("Vacation_001.jpg", results[0].proposedName)
        assertEquals("Vacation_002.jpg", results[1].proposedName)
        assertEquals("photo_test.png", results[2].proposedName)
        assertTrue(results[0].isChanged)
        assertFalse(results[2].isChanged)
    }

    @Test
    fun testRegexReplacement() {
        val rule = BatchRenameRule(
            findQuery = """IMG_(\d+)""",
            replacement = "Pic_$1",
            useRegex = true,
            targetScope = RenameTargetScope.NAME_ONLY
        )

        val results = BatchRenameEngine.evaluate(sampleFiles, rule)

        assertEquals("Pic_001.jpg", results[0].proposedName)
        assertEquals("Pic_002.jpg", results[1].proposedName)
    }

    @Test
    fun testCaseTransforms() {
        val uppercaseRule = BatchRenameRule(
            caseTransform = CaseTransform.UPPERCASE,
            targetScope = RenameTargetScope.NAME_ONLY
        )
        val uppercaseResults = BatchRenameEngine.evaluate(sampleFiles, uppercaseRule)

        assertEquals("IMG_001.jpg", uppercaseResults[0].proposedName)
        assertEquals("PHOTO_TEST.png", uppercaseResults[2].proposedName)

        val titleCaseRule = BatchRenameRule(
            caseTransform = CaseTransform.TITLE_CASE,
            targetScope = RenameTargetScope.NAME_ONLY
        )
        val titleResults = BatchRenameEngine.evaluate(
            listOf(FileModel(name = "hello world.txt", reference = "/test/hello world.txt")),
            titleCaseRule
        )

        assertEquals("Hello World.txt", titleResults[0].proposedName)
    }

    @Test
    fun testEnumeration() {
        val rule = BatchRenameRule(
            findQuery = "IMG_",
            replacement = "Item_",
            enumeration = EnumerationConfig(enabled = true, start = 1, padding = 3),
            targetScope = RenameTargetScope.NAME_ONLY
        )

        val results = BatchRenameEngine.evaluate(sampleFiles, rule)

        assertEquals("Item_001_001.jpg", results[0].proposedName)
        assertEquals("Item_002_002.jpg", results[1].proposedName)
    }

    @Test
    fun testDuplicateInBatchError() {
        val files = listOf(
            FileModel(name = "fileA.txt", reference = "/test/fileA.txt"),
            FileModel(name = "fileB.txt", reference = "/test/fileB.txt")
        )
        val rule = BatchRenameRule(
            findQuery = "fileB",
            replacement = "fileA",
            targetScope = RenameTargetScope.NAME_ONLY
        )

        val results = BatchRenameEngine.evaluate(files, rule)

        assertNull(results[0].error)
        assertEquals(BatchRenameError.DUPLICATE_IN_BATCH, results[1].error)
        assertFalse(results[1].isValid)
    }

    @Test
    fun testExistsOnDiskError() {
        val files = listOf(
            FileModel(name = "fileA.txt", reference = "/test/fileA.txt")
        )
        val existingOnDisk = setOf("fileA.txt", "fileB.txt")

        val rule = BatchRenameRule(
            findQuery = "fileA",
            replacement = "fileB",
            targetScope = RenameTargetScope.NAME_ONLY
        )

        val results = BatchRenameEngine.evaluate(files, rule, existingFolderNames = existingOnDisk)

        assertEquals(BatchRenameError.EXISTS_ON_DISK, results[0].error)
        assertFalse(results[0].isValid)
    }

    @Test
    fun testForbiddenCharactersError() {
        val files = listOf(
            FileModel(name = "fileA.txt", reference = "/test/fileA.txt")
        )
        val rule = BatchRenameRule(
            findQuery = "fileA",
            replacement = "invalid/name",
            targetScope = RenameTargetScope.NAME_ONLY
        )

        val results = BatchRenameEngine.evaluate(files, rule)

        assertEquals(BatchRenameError.INVALID_CHARACTERS, results[0].error)
        assertFalse(results[0].isValid)
    }

    @Test
    fun `grouped preview allows matching names in separate folders but checks canonical collisions within each`() {
        val first = FileModel(name = "photo.txt", reference = "/one/photo.txt")
        val second = FileModel(name = "photo.txt", reference = "/two/photo.txt")
        val rule = BatchRenameRule(findQuery = "photo", replacement = "portrait")
        val results = BatchRenameEngine.evaluateByFolder(
            files = listOf(first, second),
            rule = rule,
            folderKey = { it.reference.substringBeforeLast('/') },
            unselectedNamesByFolder = mapOf("/two" to setOf("PORTRAIT.TXT")),
            nameKey = { it.lowercase(java.util.Locale.ROOT) }
        )

        assertEquals(listOf("portrait.txt", "portrait.txt"), results.map { it.proposedName })
        assertEquals(listOf(null, BatchRenameError.EXISTS_ON_DISK), results.map { it.error })
    }

    @Test
    fun `grouped preview preserves enumeration order across folders and reports invalid provider names`() {
        val files = listOf(
            FileModel(name = "one", reference = "/first/one"),
            FileModel(name = "two", reference = "/second/two")
        )
        val result = BatchRenameEngine.evaluateByFolder(
            files = files,
            rule = BatchRenameRule(enumeration = EnumerationConfig(enabled = true)),
            folderKey = { it.reference.substringBeforeLast('/') },
            unselectedNamesByFolder = emptyMap(),
            nameKey = { it.takeIf { proposed -> proposed.length <= 6 } }
        )

        assertEquals(listOf("one_01", "two_02"), result.map { it.proposedName })
        assertTrue(result.all { it.isValid })
        val invalid = BatchRenameEngine.evaluateByFolder(
            files = files,
            rule = BatchRenameRule(findQuery = "one", replacement = "."),
            folderKey = { it.reference.substringBeforeLast('/') },
            unselectedNamesByFolder = emptyMap(),
            nameKey = { it.takeUnless { proposed -> proposed == "." } }
        )
        assertEquals(BatchRenameError.INVALID_CHARACTERS, invalid.first().error)
        assertTrue(invalid.last().isValid)
    }
}
