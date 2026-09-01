package com.ongo.application.metarewrite

import com.ongo.application.ai.AiRateLimiter
import com.ongo.application.ai.ChatClientResolver
import com.ongo.application.analytics.AnalyticsRowPlatforms
import com.ongo.application.analytics.PlatformMetricAvailability
import com.ongo.application.ai.PromptTemplates
import com.ongo.application.ai.result.MetaRewriteResult
import com.ongo.application.credit.CreditService
import com.ongo.application.metarewrite.dto.MetaRewriteResponse
import com.ongo.common.enums.AiFeature
import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.video.VideoUploadRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class MetaRewriteUseCase(
    private val videoRepository: VideoRepository,
    private val analyticsRepository: AnalyticsRepository,
    private val videoUploadRepository: VideoUploadRepository,
    private val chatClientResolver: ChatClientResolver,
    private val creditService: CreditService,
    private val rateLimiter: AiRateLimiter,
) {

    private val log = LoggerFactory.getLogger(MetaRewriteUseCase::class.java)

    /**
     * **트랜잭션을 열지 않는다.** LLM 호출을 `@Transactional` 안에 두면 `ai_credits` 행
     * 잠금과 DB 커넥션이 모델 응답 시간만큼 묶인다. 차감·환불의 커밋 경계는
     * [CreditService.withCredits] 가 잡는다.
     */
    fun rewriteMeta(userId: Long, videoId: Long): MetaRewriteResponse {
        val video = videoRepository.findById(videoId)
            ?: throw NotFoundException("영상", videoId)

        if (video.userId != userId) {
            throw ForbiddenException("해당 영상에 접근할 권한이 없습니다")
        }

        rateLimiter.checkRateLimit(userId)

        // 최근 30일 analytics 집계 — 조회수 및 참여율 계산
        val to = LocalDate.now()
        val from = to.minusDays(30)
        /*
         * **`getDailyAggregates` 를 쓰지 않는다 — 그 결과에는 플랫폼이 없다.**
         *
         * 날짜별로만 묶여 나오므로 어느 플랫폼의 행인지 알 수 없고, `TumblrClient.kt:141`
         * 의 `total_notes`(노트 총합)가 조회수로, `PinterestClient.kt:158/160` 의
         * `SAVE`(저장)·`PIN_CLICK`(클릭)이 참여 수로 섞인다. 하드코딩 0 과 달리 **다른 뜻의
         * 큰 숫자**라 유료 프롬프트에 들어가면 모델이 없는 성과를 설명한다.
         *
         * 같은 기간을 업로드 단위로 읽어 행마다 플랫폼을 붙인다.
         */
        val channelUploads = videoUploadRepository.findByUserId(userId)
        val rowPlatforms = AnalyticsRowPlatforms.of(channelUploads)
        val rows = analyticsRepository
            .findByVideoUploadIdsAndDateRange(channelUploads.mapNotNull { it.id }, from, to)
            .values
            .flatten()

        val viewRows = rowPlatforms.rowsReporting(rows, PlatformMetricAvailability.VIEWS)
        val totalViews = if (viewRows.isEmpty()) {
            NOT_COLLECTED
        } else {
            viewRows.sumOf { it.views.toLong() }.toString()
        }

        /*
         * 참여율은 분자와 분모가 **같은 행**에서 나와야 한다. 좋아요·댓글·조회수를 모두
         * 수집하는 행만 쓴다 — 분자에서만 빼고 조회수를 분모에 남기면 참여율이 낮아진다.
         *
         * 분모가 없으면 비율이 성립하지 않는다. `0.0` 은 "참여가 없었다" 는 관측이 된다.
         */
        val engagementRows = rowPlatforms.rowsReporting(
            rows,
            PlatformMetricAvailability.LIKES,
            PlatformMetricAvailability.COMMENTS,
            PlatformMetricAvailability.VIEWS,
        )
        val engagementViews = engagementRows.sumOf { it.views.toLong() }
        val engagementRate = if (engagementViews > 0) {
            val numerator = engagementRows.sumOf { (it.likes + it.commentsCount).toLong() }
            // 단위(`%`)를 값이 직접 들고 있다. 템플릿에 붙여 두면 미측정일 때 문장과 충돌한다.
            String.format("%.2f%%", numerator.toDouble() / engagementViews * 100)
        } else {
            NOT_COLLECTED
        }

        // Use the video's actual durable publication targets. A fixed platform
        // list produces paid recommendations for channels the creator never
        // selected (and silently omits connected platforms).
        val platforms = videoUploadRepository.findByVideoId(videoId)
            .map { it.platform.name }
            .distinct()
            .joinToString(", ")
            .ifBlank { "플랫폼 미지정" }
        val originalTags = video.tags.joinToString(", ")

        val userPrompt = PromptTemplates.META_REWRITE_USER
            .replace("{platform}", platforms)
            .replace("{originalTitle}", video.title)
            .replace("{originalDescription}", video.description ?: "")
            .replace("{originalTags}", originalTags)
            .replace("{totalViews}", totalViews)
            .replace("{engagementRate}", engagementRate)

        return creditService.withCredits(userId, AiFeature.META_REWRITE) {
            val result = try {
                chatClientResolver.resolve(userId).prompt()
                    .system(PromptTemplates.META_REWRITE_SYSTEM)
                    .user(userPrompt)
                    .call()
                    .entity(MetaRewriteResult::class.java)
                    ?: throw BusinessException("AI_PARSE_ERROR", "AI 응답을 파싱할 수 없습니다")
            } catch (e: BusinessException) {
                throw e
            } catch (e: Exception) {
                log.error("메타데이터 리라이트 실패: userId={}, videoId={}", userId, videoId, e)
                throw BusinessException("AI_CALL_FAILED", "AI 호출에 실패했습니다: ${e.message}")
            }

            MetaRewriteResponse(
                id = videoId,
                videoId = videoId,
                originalTitle = video.title,
                originalDescription = video.description,
                suggestedTitle = result.suggestedTitle,
                suggestedDescription = result.suggestedDescription,
                suggestedTags = result.suggestedTags,
                reasoning = result.reasoning,
                expectedImpactPercent = result.expectedImpactPercent,
                createdAt = LocalDateTime.now(),
            )
        }
    }

    companion object {
        /**
         * 지표를 **수집하는 행이 하나도 없을 때** 프롬프트에 넣는 문구.
         *
         * 숫자가 아니라 문장이어야 한다 — 어떤 숫자를 넣든 모델은 그것을 측정값으로 읽고
         * 없는 성과를 근거로 메타데이터를 다시 쓴다.
         */
        const val NOT_COLLECTED = "측정 불가(수집하는 플랫폼 없음)"
    }
}
