package dev.qtremors.arcile.presentation.ui

import dev.qtremors.arcile.core.operation.android.apk.ApkArchiveMetadata
import dev.qtremors.arcile.core.operation.android.apk.ApkUpdateCandidate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArcileUpdatePromptPolicyTest {
    @Test
    fun `new candidate prompts immediately`() {
        assertTrue(
            ArcileUpdatePromptPolicy.shouldPrompt(
                candidateId = "dev.qtremors.arcile:220:signer",
                record = ArcileUpdatePromptRecord(),
                nowMillis = 1_000L
            )
        )
    }

    @Test
    fun `same candidate is rate limited`() {
        val id = "dev.qtremors.arcile:220:signer"
        assertFalse(
            ArcileUpdatePromptPolicy.shouldPrompt(
                candidateId = id,
                record = ArcileUpdatePromptRecord(id, lastPromptAtMillis = 1_000L),
                nowMillis = 2_000L
            )
        )
        assertTrue(
            ArcileUpdatePromptPolicy.shouldPrompt(
                candidateId = id,
                record = ArcileUpdatePromptRecord(id, lastPromptAtMillis = 1_000L),
                nowMillis = 2_001L,
                cooldownMillis = 1_000L
            )
        )
    }

    @Test
    fun `dismissed candidate remains hidden but a newer candidate prompts`() {
        val dismissed = "dev.qtremors.arcile:220:signer"
        assertFalse(
            ArcileUpdatePromptPolicy.shouldPrompt(
                dismissed,
                ArcileUpdatePromptRecord(dismissedCandidateId = dismissed),
                nowMillis = Long.MAX_VALUE
            )
        )
        assertTrue(
            ArcileUpdatePromptPolicy.shouldPrompt(
                "dev.qtremors.arcile:230:signer",
                ArcileUpdatePromptRecord(dismissedCandidateId = dismissed),
                nowMillis = 1L
            )
        )
    }

    @Test
    fun `candidate identity includes application id version and signer`() {
        val release = candidate("dev.qtremors.arcile", 220L)
        val debug = candidate("dev.qtremors.arcile.debug", 220L)
        val newer = candidate("dev.qtremors.arcile", 230L)

        assertNotEquals(
            ArcileUpdatePromptPolicy.candidateId(release),
            ArcileUpdatePromptPolicy.candidateId(debug)
        )
        assertNotEquals(
            ArcileUpdatePromptPolicy.candidateId(release),
            ArcileUpdatePromptPolicy.candidateId(newer)
        )
    }

    private fun candidate(packageName: String, versionCode: Long) = ApkUpdateCandidate(
        metadata = ApkArchiveMetadata(
            packageName = packageName,
            versionName = versionCode.toString(),
            versionCode = versionCode,
            minSdkVersion = 26,
            targetSdkVersion = 36,
            signingDigests = listOf("signer"),
            filePath = "/Download/arcile.apk"
        ),
        installedVersionCode = 200L,
        installedVersionName = "2.0.0",
        isPlugin = false
    )
}
