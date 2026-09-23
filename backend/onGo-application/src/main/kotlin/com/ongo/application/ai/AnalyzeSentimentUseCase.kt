package com.ongo.application.ai

import com.ongo.application.ai.result.SentimentAnalysisResult
import com.ongo.application.ai.economics.AiSpendContext
import com.ongo.application.ai.economics.AiUnitEconomics
import com.ongo.common.enums.PlanType
import com.ongo.common.exception.BusinessException
import com.ongo.domain.comment.Comment
import com.ongo.domain.user.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.ZoneId

@Service
class AnalyzeSentimentUseCase(
    private val chatClientResolver: ChatClientResolver,
    private val rateLimiter: AiRateLimiter,
    private val userRepository: UserRepository,
    private val sentimentUsageRepository: SentimentUsageRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun analyzeBatch(userId: Long, comments: List<String>): List<String> {
        if (comments.isEmpty()) return emptyList()

        val user = userRepository.findById(userId) ?: return unanalyzed(comments.size)
        if (user.planType == PlanType.FREE) return unanalyzed(comments.size)

        // 한 번의 요청은 원가 정책의 배치 상한까지만 보낸다. 나머지 댓글은 "미분석" 으로 남긴다 —
        // 모델에 보내지도 않은 댓글에 중립 같은 가짜 결과를 지어내지 않는다.
        val batch = comments.take(AiUnitEconomics.SENTIMENT_MAX_COMMENTS_PER_BATCH)

        rateLimiter.checkRateLimit(userId)

        val today = LocalDate.now(SEOUL)
        if (!sentimentUsageRepository.tryConsumeBatch(
                userId,
                today,
                AiUnitEconomics.SENTIMENT_BATCHES_PER_DAY,
            )
        ) {
            return unanalyzed(comments.size)
        }

        val numberedComments = batch.mapIndexed { i, c -> "$i: ${c.take(200)}" }.joinToString("\n")
        val userPrompt = PromptTemplates.SENTIMENT_ANALYSIS_USER
            .replace("{comments}", InputSanitizer.sanitize(numberedComments))

        // 실패를 NEUTRAL 로 바꿔 돌려주지 않는다.
        //
        // 예전에는 예외든 빈 응답이든 전부 comments.map { "NEUTRAL" } 로 폴백했다.
        // 호출자 입장에서 "모든 댓글이 중립"과 "분석이 실패함"이 구분되지 않았고,
        // 그 값이 그대로 DB 에 저장돼 감정 통계까지 오염됐다. AI 키가 빠져 있어도
        // 화면에는 정상적인 분석 결과처럼 보였다.
        //
        // 폴백이 필요한 호출자는 스스로 판단하게 둔다. 실제로 댓글 동기화는 이 예외를
        // 잡아 로그를 남기고 진행한다 — 어떤 선택을 했는지가 코드에 드러난다.
        val result = try {
            AiSpendContext.withBudget(
                AiUnitEconomics.SENTIMENT_BATCH_BUDGET_KRW,
                "SENTIMENT_ANALYSIS:$userId:$today",
            ) {
                chatClientResolver.resolve(userId).prompt()
                    .system(PromptTemplates.SENTIMENT_ANALYSIS_SYSTEM)
                    .user(userPrompt)
                    .call()
                    .entity(SentimentAnalysisResult::class.java)
            }
        } catch (e: Exception) {
            log.warn("감정 분석 호출 실패: {}", e.message)
            throw BusinessException("AI_SENTIMENT_FAILED", "댓글 감정 분석에 실패했습니다. 잠시 후 다시 시도해주세요.")
        } ?: throw BusinessException("AI_SENTIMENT_FAILED", "댓글 감정 분석 결과를 받지 못했습니다.")

        val sentimentMap = result.results.associate { it.index to it.sentiment.uppercase() }

        // 모델이 일부 인덱스를 빠뜨리는 경우도 실제 중립으로 확정하지 않는다. 그 자리는
        // UNANALYZED 로 남겨 통계에서 빠지게 하고, 누락 건수도 남긴다.
        val missing = batch.indices.count { sentimentMap[it] == null }
        if (missing > 0) {
            log.warn("감정 분석 결과 누락: {}/{}건은 미분석으로 둔다", missing, batch.size)
        }

        return batch.indices.map { sentimentMap[it] ?: Comment.SENTIMENT_UNANALYZED } +
            unanalyzed(comments.size - batch.size)
    }

    private fun unanalyzed(count: Int): List<String> =
        List(count.coerceAtLeast(0)) { Comment.SENTIMENT_UNANALYZED }

    companion object {
        private val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
