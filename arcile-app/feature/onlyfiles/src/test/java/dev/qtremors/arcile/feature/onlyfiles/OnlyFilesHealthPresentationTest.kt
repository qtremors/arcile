package dev.qtremors.arcile.feature.onlyfiles

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.core.ui.asString
import dev.qtremors.arcile.core.vault.domain.VaultHealthIssue
import dev.qtremors.arcile.core.vault.domain.VaultHealthSeverity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OnlyFilesHealthPresentationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `health issues use distinct translated categories without exposing backend object identities`() {
        val codes = listOf("header_identity", "header_damage", "pending_transaction", "directory_cycle",
            "manifest_damage", "object_damage", "missing_object", "unknown")
        val messages = codes.map {
            vaultHealthIssueText(VaultHealthIssue(VaultHealthSeverity.ERROR, it, "secret-object", "secret diagnosis"), 0).asString(context)
        }
        assertEquals(codes.size, messages.toSet().size)
        messages.forEach {
            assertTrue(it.isNotBlank())
            assertFalse(it.contains("secret"))
        }
    }

    @Test
    fun `orphan counts preserve singular and plural quantities`() {
        val issue = VaultHealthIssue(VaultHealthSeverity.WARNING, "orphan_objects", message = "backend detail")
        assertEquals(context.resources.getQuantityString(R.plurals.onlyfiles_health_orphans, 1, 1), vaultHealthIssueText(issue, 1).asString(context))
        assertEquals(context.resources.getQuantityString(R.plurals.onlyfiles_health_orphans, 5, 5), vaultHealthIssueText(issue, 5).asString(context))
    }
}
