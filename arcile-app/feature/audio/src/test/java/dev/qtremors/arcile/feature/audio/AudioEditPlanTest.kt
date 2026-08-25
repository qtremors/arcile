package dev.qtremors.arcile.feature.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioEditPlanTest {
    @Test
    fun extractJoinsMultipleRangesInChronologicalOrder() {
        val result = AudioEditPlanner.create(
            sources = listOf(AudioEditSource("/music/source.m4a", 12_000)),
            mode = AudioEditMode.EXTRACT,
            selectionRanges = listOf(
                AudioEditRange(8_000, 10_000),
                AudioEditRange(1_000, 3_000)
            ),
            outputPath = "/music/output.m4a"
        ) as AudioEditPlanResult.Ready

        assertEquals(
            listOf(
                AudioEditSegment("/music/source.m4a", 1_000, 3_000),
                AudioEditSegment("/music/source.m4a", 8_000, 10_000)
            ),
            result.plan.segments
        )
    }

    @Test
    fun removeMergesOverlappingRangesAndKeepsRemainingAudio() {
        val result = AudioEditPlanner.create(
            sources = listOf(AudioEditSource("/music/source.wav", 12_000)),
            mode = AudioEditMode.REMOVE,
            selectionRanges = listOf(
                AudioEditRange(2_000, 5_000),
                AudioEditRange(4_000, 7_000),
                AudioEditRange(9_000, 10_000)
            ),
            outputPath = "/music/output.wav"
        ) as AudioEditPlanResult.Ready

        assertEquals(
            listOf(
                AudioEditSegment("/music/source.wav", 0, 2_000),
                AudioEditSegment("/music/source.wav", 7_000, 9_000),
                AudioEditSegment("/music/source.wav", 10_000, 12_000)
            ),
            result.plan.segments
        )
    }

    @Test
    fun `extract keeps only the selected range`() {
        val result = AudioEditPlanner.create(
            sources = listOf(AudioEditSource("/music/song.m4a", 60_000)),
            mode = AudioEditMode.EXTRACT,
            selectionStartMs = 12_000,
            selectionEndMs = 24_000,
            outputPath = "/music/result.m4a"
        )

        val plan = (result as AudioEditPlanResult.Ready).plan
        assertEquals(
            listOf(AudioEditSegment("/music/song.m4a", 12_000, 24_000)),
            plan.segments
        )
    }

    @Test
    fun `remove joins the audio around the selected range`() {
        val result = AudioEditPlanner.create(
            sources = listOf(AudioEditSource("/music/song.wav", 60_000)),
            mode = AudioEditMode.REMOVE,
            selectionStartMs = 12_000,
            selectionEndMs = 24_000,
            outputPath = "/music/result.wav"
        )

        val plan = (result as AudioEditPlanResult.Ready).plan
        assertEquals(
            listOf(
                AudioEditSegment("/music/song.wav", 0, 12_000),
                AudioEditSegment("/music/song.wav", 24_000, 60_000)
            ),
            plan.segments
        )
    }

    @Test
    fun `combine preserves the selected order`() {
        val result = AudioEditPlanner.create(
            sources = listOf(
                AudioEditSource("/music/second.aac", 20_000),
                AudioEditSource("/music/first.aac", 10_000)
            ),
            mode = AudioEditMode.COMBINE,
            outputPath = "/music/result.aac"
        )

        val plan = (result as AudioEditPlanResult.Ready).plan
        assertEquals(listOf("/music/second.aac", "/music/first.aac"), plan.segments.map { it.path })
    }

    @Test
    fun `mixed containers are rejected for a lossless combine`() {
        val result = AudioEditPlanner.create(
            sources = listOf(
                AudioEditSource("/music/first.m4a", 20_000),
                AudioEditSource("/music/second.wav", 10_000)
            ),
            mode = AudioEditMode.COMBINE
        )

        assertTrue(result is AudioEditPlanResult.MixedFormats)
    }

    @Test
    fun `lossy formats without a safe muxer are rejected`() {
        val result = AudioEditPlanner.create(
            sources = listOf(AudioEditSource("/music/song.mp3", 60_000)),
            mode = AudioEditMode.EXTRACT,
            selectionStartMs = 0,
            selectionEndMs = 10_000
        )

        assertEquals(AudioEditPlanResult.UnsupportedFormat("mp3"), result)
    }
}
