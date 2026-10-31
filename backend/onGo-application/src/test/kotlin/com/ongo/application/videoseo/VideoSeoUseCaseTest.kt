package com.ongo.application.videoseo

import com.ongo.application.ai.AiRateLimiter
import com.ongo.application.ai.ChatClientResolver
import com.ongo.application.ai.estimate.passThroughCredits
import com.ongo.application.ai.estimate.stubChatEntity
import com.ongo.application.ai.estimate.stubChatFailure
import com.ongo.application.ai.result.VideoSeoScoreResult
import com.ongo.application.credit.CreditService
import com.ongo.common.enums.AiFeature
import com.ongo.common.enums.Platform
import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.ForbiddenException
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.video.VideoUpload
import com.ongo.domain.video.VideoUploadRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * SEO 점검: 점수는 규칙(실측)이고 AI 는 제안만 한다. 화면 계약(categories·suggestions·creditsUsed)도 고정한다 —
 * 예전에는 서버가 다른 필드명으로 보내 화면이 결과를 그리지 못했다.
 */
class VideoSeoUseCaseTest {

    private val videoRepository = mockk<VideoRepository>()
    private val uploadRepository = mockk<VideoUploadRepository>()
    private val chatClientResolver = mockk<ChatClientResolver>()
    private val creditService = mockk<CreditService>()
    private val rateLimiter = mockk<AiRateLimiter>()
    private val useCase = VideoSeoUseCase(videoRepository, uploadRepository, chatClientResolver, creditService, rateLimiter)

    private val video = Video(id = 9, userId = 7, title = "오늘 있었던 일", description = "짧은 설명", tags = listOf("일상"), category = null)

    @BeforeEach
    fun setUp() {
        every { videoRepository.findById(9) } returns video
        every { uploadRepository.findByVideoId(9) } returns listOf(VideoUpload(videoId = 9, platform = Platform.YOUTUBE))
        every { rateLimiter.checkRateLimit(7) } just runs
        passThroughCredits(creditService, 7, AiFeature.VIDEO_SEO_SCORE)
    }

    @Test
    fun `점수는 규칙으로 매기고 AI 에게 점수를 묻지 않는다 — 통과 못 한 항목을 근거로 넘긴다`() {
        val prompt = stubChatEntity(
            chatClientResolver, 7, VideoSeoScoreResult::class.java,
            VideoSeoScoreResult(suggestions = listOf(" 제목에 '일상' 을 넣으세요 ", ""), relatedKeywords = listOf("#브이로그", "브이로그", "일기")),
        )

        val response = useCase.analyzeVideoSeo(7, 9)

        val expected = SeoRubric.evaluate(video.title, video.description, video.tags, video.category, listOf(Platform.YOUTUBE))
        assertEquals(expected.overallScore, response.overallScore)
        assertEquals(listOf("title", "description", "tags", "general"), response.categories.map { it.key })
        assertEquals(expected.categories.map { it.score }, response.categories.map { it.score })
        assertTrue(response.categories.all { c -> c.checks.sumOf { it.maxPoints } == c.maxScore })
        // 실패 항목이 실제 값과 함께 프롬프트에 들어간다
        assertTrue(prompt.captured.contains("- 설명 100자 이상: 5자"))
        assertTrue(prompt.captured.contains("- 카테고리 지정: 미분류"))
        // AI 몫: 공백 정리, 빈 제안 제거, 키워드 # 제거·중복 제거
        assertEquals(listOf("제목에 '일상' 을 넣으세요"), response.suggestions)
        assertEquals(listOf("브이로그", "일기"), response.relatedKeywords)
        assertTrue(response.aiEstimate.estimated)
        assertTrue(response.scoreBasis.contains("AI 가 매기지 않았습니다"))
        assertEquals(AiFeature.VIDEO_SEO_SCORE.creditCost, response.creditsUsed)
    }

    @Test
    fun `남의 영상이면 차감 전에 거절한다`() {
        every { videoRepository.findById(9) } returns video.copy(userId = 99)

        assertFailsWith<ForbiddenException> { useCase.analyzeVideoSeo(7, 9) }
        verify(exactly = 0) { creditService.withCredits(any(), any<AiFeature>(), any<() -> Any>()) }
    }

    @Test
    fun `AI 호출이 실패하면 AI_CALL_FAILED 로 끝난다 — 차감 경로 안에서 던져 환불된다`() {
        stubChatFailure(chatClientResolver, 7, RuntimeException("timeout"))

        val error = assertFailsWith<BusinessException> { useCase.analyzeVideoSeo(7, 9) }
        assertEquals("AI_CALL_FAILED", error.code)
        verify(exactly = 1) { creditService.withCredits(7, AiFeature.VIDEO_SEO_SCORE, any<() -> Any>()) }
    }

    @Test
    fun `AI 응답을 읽지 못하면 AI_PARSE_ERROR`() {
        stubChatEntity(chatClientResolver, 7, VideoSeoScoreResult::class.java, null)

        assertEquals("AI_PARSE_ERROR", assertFailsWith<BusinessException> { useCase.analyzeVideoSeo(7, 9) }.code)
    }
}
