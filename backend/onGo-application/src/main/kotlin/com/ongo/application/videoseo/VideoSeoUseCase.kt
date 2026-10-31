package com.ongo.application.videoseo

import com.ongo.application.ai.AiRateLimiter
import com.ongo.application.ai.ChatClientResolver
import com.ongo.application.ai.PromptTemplates
import com.ongo.application.ai.result.VideoSeoScoreResult
import com.ongo.application.credit.CreditService
import com.ongo.application.ai.estimate.AiEstimate
import com.ongo.application.videoseo.dto.SeoCategoryResponse
import com.ongo.application.videoseo.dto.SeoCheckResponse
import com.ongo.application.videoseo.dto.VideoSeoScoreResponse
import com.ongo.common.enums.AiFeature
import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.video.VideoUploadRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class VideoSeoUseCase(
    private val videoRepository: VideoRepository,
    private val videoUploadRepository: VideoUploadRepository,
    private val chatClientResolver: ChatClientResolver,
    private val creditService: CreditService,
    private val rateLimiter: AiRateLimiter,
) {

    private val log = LoggerFactory.getLogger(VideoSeoUseCase::class.java)

    /**
     * **트랜잭션을 열지 않는다.** LLM 호출을 `@Transactional` 안에 두면 `ai_credits` 행
     * 잠금과 DB 커넥션이 모델 응답 시간만큼 묶인다. 크레딧 차감·환불의 커밋 경계는
     * [CreditService.withCredits] 가 직접 잡는다.
     */
    fun analyzeVideoSeo(userId: Long, videoId: Long): VideoSeoScoreResponse {
        val video = videoRepository.findById(videoId)
            ?: throw NotFoundException("영상", videoId)

        if (video.userId != userId) {
            throw ForbiddenException("해당 영상에 접근할 권한이 없습니다")
        }

        rateLimiter.checkRateLimit(userId)

        val tags = video.tags.joinToString(", ")
        // SEO guidance must follow the video's actual publication targets. Do
        // not charge a creator for recommendations aimed at unrelated channels.
        val platforms = videoUploadRepository.findByVideoId(videoId).map { it.platform }.distinct()
        val platform = platforms.joinToString(", ") { it.name }.ifBlank { "플랫폼 미지정" }

        // 점수는 규칙으로 먼저 매긴다. AI 호출이 실패해도 이 값은 같다 — 다만 실패하면 크레딧을 돌려주고 결과는 내지 않는다.
        val rubric = SeoRubric.evaluate(video.title, video.description, video.tags, video.category, platforms)
        val failedChecks = rubric.failedChecks
            .joinToString("\n") { "- ${it.label}: ${it.detail}" }
            .ifEmpty { "없음(모든 항목 통과)" }

        val userPrompt = PromptTemplates.VIDEO_SEO_USER
            .replace("{platform}", platform)
            .replace("{title}", video.title)
            .replace("{description}", video.description ?: "")
            .replace("{tags}", tags)
            .replace("{category}", video.category ?: "미분류")
            .replace("{failedChecks}", failedChecks)

        return creditService.withCredits(userId, AiFeature.VIDEO_SEO_SCORE) {
            val result = try {
                chatClientResolver.resolve(userId).prompt()
                    .system(PromptTemplates.VIDEO_SEO_SYSTEM)
                    .user(userPrompt)
                    .call()
                    .entity(VideoSeoScoreResult::class.java)
                    ?: throw BusinessException("AI_PARSE_ERROR", "AI 응답을 파싱할 수 없습니다")
            } catch (e: BusinessException) {
                throw e
            } catch (e: Exception) {
                log.error("비디오 SEO 점수 분석 실패: userId={}, videoId={}", userId, videoId, e)
                throw BusinessException("AI_CALL_FAILED", "AI 호출에 실패했습니다: ${e.message}")
            }

            VideoSeoScoreResponse(
                videoId = videoId,
                overallScore = rubric.overallScore,
                scoreBasis = SCORE_BASIS,
                categories = rubric.categories.map { category ->
                    SeoCategoryResponse(
                        key = category.key,
                        name = category.name,
                        score = category.score,
                        maxScore = category.maxScore,
                        checks = category.checks.map {
                            SeoCheckResponse(it.key, it.label, it.passed, it.points, it.maxPoints, it.detail)
                        },
                    )
                },
                suggestions = result.suggestions.map(String::trim).filter(String::isNotEmpty),
                relatedKeywords = result.relatedKeywords.map { it.trim().trimStart('#') }.filter(String::isNotEmpty).distinct(),
                aiEstimate = AiEstimate(basis = SUGGESTION_BASIS),
                creditsUsed = AiFeature.VIDEO_SEO_SCORE.creditCost,
            )
        }
    }

    companion object {
        const val SCORE_BASIS =
            "onGo 규칙 점수입니다. 제목·설명·태그의 실제 글자 수, 플랫폼 한도, 키워드 포함 여부로 매겼고 AI 가 매기지 않았습니다. 플랫폼 검색 순위를 뜻하지 않습니다."
        const val SUGGESTION_BASIS =
            "개선 제안과 관련 키워드는 AI 가 이 영상의 제목·설명·태그만 보고 만든 것입니다. 검색량·순위 데이터는 보지 않았습니다."
    }
}
