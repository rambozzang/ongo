package com.ongo.application.video

import com.ongo.common.exception.ForbiddenException
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoFavoriteRepository
import com.ongo.domain.video.VideoRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class VideoFavoriteUseCaseTest {
    private val videoRepository = mockk<VideoRepository>()
    private val favoriteRepository = mockk<VideoFavoriteRepository>(relaxed = true)
    private val useCase = VideoFavoriteUseCase(videoRepository, favoriteRepository)

    @Test
    fun `다른 사용자의 영상은 즐겨찾기에 추가할 수 없다`() {
        every { videoRepository.findById(7L) } returns Video(id = 7L, userId = 99L, title = "남의 영상")

        assertThrows(ForbiddenException::class.java) { useCase.toggle(1L, 7L) }
        verify(exactly = 0) { favoriteRepository.add(any(), any()) }
    }

    @Test
    fun `소유 영상은 즐겨찾기 토글 결과를 서버 저장소에 반영한다`() {
        every { videoRepository.findById(7L) } returns Video(id = 7L, userId = 1L, title = "내 영상")
        every { favoriteRepository.exists(1L, 7L) } returns false

        val result = useCase.toggle(1L, 7L)

        assertEquals(VideoFavoriteResponse(7L, true), result)
        verify { favoriteRepository.add(1L, 7L) }
    }
}
