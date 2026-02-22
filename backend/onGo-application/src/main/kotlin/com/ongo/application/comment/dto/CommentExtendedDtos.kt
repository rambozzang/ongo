package com.ongo.application.comment.dto

data class SentimentTrendResponse(
    val data: List<SentimentTrendPoint>,
    val summary: SentimentSummary,
)

data class SentimentTrendPoint(
    val date: String,
    val positive: Int,
    val neutral: Int,
    val negative: Int,
)

data class SentimentSummary(
    val totalPositive: Int,
    val totalNeutral: Int,
    val totalNegative: Int,
    val trend: String,
)

data class FaqClusterResponse(
    val clusters: List<FaqCluster>,
    val generatedAt: String,
)

data class FaqCluster(
    val topic: String,
    val questionCount: Int,
    val sampleQuestions: List<String>,
    val suggestedReply: String,
)

data class BatchAiDraftRequest(
    val commentIds: List<Long>,
    val tone: String = "FRIENDLY",
)

data class BatchAiDraftResponse(
    val drafts: List<AiDraftItem>,
    val totalCreditsUsed: Int,
)

data class AiDraftItem(
    val commentId: Long,
    val commentContent: String,
    val draftReply: String,
    val tone: String,
)
