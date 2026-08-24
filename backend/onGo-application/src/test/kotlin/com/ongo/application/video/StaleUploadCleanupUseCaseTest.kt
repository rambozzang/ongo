package com.ongo.application.video

import com.ongo.common.enums.UploadStatus
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoRepository
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals

/**
 * 방치된 업로드 회수.
 *
 * confirm 이 오지 않은 UPLOADING 행은 사용자가 더 이상 완료할 수 없는데(URL 만료), 이미 올라간
 * 오브젝트는 계속 과금되고 예약분이 사용자 쿼터를 영구히 물고 있게 된다. 반대로 정상 업로드나
 * 아직 진행 중일 수 있는 최신 건을 지우면 사용자의 파일을 잃는다.
 */
class StaleUploadCleanupUseCaseTest {

    private val videoRepository = mockk<VideoRepository>()
    private val storageService = mockk<StorageService>(relaxed = true)

    private lateinit var useCase: StaleUploadCleanupUseCase

    private val now = LocalDateTime.of(2026, 8, 22, 12, 0)

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        useCase = StaleUploadCleanupUseCase(
            videoRepository, storageService,
            staleAfterMinutes = 180L, batchSize = 200,
        )
    }

    private fun stale(id: Long, userId: Long = 100L) = Video(
        id = id,
        userId = userId,
        title = "방치된 업로드",
        fileSizeBytes = 2_000L,
        status = UploadStatus.UPLOADING,
    )

    @Test
    fun `reclaims stale uploads and asks the repository only for expired UPLOADING rows`() {
        every { videoRepository.findStaleUploading(any(), any()) } returns listOf(stale(1L), stale(2L))
        every { videoRepository.delete(any()) } just runs

        val reclaimed = useCase.cleanupStaleUploads(now)

        assertEquals(2, reclaimed)
        // 임계 시각은 설정값만큼 과거여야 한다 — 진행 중인 업로드를 끊지 않기 위한 여유다.
        verify(exactly = 1) { videoRepository.findStaleUploading(now.minusMinutes(180L), 200) }
        verify(exactly = 1) { storageService.deleteFile(1L) }
        verify(exactly = 1) { storageService.deleteFile(2L) }
        verify(exactly = 1) { videoRepository.delete(1L) }
        verify(exactly = 1) { videoRepository.delete(2L) }
    }

    /*
     * 스토리지 삭제가 실패했는데 행을 지우면 과금되는 오브젝트를 아무도 못 찾는다.
     * 행을 남겨 다음 주기가 다시 시도하게 한다.
     */
    @Test
    fun `keeps the row when storage deletion fails so the orphan stays trackable`() {
        every { videoRepository.findStaleUploading(any(), any()) } returns listOf(stale(1L))
        every { storageService.deleteFile(1L) } throws IllegalStateException("스토리지 장애")

        val reclaimed = useCase.cleanupStaleUploads(now)

        assertEquals(0, reclaimed)
        verify(exactly = 0) { videoRepository.delete(any()) }
    }

    @Test
    fun `one failing item does not stop the rest of the batch`() {
        every { videoRepository.findStaleUploading(any(), any()) } returns listOf(stale(1L), stale(2L))
        every { storageService.deleteFile(1L) } throws IllegalStateException("스토리지 장애")
        every { videoRepository.delete(2L) } just runs

        val reclaimed = useCase.cleanupStaleUploads(now)

        assertEquals(1, reclaimed)
        verify(exactly = 0) { videoRepository.delete(1L) }
        verify(exactly = 1) { videoRepository.delete(2L) }
    }

    @Test
    fun `does nothing when there is no stale upload`() {
        every { videoRepository.findStaleUploading(any(), any()) } returns emptyList()

        assertEquals(0, useCase.cleanupStaleUploads(now))

        verify(exactly = 0) { storageService.deleteFile(any()) }
        verify(exactly = 0) { videoRepository.delete(any()) }
    }
}
