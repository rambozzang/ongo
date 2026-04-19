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
