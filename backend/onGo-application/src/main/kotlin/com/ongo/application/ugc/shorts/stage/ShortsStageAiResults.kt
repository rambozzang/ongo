package com.ongo.application.ugc.shorts.stage

/**
 * AI 단계 실행기들의 structured output 응답 모델.
 * ChatClient `.entity(...)` 로 파싱된다.
 */

/** REFRAME: 1080x1920 세로 크롭 박스. */
data class ReframeCropResult(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

/** SEGMENT: 전사에서 뽑은 클립 후보 목록. */
data class SegmentExtractionResult(
    val clips: List<SegmentClip> = emptyList(),
) {
    data class SegmentClip(
        val title: String? = null,
        val caption: String? = null,
        val startMs: Long = 0,
        val endMs: Long = 0,
    )
}

/** SUBTITLE: 클립별 다듬은 자막 줄 (개수·순서는 입력과 동일해야 한다). */
data class SubtitlePolishResult(
    val clips: List<ClipSubtitles> = emptyList(),
) {
    data class ClipSubtitles(
        val clipSeq: Int = 0,
        val lines: List<String> = emptyList(),
    )
}

/** HOOK: 클립별 후킹 문구 A/B안. */
data class HookGenerationResult(
    val clips: List<ClipHooks> = emptyList(),
) {
    data class ClipHooks(
        val clipSeq: Int = 0,
        val hookA: String = "",
        val hookB: String = "",
    )
}

/** TEMPLATE: 템플릿 적용 메모. */
data class TemplateApplyResult(
    val summary: String = "",
    val notes: String? = null,
)

/** VALIDATE: 검증 리포트에 대한 AI 총평. */
data class ValidateVerdictResult(
    val passed: Boolean = true,
    val summary: String = "",
)
