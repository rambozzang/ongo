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

data class PerformanceReportResult(
    val reportMarkdown: String,
    val highlights: List<String>,
    val improvements: List<String>,
    val nextWeekSuggestions: List<String>,
)

data class SttResult(
    val text: String,
    val segments: List<SttSegmentResult>,
    /** 단어 단위 시각(초). 모델이 주지 않으면 비어 있다 — 그때 자막은 문장 단위로만 강조 없이 나간다. */
    val words: List<SttWordResult> = emptyList(),
) {
    data class SttSegmentResult(
        val startTime: Double,
        val endTime: Double,
        val text: String,
    )

    data class SttWordResult(
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
    /**
     * 수요·경쟁은 AI 추정이다(검색·시장 데이터를 보지 않는다). 모르면 null.
     * 예전의 관련성 점수(0~100)는 근거가 없어 없앴다.
     */
    data class ContentOpportunityResult(
        val topic: String,
        val estimatedDemand: String? = null,
        val competitionLevel: String? = null,
        val suggestedAngle: String = "",
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

data class ContentCalendarResult(
    val suggestions: List<CalendarSuggestionItem>,
) {
    data class CalendarSuggestionItem(
        val title: String,
        val description: String,
        val suggestedDate: String,
        val suggestedTime: String,
        val platform: String,
        val contentType: String,
        val topic: String,
        val expectedEngagement: Double,
        val confidence: Int,
    )
}

data class RevenueInsightResult(
    val insightType: String,
    val summary: String,
    val details: List<String>,
    val recommendations: List<String>,
    val confidence: Double,
)

data class MetaRewriteResult(
    val suggestedTitle: String,
    val suggestedDescription: String,
    val suggestedTags: List<String>,
    val reasoning: String,
    val expectedImpactPercent: Int,
)

data class ContentRepurposeResult(
    val clips: List<ClipSuggestion>,
) {
    data class ClipSuggestion(
        val startTime: String,
        val endTime: String,
        val title: String,
        val description: String,
        val viralScore: Int,
        val reasoning: String,
        val suggestedPlatform: String,
    )
}

/**
 * 채널 감사의 AI 응답이자 저장 형식.
 *
 * [overallScore] 는 **더 이상 AI 에게 묻지 않는다**(근거 없는 0~100 점수였다). 이 필드는 예전에 저장된
 * 보고서를 읽기 위해 남겨 두며, 새 보고서는 null 로 저장한다. 실측 지표는 저장할 때 `measured` 로
 * 따로 붙인다 — 이 클래스에 넣으면 구조화 출력 스키마에 들어가 모델이 그 숫자까지 채우려 든다.
 */
data class ChannelAuditResult(
    val overallScore: Int? = null,
    val strengths: List<String>,
    val weaknesses: List<String>,
    val actionItems: List<ActionItem>,
    val outlierVideos: List<OutlierVideo>,
    val growthForecast: String,
) {
    data class ActionItem(
        val priority: String,
        val action: String,
        val expectedImpact: String,
    )

    data class OutlierVideo(
        val videoTitle: String,
        val metric: String,
        val reason: String,
    )
}

/**
 * SEO 점검의 AI 몫: 개선 제안과 관련 키워드뿐이다. 점수는 `SeoRubric` 이 규칙으로 매긴다 —
 * AI 에게 점수를 묻지 않는다(근거 없는 숫자가 측정값처럼 보였다).
 */
data class VideoSeoScoreResult(
    val suggestions: List<String> = emptyList(),
    val relatedKeywords: List<String> = emptyList(),
)

data class KeywordResearchResult(
    val keyword: String,
    val platforms: List<PlatformKeywordAnalysis>,
    val overallOpportunity: String,
    val suggestions: List<String>,
) {
    /**
     * 검색량·경쟁도·추세는 **AI 추정**이다 — 모델은 검색 데이터를 보지 않는다. 모르면 null.
     * 예전의 기회 점수(1~100)는 근거가 없어 없앴다.
     */
    data class PlatformKeywordAnalysis(
        val platform: String,
        val searchVolume: String? = null,
        val competition: String? = null,
        val trend: String? = null,
        val relatedKeywords: List<String> = emptyList(),
    )
}
