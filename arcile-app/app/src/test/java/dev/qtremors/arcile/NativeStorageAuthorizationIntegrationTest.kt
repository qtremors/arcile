package dev.qtremors.arcile

import dagger.hilt.internal.GeneratedComponentManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NativeStorageAuthorizationIntegrationTest {

    @Test
    fun `application component implements native storage authorization entry point`() {
        val application = ApplicationProvider.getApplicationContext<ArcileApp>()
        val component = (application as GeneratedComponentManager<*>).generatedComponent()
        val entryPoint = Class.forName(
            "dev.qtremors.arcile.core.ui.NativeStorageAuthorizationEntryPoint"
        )

        assertTrue(
            "The application Hilt component must implement the core UI authorization entry point",
            entryPoint.isInstance(component)
        )
    }
}
