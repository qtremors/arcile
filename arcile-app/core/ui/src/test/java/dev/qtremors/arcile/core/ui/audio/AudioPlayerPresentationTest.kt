package dev.qtremors.arcile.core.ui.audio

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.core.storage.domain.AudioTrack
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.testing.ArcileTestTheme
import dev.qtremors.arcile.core.ui.viewer.ViewerFileAction
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AudioPlayerPresentationTest {
    @get:Rule val rule = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun text(id: Int) = context.getString(id)

    @Test
    fun `private source gets the normal player controls without unsupported library actions`() {
        val actions = mutableListOf<ViewerFileAction>()
        showPlayer(actions::add)
        rule.onNodeWithText("Private title").assertExists()
        rule.onNodeWithContentDescription(text(R.string.audio_queue)).performClick()
        rule.onNodeWithText("Private queue").assertExists()
    }

    @Test
    fun `source actions are dispatched through the shared menu while editors and favorites are absent`() {
        val actions = mutableListOf<ViewerFileAction>()
        showPlayer(actions::add)
        rule.onNodeWithContentDescription(text(R.string.audio_more)).performClick()
        rule.onNodeWithText(text(R.string.audio_playback_settings)).assertExists()
        rule.onNodeWithText(text(R.string.audio_sleep_timer)).assertExists()
        rule.onNodeWithText(text(R.string.audio_edit)).assertDoesNotExist()
        rule.onNodeWithText(text(R.string.audio_edit_tags)).assertDoesNotExist()
        rule.onNodeWithText(text(R.string.audio_lyrics)).assertDoesNotExist()
        rule.onNodeWithContentDescription(text(R.string.audio_add_to_favorites)).assertDoesNotExist()
        rule.onNodeWithText(text(R.string.action_copy)).performClick()
        rule.runOnIdle { assertEquals(listOf(ViewerFileAction.Copy), actions) }
    }

    private fun showPlayer(onAction: (ViewerFileAction) -> Unit) {
        rule.setContent {
            ArcileTestTheme {
                AudioNowPlayingScreen(
                    track = AudioTrack(FileModel("song.mp3", "vault:opaque"), "Private title", "Private artist"),
                    playback = AudioPlaybackState(isConnected = true, currentMediaId = "vault:opaque"),
                    onCollapse = {}, onTogglePlayback = {}, onPrevious = {}, onNext = {},
                    onToggleRepeat = {}, onToggleShuffle = {}, onPlaybackParametersChange = { _, _ -> },
                    onSleepTimerChange = {}, onSeek = {}, onShowMetadata = {},
                    artwork = { _, _ -> Text("Private cover") }, queueContent = { Text("Private queue") },
                    viewerActions = setOf(ViewerFileAction.Copy), onViewerAction = onAction,
                    privateSession = true
                )
            }
        }
    }
}
