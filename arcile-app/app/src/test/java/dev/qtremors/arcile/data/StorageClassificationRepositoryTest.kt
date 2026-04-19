package dev.qtremors.arcile.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.domain.StorageKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StorageClassificationRepositoryTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "datastore/storage_classifications_prefs.preferences_pb").delete()
    }

    @Test
    fun `set and get classification round trip persists metadata`() = runBlocking {
        val repository = StorageClassificationRepository(context)

        repository.setClassification("usb", StorageKind.OTG, lastSeenName = "USB Drive", lastSeenPath = "/storage/usb")

        val classification = repository.getClassification("usb")

        assertNotNull(classification)
        assertEquals(StorageKind.OTG, classification?.assignedKind)
        assertEquals("USB Drive", classification?.lastSeenName)
        assertEquals("/storage/usb", classification?.lastSeenPath)
    }

    @Test
    fun `resetClassification removes stored value`() = runBlocking {
        val repository = StorageClassificationRepository(context)
        repository.setClassification("usb", StorageKind.OTG, lastSeenName = "USB", lastSeenPath = "/storage/usb")

        repository.resetClassification("usb")

        assertNull(repository.getClassification("usb"))
    }
}
