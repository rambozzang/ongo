package com.ongo.application.ai

import com.ongo.application.credit.CreditService
import com.ongo.domain.ai.AiPipeline
import com.ongo.domain.ai.AiPipelineRepository
import com.ongo.domain.ai.AiPipelineStep
import com.ongo.domain.ai.PipelineStatus
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class AiPipelineUseCaseTest {
    private val stt = mockk<SttUseCase>(relaxed = true)
    private val analysis = mockk<AnalyzeScriptUseCase>(relaxed = true)
    private val meta = mockk<GenerateMetaUseCase>(relaxed = true)
    private val hashtags = mockk<GenerateHashtagsUseCase>(relaxed = true)
    private val schedule = mockk<SuggestScheduleUseCase>(relaxed = true)
    private val credits = mockk<CreditService>(relaxed = true)
    private val videos = mockk<VideoRepository>()
    private val repository = mockk<AiPipelineRepository>()

    @Test
    fun `start persists pipeline with channel before asynchronous execution`() {
        every { videos.findById(42L) } returns Video(id = 42L, userId = 7L, title = "테스트 영상")
        every { repository.save(any()) } answers { firstArg() }
        every { repository.claimForExecution(any(), any(), any()) } returns null

        val pipeline = useCase().startPipeline(
            userId = 7L,
            videoId = 42L,
            stepNames = listOf(AiPipelineStep.SUGGEST_SCHEDULE.name),
            channelId = 99L,
        )

        assertEquals(PipelineStatus.PENDING, pipeline.status)
        assertEquals(99L, pipeline.channelId)
        verify { repository.save(match { it.id == pipeline.id && it.channelId == 99L }) }
    }

    @Test
    fun `status lookup reads durable repository instead of process memory`() {
        val persisted = AiPipeline(
            id = "persisted",
            userId = 7L,
            videoId = 42L,
            steps = listOf(AiPipelineStep.GENERATE_META),
            status = PipelineStatus.RUNNING,
        )
        every { repository.findById("persisted") } returns persisted

        assertSame(persisted, useCase().getPipelineStatus(7L, "persisted"))
    }

    private fun useCase() = AiPipelineUseCase(
        sttUseCase = stt,
        analyzeScriptUseCase = analysis,
        generateMetaUseCase = meta,
        generateHashtagsUseCase = hashtags,
        suggestScheduleUseCase = schedule,
        creditService = credits,
        videoRepository = videos,
        pipelineRepository = repository,
    )
}
