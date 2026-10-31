package com.ongo.application.comment.dto

import java.time.LocalDateTime

// AI 답변 후보 생성 요청
data class GenerateAiReplyRequest(
    val commentId: Long,
    val tone: String? = null,
)

// AI 답변 후보 단건
data class AiReplyCandidate(
    val id: Long,
    val generatedReply: String,
    val tone: String,
    val isUsed: Boolean,
    val createdAt: LocalDateTime?,
)

// AI 답변 후보 목록 응답
data class AiReplyCandidatesResponse(
    val commentId: Long,
    val commentContent: String,
    val candidates: List<AiReplyCandidate>,
)

// 일괄 답변 요청
data class BatchReplyRequest(
    val commentIds: List<Long>,
    val replyText: String,
)

// 일괄 숨김 요청
data class BatchHideRequest(
    val commentIds: List<Long>,
)

// 일괄 처리 결과
data class BatchResultResponse(
    val successCount: Int,
    val failCount: Int,
    val totalRequested: Int,
    /**
     * 요청 순서대로의 항목별 결과. 개수만으로는 **어떤 댓글이 왜** 실패했는지 알 수 없어
     * 실패한 것만 다시 시도할 수 없었다. 중복 ID 는 한 번만 처리되어 한 항목이다.
     */
    val items: List<BatchItemResult> = emptyList(),
)

data class BatchItemResult(
    val commentId: Long,
    val success: Boolean,
    /** 실패 코드(예: PLATFORM_API_FAILED, COMMENT_REPLY_UNSUPPORTED). 성공이면 null. */
    val errorCode: String? = null,
    val message: String? = null,
    /** 같은 요청을 다시 보내면 성공할 수 있는가. 플랫폼 일시 오류는 true, 권한·미지원·삭제된 댓글은 false. */
    val retryable: Boolean = false,
)

// 위기 감지 응답
data class CrisisDetectionResponse(
    val isInCrisis: Boolean,
    val currentNegativeCount: Int,
    val previousNegativeCount: Int,
    val changePercent: Double,
    val topKeywords: List<String>,
    val windowHours: Int,
    val thresholdPct: Int,
)
