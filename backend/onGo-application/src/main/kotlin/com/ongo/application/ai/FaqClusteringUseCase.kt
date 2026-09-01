package com.ongo.application.ai

import com.ongo.application.ai.result.FaqClusterResult
import com.ongo.application.comment.dto.FaqCluster
import com.ongo.application.comment.dto.FaqClusterResponse
import com.ongo.application.credit.CreditService
import com.ongo.common.enums.AiFeature
import com.ongo.common.exception.BusinessException
import com.ongo.domain.comment.CommentRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class FaqClusteringUseCase(
    private val chatClientResolver: ChatClientResolver,
    private val creditService: CreditService,
    private val rateLimiter: AiRateLimiter,
    private val commentRepository: CommentRepository,
) {

    private val log = LoggerFactory.getLogger(FaqClusteringUseCase::class.java)

    /**
     * ## 차감을 댓글 조회 **뒤로** 옮긴 이유
     *
     * 예전에는 조회 전에 차감했다. 그래서 분석할 댓글이 하나도 없을 때도 크레딧이 나갔고,
     * 그 경로는 빈 응답을 반환하며 끝나 환불조차 하지 않았다. **AI 를 부르지 않은 요청에
     * 과금하면 안 된다.**
     *
     * 차감·환불은 [CreditService.withCredits] 한 곳에서 처리한다. `AI_PARSE_ERROR` 도
     * 환불 대상이다.
     */
    fun execute(userId: Long): FaqClusterResponse {
        rateLimiter.checkRateLimit(userId)

        // 최근 200개 댓글 수집
        val comments = commentRepository.findByUserId(userId, 0, 200)
        if (comments.isEmpty()) {
            return FaqClusterResponse(
                clusters = emptyList(),
                generatedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            )
        }

        val commentsText = comments.mapIndexed { index, comment ->
            "[$index] ${InputSanitizer.sanitize(comment.content)}"
        }.joinToString("\n")

        val userPrompt = PromptTemplates.FAQ_CLUSTERING_USER
            .replace("{comments}", commentsText)

        return creditService.withCredits(userId, AiFeature.FAQ_CLUSTERING) {
            val result = try {
                chatClientResolver.resolve(userId).prompt()
                    .system(PromptTemplates.FAQ_CLUSTERING_SYSTEM)
                    .user(userPrompt)
                    .call()
                    .entity(FaqClusterResult::class.java)
                    ?: throw BusinessException("AI_PARSE_ERROR", "AI 응답을 파싱할 수 없습니다")
            } catch (e: BusinessException) {
                throw e
            } catch (e: Exception) {
                log.error("FAQ 클러스터링 실패: userId={}", userId, e)
                throw BusinessException("AI_CALL_FAILED", "AI 호출에 실패했습니다: ${e.message}")
            }

            FaqClusterResponse(
                clusters = result.clusters.map { cluster ->
                    FaqCluster(
                        topic = cluster.topic,
                        questionCount = cluster.questionCount,
                        sampleQuestions = cluster.sampleQuestions,
                        suggestedReply = cluster.suggestedReply,
                    )
                },
                generatedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            )
        }
    }
}
