package com.ongo.domain.ugc.shorts

/**
 * UGC 쇼츠 파이프라인의 9단계.
 *
 * @property displayName 화면 표시명
 * @property sortOrder 파이프라인 순서 (1~9)
 * @property aiExecutable 실제 AI 호출에 사용되는 단계인지 여부
 */
enum class PipelineStage(val displayName: String, val sortOrder: Int, val aiExecutable: Boolean) {
    TRANSCRIBE("전사", 1, false),
    REFRAME("세로 변환", 2, true),
    SEGMENT("맥락 컷", 3, true),
    SUBTITLE("자막", 4, true),
    HOOK("후킹 문구", 5, true),
    TEMPLATE("템플릿", 6, true),
    RENDER_SPEC("렌더 스펙", 7, false),
    VALIDATE("검증", 8, true),
    SCHEDULE("예약", 9, false),
}
