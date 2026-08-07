package com.ongo.application.subtitleeditor

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ongo.common.enums.MediaType
import com.ongo.common.exception.ForbiddenException
import com.ongo.domain.subtitleeditor.SubtitleEditorRepository
import com.ongo.domain.subtitleeditor.SubtitleTrack
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoRepository
import com.ongo.application.subtitleeditor.dto.CreateSubtitleTrackRequest
import com.ongo.application.subtitleeditor.dto.UpdateSubtitleTrackRequest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal

class SubtitleEditorUseCaseTest {

    private val repository = mockk<SubtitleEditorRepository>(relaxed = true)
    private val videoRepository = mockk<VideoRepository>()
    private val objectMapper = jacksonObjectMapper()
    private val useCase = SubtitleEditorUseCase(repository, videoRepository, objectMapper)

    private val ownedVideo = Video(
        id = 10L,
        userId = 1L,
        title = "서버가 신뢰하는 제목",
        mediaType = MediaType.VIDEO,
    )

    @Test
    fun `create verifies video ownership and ignores client supplied title`() {
        every { videoRepository.findById(10L) } returns ownedVideo
        every { repository.save(any()) } answers { firstArg<SubtitleTrack>().copy(id = 7L) }

        val result = useCase.createSubtitleTrack(
            1L,
            CreateSubtitleTrackRequest(
                videoId = 10L,
                videoTitle = "위조된 제목",
                language = "ko",
                cues = "[{\"start\":0,\"end\":1.5,\"text\":\"안녕하세요\"}]",
                totalDuration = BigDecimal("1.5"),
                wordCount = 1,
            ),
        )

        assertEquals("서버가 신뢰하는 제목", result.videoTitle)
        verify { repository.save(match { it.videoTitle == "서버가 신뢰하는 제목" }) }
    }

    @Test
    fun `create rejects a video owned by another user`() {
        every { videoRepository.findById(10L) } returns ownedVideo

        assertThrows<ForbiddenException> {
            useCase.createSubtitleTrack(2L, CreateSubtitleTrackRequest(videoId = 10L, language = "ko"))
        }
        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `video listing verifies ownership`() {
        every { videoRepository.findById(10L) } returns ownedVideo

        assertThrows<ForbiddenException> { useCase.listSubtitleTracksByVideo(2L, 10L) }
        verify(exactly = 0) { repository.findByVideoId(any()) }
    }

    @Test
    fun `invalid cues are rejected before persistence`() {
        every { videoRepository.findById(10L) } returns ownedVideo

        assertThrows<IllegalArgumentException> {
            useCase.createSubtitleTrack(
                1L,
                CreateSubtitleTrackRequest(videoId = 10L, language = "ko", cues = "not-json"),
            )
        }
        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `cue timestamps must be numeric`() {
        every { videoRepository.findById(10L) } returns ownedVideo

        assertThrows<IllegalArgumentException> {
            useCase.createSubtitleTrack(
                1L,
                CreateSubtitleTrackRequest(
                    videoId = 10L,
                    language = "ko",
                    cues = "[{\"start\":\"not-a-number\",\"end\":1,\"text\":\"잘못된 시간\"}]",
                ),
            )
        }
        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `update validates status and cue ranges`() {
        every { repository.findById(7L) } returns SubtitleTrack(
            id = 7L,
            userId = 1L,
            videoId = 10L,
            language = "ko",
        )

        assertThrows<IllegalArgumentException> {
            useCase.updateSubtitleTrack(
                1L,
                7L,
                UpdateSubtitleTrackRequest(
                    status = "PUBLISHED",
                    cues = "[{\"start\":2,\"end\":1,\"text\":\"잘못된 범위\"}]",
                ),
            )
        }
        verify(exactly = 0) { repository.update(any()) }
    }
}
