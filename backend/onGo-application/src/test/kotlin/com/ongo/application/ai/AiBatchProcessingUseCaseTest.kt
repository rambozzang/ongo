package com.ongo.application.ai

import com.ongo.application.ai.dto.AiBatchOperation
import com.ongo.application.ai.dto.AiBatchRequest
import com.ongo.application.ai.dto.BatchStatus
import com.ongo.application.credit.CreditBalanceInfo
import com.ongo.application.credit.CreditService
import com.ongo.common.exception.ForbiddenException
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoRepository
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AiBatchProcessingUseCaseTest {
    private val meta = mockk<GenerateMetaUseCase>(relaxed = true)
    private val hashtags = mockk<GenerateHashtagsUseCase>(relaxed = true)
    private val stt = mockk<SttUseCase>(relaxed = true)
    private val credits = mockk<CreditService>(relaxed = true)
    private val videos = mockk<VideoRepository>()
    private val repository = mockk<AiBatchRepository>()

    @Test
    fun `batch persists request and item ownership before asynchronous execution`() {
        every { videos.findById(42L) } returns Video(id = 42L, userId = 7L, title = "배치 영상")
        every { credits.getBalance(7L) } returns CreditBalanceInfo(100, 100, 100, 0, LocalDate.now())
        every { repository.save(any(), any()) } answers {
            val response = firstArg<com.ongo.application.ai.dto.AiBatchResponse>()
            val request = secondArg<AiBatchRequest>()
            PersistedAiBatch(response, request.videoIds, request.operation, request.platform)
        }
        every { repository.claimForExecution(any(), any(), any()) } returns null

        val response = useCase().startBatch(
            userId = 7L,
            request = AiBatchRequest(listOf(42L), AiBatchOperation.GENERATE_META),
        )

        assertTrue(response.batchId.isNotBlank())
        assertEquals(BatchStatus.PROCESSING, response.status)
        assertEquals("배치 영상", response.items.single().videoTitle)
    }

    @Test
    fun `batch rejects a video owned by another user before creating a job`() {
        every { videos.findById(42L) } returns Video(id = 42L, userId = 99L, title = "타인 영상")

        assertFailsWith<ForbiddenException> {
            useCase().startBatch(
                userId = 7L,
                request = AiBatchRequest(listOf(42L), AiBatchOperation.STT),
            )
        }
    }

    private fun useCase() = AiBatchProcessingUseCase(
        generateMetaUseCase = meta,
        generateHashtagsUseCase = hashtags,
        sttUseCase = stt,
        creditService = credits,
        videoRepository = videos,
        batchRepository = repository,
    )
}
