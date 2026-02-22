package com.ongo.application.ai.result

/**
 * Spring AI Structured Output DTOs for AI features.
 * These are the record-like data classes that Spring AI deserializes JSON into.
 */

data class MetaGenerationResult(
    val platforms: List<PlatformMetaResult>,
) {
    data class PlatformMetaResult(
        val platform: String,
        val titleCandidates: List<String>,
        val description: String,
        val hashtags: List<String>,
    )
}

data class HashtagGenerationResult(
    val platforms: List<PlatformHashtagsResult>,
) {
    data class PlatformHashtagsResult(
        val platform: String,
        val hashtags: List<String>,
    )
}

data class ScriptAnalysisResult(
    val keywords: List<String>,
    val targetAudience: String,
    val suggestedCategory: String,
    val summary: String,
)

data class CommentReplyResult(
    val replies: List<ToneReplyResult>,
) {
    data class ToneReplyResult(
        val tone: String,
        val reply: String,
    )
}

data class ScheduleSuggestionResult(
    val suggestions: List<ScheduleSlotResult>,
) {
    data class ScheduleSlotResult(
        val dayOfWeek: String,
        val time: String,
        val reason: String,
        val expectedBoost: Double,
    )
}

data class ContentIdeaResult(
    val ideas: List<IdeaResult>,
) {
    data class IdeaResult(
        val title: String,
        val description: String,
        val expectedReaction: String,
        val difficulty: String,
    )
}

data class PerformanceReportResult(
    val reportMarkdown: String,
    val highlights: List<String>,
    val improvements: List<String>,
    val nextWeekSuggestions: List<String>,
)

data class SttResult(
    val text: String,
    val segments: List<SttSegmentResult>,
) {
    data class SttSegmentResult(
        val startTime: Double,
        val endTime: Double,
        val text: String,
    )
}

data class WeeklyDigestResult(
    val summary: String,
    val topVideos: List<TopVideoInsight>,
    val anomalies: List<String>,
    val actionItems: List<String>,
) {
    data class TopVideoInsight(
        val title: String,
        val views: Long,
        val insight: String,
    )
}

data class SentimentAnalysisResult(
    val results: List<SentimentItem>,
) {
    data class SentimentItem(
        val index: Int,
        val sentiment: String,
    )
}

data class FaqClusterResult(
    val clusters: List<FaqClusterItem>,
) {
    data class FaqClusterItem(
        val topic: String,
        val questionCount: Int,
        val sampleQuestions: List<String>,
        val suggestedReply: String,
    )
}

data class BatchReplyDraftResult(
    val drafts: List<DraftItem>,
) {
    data class DraftItem(
        val commentIndex: Int,
        val draftReply: String,
    )
}

data class ContentGapResult(
    val opportunities: List<ContentOpportunityResult>,
    val oversaturated: List<OversaturatedTopicResult>,
) {
    data class ContentOpportunityResult(
        val topic: String,
        val estimatedDemand: String,
        val competitionLevel: String,
        val suggestedAngle: String,
        val relevanceScore: Double,
    )
    data class OversaturatedTopicResult(
        val topic: String,
        val saturationLevel: String,
        val recommendation: String,
    )
}

data class StrategyCoachResult(
    val contentRecommendations: List<ContentRecommendation>,
    val platformStrategy: List<PlatformStrategyItem>,
    val timingAdvice: List<TimingAdvice>,
    val overallStrategy: String,
) {
    data class ContentRecommendation(
        val topic: String,
        val targetPlatform: String,
        val reason: String,
        val priority: String,
        val expectedImpact: String,
    )
    data class PlatformStrategyItem(
        val platform: String,
        val strength: String,
        val opportunity: String,
        val action: String,
    )
    data class TimingAdvice(
        val recommendation: String,
        val reason: String,
        val expectedBoost: String,
    )
}

data class RevenueReportResult(
    val reportMarkdown: String,
    val highlights: List<String>,
    val improvements: List<String>,
    val optimizationTips: List<String>,
    val platformBreakdown: List<PlatformRevenueBreakdown>,
) {
    data class PlatformRevenueBreakdown(
        val platform: String,
        val contribution: String,
        val trend: String,
        val suggestion: String,
    )
}
