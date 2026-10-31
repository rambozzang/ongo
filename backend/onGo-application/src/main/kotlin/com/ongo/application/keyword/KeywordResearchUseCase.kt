package com.ongo.application.keyword

import com.fasterxml.jackson.databind.ObjectMapper
import com.ongo.application.ai.AiRateLimiter
import com.ongo.application.ai.ChatClientResolver
import com.ongo.application.ai.PromptTemplates
import com.ongo.application.ai.estimate.AiEstimate
import com.ongo.application.ai.result.KeywordResearchResult
import com.ongo.application.credit.CreditService
import com.ongo.application.keyword.dto.*
import com.ongo.common.enums.AiFeature
import com.ongo.common.exception.BusinessException
import com.ongo.domain.keyword.KeywordResearch
import com.ongo.domain.keyword.KeywordResearchRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class KeywordResearchUseCase(
    private val keywordResearchRepository: KeywordResearchRepository,
    private val chatClientResolver: ChatClientResolver,
    private val creditService: CreditService,
    private val rateLimiter: AiRateLimiter,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * **트랜잭션을 열지 않는다.** LLM 호출을 `@Transactional` 안에 두면 `ai_credits` 행
     * 잠금과 DB 커넥션이 모델 응답 시간만큼 묶인다. 차감·환불의 커밋 경계는
     * [CreditService.withCredits] 가 잡고, 결과 저장은 호출 이후 짧게 끝난다.
     */
    fun research(userId: Long, request: KeywordResearchRequest): KeywordResearchResponse {
        rateLimiter.checkRateLimit(userId)

        val userPrompt = PromptTemplates.KEYWORD_RESEARCH_USER
            .replace("{keyword}", request.keyword)
            .replace("{platforms}", request.platforms.joinToString(", "))
            .replace("{category}", request.category)

        return creditService.withCredits(userId, AiFeature.KEYWORD_RESEARCH) {
            val result = try {
                chatClientResolver.resolve(userId).prompt()
                    .system(PromptTemplates.KEYWORD_RESEARCH_SYSTEM)
                    .user(userPrompt)
                    .call()
                    .entity(KeywordResearchResult::class.java)
                    ?: throw BusinessException("AI_PARSE_ERROR", "AI 응답을 파싱할 수 없습니다")
            } catch (e: BusinessException) {
                throw e
            } catch (e: Exception) {
                log.error("키워드 리서치 AI 호출 실패: userId={}", userId, e)
                throw BusinessException("AI_CALL_FAILED", "AI 키워드 분석에 실패했습니다: ${e.message}")
            }

            val resultJson = objectMapper.writeValueAsString(result)

            val saved = keywordResearchRepository.save(
                KeywordResearch(
                    userId = userId,
                    keyword = result.keyword,
                    platforms = request.platforms.joinToString(","),
                    resultJson = resultJson,
                )
            )

            toResponse(saved, result)
        }
    }

    fun getHistory(userId: Long, page: Int, size: Int): KeywordResearchHistoryResponse {
        val items = keywordResearchRepository.findByUserId(userId, page, size)
        val total = keywordResearchRepository.countByUserId(userId)
        return KeywordResearchHistoryResponse(
            items = items.map { toSummary(it) },
            totalCount = total,
            page = page,
            size = size,
        )
    }

    private fun toResponse(saved: KeywordResearch, result: KeywordResearchResult) = KeywordResearchResponse(
        id = saved.id!!,
        keyword = result.keyword,
        platforms = result.platforms.map {
            PlatformKeywordAnalysisDto(
                platform = it.platform,
                searchVolume = it.searchVolume?.let { v -> grade(v, LEVELS) },
                competition = it.competition?.let { v -> grade(v, LEVELS) },
                trend = it.trend?.let { v -> grade(v, TRENDS) },
                relatedKeywords = it.relatedKeywords,
            )
        },
        overallOpportunity = result.overallOpportunity,
        suggestions = result.suggestions,
        aiEstimate = AiEstimate(basis = BASIS),
        creditsUsed = AiFeature.KEYWORD_RESEARCH.creditCost,
        createdAt = saved.createdAt.toString(),
    )

    companion object {
        const val BASIS =
            "검색량·경쟁도·추세는 실제 검색 데이터 없이 AI 모델의 일반 지식으로 추정한 등급입니다. 플랫폼 공식 수치가 아닙니다."
        private val LEVELS = setOf("HIGH", "MEDIUM", "LOW")
        private val TRENDS = setOf("RISING", "STABLE", "DECLINING")

        /** 정해진 등급만 받는다. 모델이 숫자·문장을 넣었으면 "판단 못 함"(null)으로 본다. */
        fun grade(raw: String, allowed: Set<String>): String? = raw.trim().uppercase().takeIf { it in allowed }
    }

    private fun toSummary(research: KeywordResearch) = KeywordResearchSummary(
        id = research.id!!,
        keyword = research.keyword,
        platforms = research.platforms.split(","),
        createdAt = research.createdAt.toString(),
    )
}
