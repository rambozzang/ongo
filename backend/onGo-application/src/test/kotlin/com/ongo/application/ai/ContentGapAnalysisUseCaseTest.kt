package com.ongo.application.ai

import com.ongo.application.ai.estimate.passThroughCredits
import com.ongo.application.ai.estimate.stubChatEntity
import com.ongo.application.ai.result.ContentGapResult
import com.ongo.application.credit.CreditService
import com.ongo.common.enums.AiFeature
import com.ongo.common.exception.BusinessException
import com.ongo.domain.competitor.Competitor
import com.ongo.domain.competitor.CompetitorRepository
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 콘텐츠 갭: 관련성 점수는 없고, 수요·경쟁은 AI 추정으로 표시하며, 볼 영상이 없으면 청구하지 않는다. */
class ContentGapAnalysisUseCaseTest {

    private val chatClientResolver = mockk<ChatClientResolver>()
    private val creditService = mockk<CreditService>()
    private val rateLimiter = mockk<AiRateLimiter>()
    private val videoRepository = mockk<VideoRepository>()
    private val competitorRepository = mockk<CompetitorRepository>()
    private val useCase = ContentGapAnalysisUseCase(chatClientResolver, creditService, rateLimiter, videoRepository, competitorRepository)

    @BeforeEach
    fun setUp() {
        every { rateLimiter.checkRateLimit(7) } just runs
        passThroughCredits(creditService, 7, AiFeature.CONTENT_GAP_ANALYSIS)
        every { competitorRepository.findByUserId(7) } returns listOf(
            Competitor(userId = 7, platform = "YOUTUBE", platformChannelId = "UC1", channelName = "경쟁 채널", subscriberCount = 1000),
        )
    }

    @Test
    fun `영상이 하나도 없으면 AI 를 부르지 않고 크레딧도 받지 않는다`() {
        every { videoRepository.findByUserId(7, 0, 100) } returns emptyList()

        val error = assertFailsWith<BusinessException> { useCase.execute(7, includeCompetitors = true) }

        assertEquals("CONTENT_GAP_NO_DATA", error.code)
        verify(exactly = 0) { creditService.withCredits(any(), any<AiFeature>(), any<() -> Any>()) }
        verify(exactly = 0) { chatClientResolver.resolve(any()) }
    }

    @Test
    fun `등급이 아닌 수요·경쟁 값은 판단 못 함으로 두고, 무엇을 보고 추정했는지 실측 개수로 말한다`() {
        every { videoRepository.findByUserId(7, 0, 100) } returns (1..3).map { Video(id = it.toLong(), userId = 7, title = "영상 $it") }
        stubChatEntity(
            chatClientResolver, 7, ContentGapResult::class.java,
            ContentGapResult(
                opportunities = listOf(
                    ContentGapResult.ContentOpportunityResult("주제A", estimatedDemand = "high", competitionLevel = "LOW", suggestedAngle = "각도"),
                    ContentGapResult.ContentOpportunityResult("주제B", estimatedDemand = "월 5만 회", competitionLevel = null, suggestedAngle = ""),
                ),
                oversaturated = listOf(ContentGapResult.OversaturatedTopicResult("주제C", "medium", "차별화")),
            ),
        )

        val response = useCase.execute(7, includeCompetitors = true)

        assertEquals("HIGH", response.opportunities[0].estimatedDemand)
        assertEquals("LOW", response.opportunities[0].competitionLevel)
        assertNull(response.opportunities[1].estimatedDemand) // 지어낸 수치는 받지 않는다
        assertNull(response.opportunities[1].competitionLevel)
        assertEquals("MEDIUM", response.oversaturated.single().saturationLevel)
        assertEquals(3, response.analyzedVideoCount)
        assertEquals(
            "내 영상 3개의 제목·카테고리·태그와 경쟁 채널 1개의 이름·구독자·평균 조회수만 보고 AI 가 추정했습니다. 검색량·시장 데이터는 보지 않았습니다.",
            response.aiEstimate.basis,
        )
        assertTrue(response.aiEstimate.estimated)
        assertEquals(AiFeature.CONTENT_GAP_ANALYSIS.creditCost, response.creditsUsed)
    }

    @Test
    fun `경쟁 채널을 빼면 근거에도 넣지 않는다`() {
        every { videoRepository.findByUserId(7, 0, 100) } returns listOf(Video(id = 1, userId = 7, title = "영상"))
        stubChatEntity(chatClientResolver, 7, ContentGapResult::class.java, ContentGapResult(emptyList(), emptyList()))

        val response = useCase.execute(7, includeCompetitors = false)

        assertEquals("내 영상 1개의 제목·카테고리·태그만 보고 AI 가 추정했습니다. 검색량·시장 데이터는 보지 않았습니다.", response.aiEstimate.basis)
        verify(exactly = 0) { competitorRepository.findByUserId(any()) }
    }
}
