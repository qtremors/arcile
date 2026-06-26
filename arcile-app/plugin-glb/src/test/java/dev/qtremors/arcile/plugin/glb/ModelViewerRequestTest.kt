package dev.qtremors.arcile.plugin.glb

import android.net.Uri
import dev.qtremors.arcile.plugin.api.PluginContract
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ModelViewerRequestTest {
    private val contentUri = Uri.parse("content://example/model")

    @Test
    fun `accepts contract action with supported mime or extension`() {
        assertTrue(
            isSupportedPluginModelRequest(
                PluginContract.ACTION_VIEW_FILE,
                contentUri,
                MODEL_GLB_MIME_TYPE,
                "model"
            )
        )
        assertTrue(
            isSupportedPluginModelRequest(
                PluginContract.ACTION_VIEW_FILE,
                contentUri,
                "application/octet-stream",
                "model.GLB"
            )
        )
    }

    @Test
    fun `rejects invalid action scheme and unsupported file`() {
        assertFalse(isSupportedPluginModelRequest(IntentActionView, contentUri, MODEL_GLB_MIME_TYPE, "model.glb"))
        assertFalse(
            isSupportedPluginModelRequest(
                PluginContract.ACTION_VIEW_FILE,
                Uri.parse("file:///storage/model.glb"),
                MODEL_GLB_MIME_TYPE,
                "model.glb"
            )
        )
        assertFalse(
            isSupportedPluginModelRequest(
                PluginContract.ACTION_VIEW_FILE,
                contentUri,
                "text/plain",
                "notes.txt"
            )
        )
    }

    private companion object {
        const val IntentActionView = "android.intent.action.VIEW"
    }
}
