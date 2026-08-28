package dev.qtremors.arcile

import androidx.test.core.app.ApplicationProvider
import dagger.hilt.internal.GeneratedComponentManager
import kotlinx.coroutines.cancel
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = ArcileApp::class)
class NativeStorageAuthorizationIntegrationTest {

    @Test
    fun `application component implements native storage authorization entry point`() {
        val application = ApplicationProvider.getApplicationContext<ArcileApp>()
        try {
            val componentManager = requireNotNull(
                GeneratedComponentManager::class.java.cast(application)
            )
            val component = componentManager.generatedComponent()
            val entryPoint = Class.forName(
                "dev.qtremors.arcile.core.ui.NativeStorageAuthorizationEntryPoint"
            )

            assertTrue(
                "The application Hilt component must implement the core UI authorization entry point",
                entryPoint.isInstance(component)
            )
        } finally {
            application.applicationScope.cancel()
        }
    }
}
