package com.ongo.application.comment

import com.ongo.application.comment.dto.BatchHideRequest
import com.ongo.application.comment.dto.BatchReplyRequest
import com.ongo.application.comment.dto.BatchResultResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.Executors
import java.util.concurrent.Future

@Service
class CommentBatchUseCase(
    private val commentEngagementUseCase: CommentEngagementUseCase,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun batchReply(userId: Long, request: BatchReplyRequest): BatchResultResponse {
        val executor = Executors.newVirtualThreadPerTaskExecutor()
        val successCount = java.util.concurrent.atomic.AtomicInteger(0)
        val failCount = java.util.concurrent.atomic.AtomicInteger(0)

        try {
            val futures: List<Future<*>> = request.commentIds.map { commentId ->
                executor.submit {
                    try {
                        commentEngagementUseCase.replyToComment(userId, commentId, request.replyText)
                        successCount.incrementAndGet()
                    } catch (e: Exception) {
                        log.warn("일괄 답변 실패: commentId={}, error={}", commentId, e.message)
                        failCount.incrementAndGet()
                    }
                }
            }
            futures.forEach { it.get() }
        } finally {
            executor.shutdown()
        }

        return BatchResultResponse(
            successCount = successCount.get(),
            failCount = failCount.get(),
            totalRequested = request.commentIds.size,
        )
    }

    fun batchHide(userId: Long, request: BatchHideRequest): BatchResultResponse {
        val executor = Executors.newVirtualThreadPerTaskExecutor()
        val successCount = java.util.concurrent.atomic.AtomicInteger(0)
        val failCount = java.util.concurrent.atomic.AtomicInteger(0)

        try {
            val futures: List<Future<*>> = request.commentIds.map { commentId ->
                executor.submit {
                    try {
                        commentEngagementUseCase.hideComment(userId, commentId)
                        successCount.incrementAndGet()
                    } catch (e: Exception) {
                        log.warn("일괄 숨김 실패: commentId={}, error={}", commentId, e.message)
                        failCount.incrementAndGet()
                    }
                }
            }
            futures.forEach { it.get() }
        } finally {
            executor.shutdown()
        }

        return BatchResultResponse(
            successCount = successCount.get(),
            failCount = failCount.get(),
            totalRequested = request.commentIds.size,
        )
    }
}
