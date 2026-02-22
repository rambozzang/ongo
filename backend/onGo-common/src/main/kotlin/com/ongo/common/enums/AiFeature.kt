package com.ongo.common.enums

enum class AiFeature(
    val displayName: String,
    val creditCost: Int,
) {
    META_GENERATION("제목/설명 생성", 5),
    HASHTAG_RECOMMENDATION("해시태그 추천", 3),
    STT("영상 STT 변환", 10),
    SCRIPT_ANALYSIS("스크립트 분석", 5),
    COMMENT_REPLY("댓글 답변 생성", 2),
    SCHEDULE_SUGGESTION("업로드 시간 추천", 3),
    CONTENT_IDEA("콘텐츠 아이디어", 5),
    PERFORMANCE_REPORT("성과 리포트", 8),
    WEEKLY_DIGEST("주간 다이제스트", 8),
    CONTENT_GAP_ANALYSIS("콘텐츠 갭 분석", 10),
    COMPETITOR_INSIGHT("경쟁자 벤치마킹 인사이트", 8),
    SENTIMENT_ANALYSIS("감정 분석", 0),
    STRATEGY_COACH("AI 전략 코치", 10),
    REVENUE_REPORT("수익 분석 리포트", 8),
    FAQ_CLUSTERING("FAQ 자동 분류", 5),
    BATCH_REPLY_DRAFT("배치 답변 초안", 3),
}
