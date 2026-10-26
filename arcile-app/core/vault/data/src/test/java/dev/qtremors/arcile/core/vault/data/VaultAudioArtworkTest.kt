package dev.qtremors.arcile.core.vault.data

import android.graphics.Bitmap
import android.graphics.Color
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBitmapFactory

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VaultAudioArtworkTest {
    @Before fun rejectInvalidImageData() { ShadowBitmapFactory.setAllowInvalidImageData(false) }
    @After fun resetBitmapSimulation() { ShadowBitmapFactory.reset() }

    @Test
    fun `large embedded covers are sampled before encoding into the encrypted cache`() {
        val original = Bitmap.createBitmap(1_024, 512, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val bytes = ByteArrayOutputStream().use {
            original.compress(Bitmap.CompressFormat.PNG, 100, it)
            it.toByteArray()
        }
        original.recycle()
        val decoded = decodeEmbeddedAudioArtwork(bytes, 128)
        assertTrue(decoded.width <= 256 && decoded.height <= 256)
        assertEquals(Color.RED, decoded.getPixel(0, 0))
        decoded.recycle()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `oversized embedded covers are rejected before bitmap allocation`() {
        decodeEmbeddedAudioArtwork(ByteArray(8 * 1024 * 1024 + 1), 128)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `corrupt embedded covers fail without a file fallback`() {
        decodeEmbeddedAudioArtwork(byteArrayOf(1, 2, 3), 128)
    }
}
