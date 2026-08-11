package com.ongo.application.ai

import com.ongo.application.ai.result.SentimentAnalysisResult
import com.ongo.common.exception.BusinessException
import com.ongo.domain.comment.Comment
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class AnalyzeSentimentUseCase(
    private val chatClientResolver: ChatClientResolver,
    private val rateLimiter: AiRateLimiter,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun analyzeBatch(userId: Long, comments: List<String>): List<String> {
        if (comments.isEmpty()) return emptyList()

        rateLimiter.checkRateLimit(userId)

        val numberedComments = comments.mapIndexed { i, c -> "$i: ${c.take(200)}" }.joinToString("\n")
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
            chatClientResolver.resolve(userId).prompt()
                .system(PromptTemplates.SENTIMENT_ANALYSIS_SYSTEM)
                .user(userPrompt)
                .call()
                .entity(SentimentAnalysisResult::class.java)
        } catch (e: Exception) {
            log.warn("감정 분석 호출 실패: {}", e.message)
            throw BusinessException("AI_SENTIMENT_FAILED", "댓글 감정 분석에 실패했습니다. 잠시 후 다시 시도해주세요.")
        } ?: throw BusinessException("AI_SENTIMENT_FAILED", "댓글 감정 분석 결과를 받지 못했습니다.")

        val sentimentMap = result.results.associate { it.index to it.sentiment.uppercase() }

        // 모델이 일부 인덱스를 빠뜨리는 경우도 실제 중립으로 확정하지 않는다. 그 자리는
        // UNANALYZED 로 남겨 통계에서 빠지게 하고, 누락 건수도 남긴다.
        val missing = comments.indices.count { sentimentMap[it] == null }
        if (missing > 0) {
            log.warn("감정 분석 결과 누락: {}/{}건은 NEUTRAL 로 채운다", missing, comments.size)
        }

        return comments.indices.map { sentimentMap[it] ?: Comment.SENTIMENT_UNANALYZED }
    }
}
