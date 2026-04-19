package com.ongo.application.keyword

import com.fasterxml.jackson.databind.ObjectMapper
import com.ongo.application.ai.AiRateLimiter
import com.ongo.application.ai.ChatClientResolver
import com.ongo.application.ai.PromptTemplates
import com.ongo.application.ai.result.KeywordResearchResult
import com.ongo.application.credit.CreditService
import com.ongo.application.keyword.dto.*
import com.ongo.common.enums.AiFeature
import com.ongo.common.exception.BusinessException
import com.ongo.domain.keyword.KeywordResearch
import com.ongo.domain.keyword.KeywordResearchRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class KeywordResearchUseCase(
    private val keywordResearchRepository: KeywordResearchRepository,
    private val chatClientResolver: ChatClientResolver,
    private val creditService: CreditService,
    private val rateLimiter: AiRateLimiter,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun research(userId: Long, request: KeywordResearchRequest): KeywordResearchResponse {
        rateLimiter.checkRateLimit(userId)
        creditService.validateAndDeduct(userId, AiFeature.KEYWORD_RESEARCH)

        val userPrompt = PromptTemplates.KEYWORD_RESEARCH_USER
            .replace("{keyword}", request.keyword)
            .replace("{platforms}", request.platforms.joinToString(", "))
            .replace("{category}", request.category)

        return try {
            val result = chatClientResolver.resolve(userId).prompt()
                .system(PromptTemplates.KEYWORD_RESEARCH_SYSTEM)
                .user(userPrompt)
                .call()
                .entity(KeywordResearchResult::class.java)
                ?: throw BusinessException("AI_PARSE_ERROR", "AI 응답을 파싱할 수 없습니다")

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
        } catch (e: BusinessException) {
            throw e
        } catch (e: Exception) {
            log.error("키워드 리서치 AI 호출 실패, 크레딧 환불: userId={}", userId, e)
            creditService.refundCredit(userId, AiFeature.KEYWORD_RESEARCH.creditCost, AiFeature.KEYWORD_RESEARCH.name)
            throw BusinessException("AI_CALL_FAILED", "AI 키워드 분석에 실패했습니다: ${e.message}")
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
                searchVolume = it.searchVolume,
                competition = it.competition,
                trend = it.trend,
                opportunityScore = it.opportunityScore,
                relatedKeywords = it.relatedKeywords,
            )
        },
        overallOpportunity = result.overallOpportunity,
        suggestions = result.suggestions,
        createdAt = saved.createdAt.toString(),
    )

    private fun toSummary(research: KeywordResearch) = KeywordResearchSummary(
        id = research.id!!,
        keyword = research.keyword,
        platforms = research.platforms.split(","),
        createdAt = research.createdAt.toString(),
    )
}
