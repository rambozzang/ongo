package com.ongo.application.comment

import com.ongo.application.comment.dto.BatchHideRequest
import com.ongo.application.comment.dto.BatchItemResult
import com.ongo.application.comment.dto.BatchReplyRequest
import com.ongo.application.comment.dto.BatchResultResponse
import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.PlatformApiException
import com.ongo.common.exception.RateLimitExceededException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore

/**
 * 댓글 일괄 처리. **항목마다 결과를 돌려준다** — 화면이 성공한 것만 처리 완료로 바꾸고 실패한 것만 다시 시도하게.
 *
 * 예전에는 개수만 돌려줘서, 화면은 일부가 실패해도 선택한 전부를 처리 완료로 표시했다(가짜 성공).
 * 요청 개수·동시 실행 상한도 없어 수백 건이 플랫폼 API 로 한꺼번에 나갈 수 있었다.
 */
@Service
class CommentBatchUseCase(
    private val commentEngagementUseCase: CommentEngagementUseCase,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun batchReply(userId: Long, request: BatchReplyRequest): BatchResultResponse {
        val text = request.replyText.trim()
        if (text.isEmpty()) throw BusinessException("BATCH_REPLY_EMPTY", "보낼 답변을 입력해 주세요.")
        if (text.length > MAX_REPLY_LENGTH) {
            throw BusinessException("BATCH_REPLY_TOO_LONG", "답변은 ${MAX_REPLY_LENGTH}자 이하로 입력해 주세요.")
        }
        return runBatch(request.commentIds, "일괄 답변") { commentId ->
            commentEngagementUseCase.replyToComment(userId, commentId, text)
        }
    }

    /** "처리 완료" 다. 토글이 아니라 **숨김으로 정한다** — 이미 숨긴 댓글이 섞여 있어도 되살리지 않는다. */
    fun batchHide(userId: Long, request: BatchHideRequest): BatchResultResponse =
        runBatch(request.commentIds, "일괄 숨김") { commentId ->
            commentEngagementUseCase.setHidden(userId, commentId, hidden = true)
        }

    private fun runBatch(commentIds: List<Long>, label: String, action: (Long) -> Unit): BatchResultResponse {
        val ids = commentIds.distinct()
        if (ids.isEmpty()) throw BusinessException("BATCH_EMPTY", "처리할 댓글을 선택해 주세요.")
        if (ids.size > MAX_BATCH_SIZE) {
            throw BusinessException("BATCH_TOO_LARGE", "한 번에 ${MAX_BATCH_SIZE}개까지 처리할 수 있습니다.")
        }

        // 플랫폼 API 속도 제한에 한꺼번에 걸리지 않게 동시에 MAX_CONCURRENCY 건만 보낸다.
        val slots = Semaphore(MAX_CONCURRENCY)
        val results = Executors.newVirtualThreadPerTaskExecutor().use { executor ->
            ids.map { commentId ->
                executor.submit<BatchItemResult> {
                    slots.acquire()
                    try {
                        action(commentId)
                        BatchItemResult(commentId = commentId, success = true)
                    } catch (e: Exception) {
                        log.warn("{} 실패: commentId={}, error={}", label, commentId, e.message)
                        failureOf(commentId, e)
                    } finally {
                        slots.release()
                    }
                }
            }.map { it.get() }
        }

        val succeeded = results.count { it.success }
        return BatchResultResponse(
            successCount = succeeded,
            failCount = results.size - succeeded,
            totalRequested = ids.size,
            items = results,
        )
    }

    /** 다시 보내서 나아질 수 있는 실패인지 가른다. 권한·미지원·삭제된 댓글은 다시 보내도 같다. */
    private fun failureOf(commentId: Long, e: Exception): BatchItemResult = when (e) {
        is PlatformApiException -> BatchItemResult(commentId, false, e.code, e.message, retryable = true)
        is RateLimitExceededException -> BatchItemResult(commentId, false, "RATE_LIMITED", e.message, retryable = true)
        is BusinessException -> BatchItemResult(commentId, false, e.code, e.message, retryable = false)
        else -> BatchItemResult(commentId, false, "BATCH_ITEM_FAILED", "처리 중 오류가 발생했습니다.", retryable = true)
    }

    companion object {
        const val MAX_BATCH_SIZE = 50
        const val MAX_CONCURRENCY = 4
        const val MAX_REPLY_LENGTH = 2_000
    }
}
