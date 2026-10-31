package com.ongo.application.keyword

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ongo.application.ai.AiRateLimiter
import com.ongo.application.ai.ChatClientResolver
import com.ongo.application.ai.estimate.passThroughCredits
import com.ongo.application.ai.estimate.stubChatEntity
import com.ongo.application.ai.result.KeywordResearchResult
import com.ongo.application.credit.CreditService
import com.ongo.application.keyword.dto.KeywordResearchRequest
import com.ongo.common.enums.AiFeature
import com.ongo.domain.keyword.KeywordResearch
import com.ongo.domain.keyword.KeywordResearchRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 키워드 리서치: 등급은 AI 추정으로 표시하고, 정해진 등급이 아닌 값(숫자 등)은 "판단 못 함"으로 본다. */
class KeywordResearchUseCaseTest {

    private val repository = mockk<KeywordResearchRepository>()
    private val chatClientResolver = mockk<ChatClientResolver>()
    private val creditService = mockk<CreditService>()
    private val rateLimiter = mockk<AiRateLimiter>()
    private val useCase = KeywordResearchUseCase(repository, chatClientResolver, creditService, rateLimiter, jacksonObjectMapper())

    @Test
    fun `등급만 받고 숫자 점수는 내지 않으며 추정 근거를 붙인다`() {
        every { rateLimiter.checkRateLimit(7) } just runs
        passThroughCredits(creditService, 7, AiFeature.KEYWORD_RESEARCH)
        every { repository.save(any()) } answers { firstArg<KeywordResearch>().copy(id = 3) }
        val prompt = stubChatEntity(
            chatClientResolver, 7, KeywordResearchResult::class.java,
            KeywordResearchResult(
                keyword = "재테크",
                platforms = listOf(
                    KeywordResearchResult.PlatformKeywordAnalysis("YOUTUBE", searchVolume = "high", competition = "MEDIUM", trend = "RISING"),
                    KeywordResearchResult.PlatformKeywordAnalysis("TIKTOK", searchVolume = "월 12만", competition = null, trend = "boom"),
                ),
                overallOpportunity = "요약",
                suggestions = listOf("제안"),
            ),
        )

        val response = useCase.research(7, KeywordResearchRequest(keyword = "재테크"))

        val youtube = response.platforms[0]
        assertEquals(Triple("HIGH", "MEDIUM", "RISING"), Triple(youtube.searchVolume, youtube.competition, youtube.trend))
        val tiktok = response.platforms[1]
        assertNull(tiktok.searchVolume) // "월 12만" 같은 지어낸 수치는 받지 않는다
        assertNull(tiktok.competition)
        assertNull(tiktok.trend)
        assertTrue(response.aiEstimate.estimated)
        assertTrue(response.aiEstimate.basis.contains("실제 검색 데이터 없이"))
        assertEquals(AiFeature.KEYWORD_RESEARCH.creditCost, response.creditsUsed)
        assertTrue(prompt.captured.contains("재테크"))
    }
}
