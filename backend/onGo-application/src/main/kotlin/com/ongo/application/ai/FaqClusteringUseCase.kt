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

    fun execute(userId: Long): FaqClusterResponse {
        rateLimiter.checkRateLimit(userId)
        creditService.validateAndDeduct(userId, AiFeature.FAQ_CLUSTERING)

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

        try {
            val result = chatClientResolver.resolve(userId).prompt()
                .system(PromptTemplates.FAQ_CLUSTERING_SYSTEM)
                .user(userPrompt)
                .call()
                .entity(FaqClusterResult::class.java)
                ?: throw BusinessException("AI_PARSE_ERROR", "AI 응답을 파싱할 수 없습니다")

            return FaqClusterResponse(
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
        } catch (e: BusinessException) {
            throw e
        } catch (e: Exception) {
            log.error("FAQ 클러스터링 실패, 크레딧 환불 처리: userId={}", userId, e)
            creditService.refundCredit(userId, AiFeature.FAQ_CLUSTERING.creditCost, AiFeature.FAQ_CLUSTERING.name)
            throw BusinessException("AI_CALL_FAILED", "AI 호출에 실패했습니다: ${e.message}")
        }
    }
}
