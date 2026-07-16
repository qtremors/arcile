package dev.qtremors.arcile.core.vault.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.core.vault.crypto.VaultManifestCodec
import dev.qtremors.arcile.core.vault.domain.VaultFailure
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VaultExternalManagerTest {
    private lateinit var context: Context
    private lateinit var directory: File
    private lateinit var manager: VaultExternalManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("onlyfiles_vault_locations", Context.MODE_PRIVATE).edit().clear().commit()
        directory = File(context.cacheDir, "portable-vault-test").apply {
            deleteRecursively()
            mkdirs()
        }
        manager = VaultExternalManager(VaultLocationRegistry(context))
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun `portable vault creates attaches and never overwrites its folder`() {
        val password = "portable secret".toCharArray()
        val created = manager.create(directory.path, "Portable", password)
        assertTrue(password.all { it == '\u0000' })
        assertEquals("Portable", VaultManifestCodec().readPublic(directory).getOrThrow().publicName)
        assertEquals(created.id, manager.attach(directory.path).id)

        val replacementPassword = "replacement".toCharArray()
        val failure = runCatching {
            manager.create(directory.path, "Replacement", replacementPassword)
        }.exceptionOrNull()
        assertTrue(failure is VaultFailure.FolderNotEmpty)
        assertTrue(replacementPassword.all { it == '\u0000' })
        assertEquals("Portable", VaultManifestCodec().readPublic(directory).getOrThrow().publicName)
    }
}
