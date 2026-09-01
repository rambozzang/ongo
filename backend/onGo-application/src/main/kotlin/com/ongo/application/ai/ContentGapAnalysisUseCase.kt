package com.ongo.application.ai

import com.ongo.application.ai.dto.ContentGapResponse
import com.ongo.application.ai.dto.ContentOpportunity
import com.ongo.application.ai.dto.OversaturatedTopic
import com.ongo.application.ai.result.ContentGapResult
import com.ongo.application.credit.CreditService
import com.ongo.common.enums.AiFeature
import com.ongo.common.exception.BusinessException
import com.ongo.domain.competitor.CompetitorRepository
import com.ongo.domain.video.VideoRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class ContentGapAnalysisUseCase(
    private val chatClientResolver: ChatClientResolver,
    private val creditService: CreditService,
    private val rateLimiter: AiRateLimiter,
    private val videoRepository: VideoRepository,
    private val competitorRepository: CompetitorRepository,
) {

    private val log = LoggerFactory.getLogger(ContentGapAnalysisUseCase::class.java)

    /**
     * **트랜잭션을 열지 않는다.** LLM 호출을 `@Transactional` 안에 두면 `ai_credits` 행
     * 잠금과 DB 커넥션이 모델 응답 시간만큼 묶인다. 차감·환불의 커밋 경계는
     * [CreditService.withCredits] 가 잡는다.
     */
    fun execute(userId: Long, includeCompetitors: Boolean): ContentGapResponse {
        rateLimiter.checkRateLimit(userId)

        val videos = videoRepository.findByUserId(userId, page = 0, size = 100)
        val userVideosStr = videos.take(20).mapIndexed { i, v ->
            "${i + 1}. ${v.title} (카테고리: ${v.category ?: "미분류"}, 태그: ${v.tags?.joinToString(", ") ?: "없음"})"
        }.joinToString("\n").ifEmpty { "데이터 없음" }

        val competitorStr = if (includeCompetitors) {
            val competitors = competitorRepository.findByUserId(userId)
            competitors.joinToString("\n") { comp ->
                "- ${comp.channelName} (${comp.platform}, 구독자: ${comp.subscriberCount}, 평균 조회수: ${comp.avgViews})"
            }.ifEmpty { "경쟁자 데이터 없음" }
        } else {
            "경쟁자 분석 미포함"
        }

        val userPrompt = PromptTemplates.CONTENT_GAP_USER
            .replace("{userVideos}", InputSanitizer.sanitize(userVideosStr))
            .replace("{competitorData}", InputSanitizer.sanitize(competitorStr))

        return creditService.withCredits(userId, AiFeature.CONTENT_GAP_ANALYSIS) {
            val result = try {
                chatClientResolver.resolve(userId).prompt()
                    .system(PromptTemplates.CONTENT_GAP_SYSTEM)
                    .user(userPrompt)
                    .call()
                    .entity(ContentGapResult::class.java)
                    ?: throw BusinessException("AI_PARSE_ERROR", "AI 응답을 파싱할 수 없습니다")
            } catch (e: BusinessException) {
                throw e
            } catch (e: Exception) {
                log.error("콘텐츠 갭 분석 실패: userId={}", userId, e)
                throw BusinessException("AI_CALL_FAILED", "AI 호출에 실패했습니다: ${e.message}")
            }

            ContentGapResponse(
                opportunities = result.opportunities.map {
                    ContentOpportunity(
                        topic = it.topic,
                        estimatedDemand = it.estimatedDemand,
                        competitionLevel = it.competitionLevel,
                        suggestedAngle = it.suggestedAngle,
                        relevanceScore = it.relevanceScore,
                    )
                },
                oversaturated = result.oversaturated.map {
                    OversaturatedTopic(
                        topic = it.topic,
                        saturationLevel = it.saturationLevel,
                        recommendation = it.recommendation,
                    )
                },
                analyzedAt = LocalDateTime.now().toString(),
            )
        }
    }
}
