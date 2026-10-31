package com.ongo.application.ai

import com.ongo.application.ai.dto.ContentGapResponse
import com.ongo.application.ai.dto.ContentOpportunity
import com.ongo.application.ai.dto.OversaturatedTopic
import com.ongo.application.ai.estimate.AiEstimate
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
        // 볼 영상이 없으면 "갭"을 말할 근거가 없다. 모델은 빈 입력에도 그럴듯한 기회를 지어내므로 청구 전에 거절한다.
        if (videos.isEmpty()) {
            throw BusinessException(
                "CONTENT_GAP_NO_DATA",
                "분석할 영상이 없습니다. 영상을 올린 뒤 다시 시도해 주세요. 크레딧은 차감하지 않았습니다.",
            )
        }
        val analyzed = videos.take(VIDEO_SAMPLE)
        val competitors = if (includeCompetitors) competitorRepository.findByUserId(userId) else emptyList()
        val userVideosStr = analyzed.mapIndexed { i, v ->
            "${i + 1}. ${v.title} (카테고리: ${v.category ?: "미분류"}, 태그: ${v.tags?.joinToString(", ") ?: "없음"})"
        }.joinToString("\n").ifEmpty { "데이터 없음" }

        val competitorStr = if (includeCompetitors) {
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
                        estimatedDemand = it.estimatedDemand?.let(::level),
                        competitionLevel = it.competitionLevel?.let(::level),
                        suggestedAngle = it.suggestedAngle,
                    )
                },
                oversaturated = result.oversaturated.map {
                    OversaturatedTopic(
                        topic = it.topic,
                        saturationLevel = level(it.saturationLevel) ?: it.saturationLevel,
                        recommendation = it.recommendation,
                    )
                },
                analyzedAt = LocalDateTime.now().toString(),
                aiEstimate = AiEstimate(basis = basisOf(analyzed.size, competitors.size)),
                analyzedVideoCount = analyzed.size,
                creditsUsed = AiFeature.CONTENT_GAP_ANALYSIS.creditCost,
            )
        }
    }

    companion object {
        /** 프롬프트에 넣는 내 영상 수. */
        const val VIDEO_SAMPLE = 20
        private val LEVELS = setOf("HIGH", "MEDIUM", "LOW")

        /** HIGH/MEDIUM/LOW 만 받는다. 그 밖의 값(예: 모델이 지어낸 숫자 문자열)은 "판단 못 함"으로 본다. */
        fun level(raw: String): String? = raw.trim().uppercase().takeIf { it in LEVELS }

        fun basisOf(videoCount: Int, competitorCount: Int): String = buildString {
            append("내 영상 ${videoCount}개의 제목·카테고리·태그")
            if (competitorCount > 0) append("와 경쟁 채널 ${competitorCount}개의 이름·구독자·평균 조회수")
            append("만 보고 AI 가 추정했습니다. 검색량·시장 데이터는 보지 않았습니다.")
        }
    }
}
