package com.ongo.application.ai.result

/**
 * 업로드 시간 추천의 AI 응답. AI 가 채우는 것은 **요일·시간·이유**뿐이다.
 *
 * [SlotItem.score]·[SlotItem.audienceOnline]·[SlotItem.competitionLevel] 은 서버가 실측으로 채우거나
 * (조회수 중앙값·분석 점수) 데이터가 없다고 표시한다. 모델이 값을 넣어 보내도 쓰지 않는다.
 */
data class ScheduleOptimalResult(
    val slots: List<SlotItem>,
) {
    data class SlotItem(
        val dayOfWeek: String,       // MON, TUE, WED, THU, FRI, SAT, SUN
        val hour: Int,                // 0-23
        val score: Int = 0,           // 쓰지 않음 — 서버가 실측 점수로 채운다
        val audienceOnline: Int = 0,  // 쓰지 않음 — 서버가 조회수 중앙값으로 채운다
        val competitionLevel: String = "UNKNOWN", // 쓰지 않음 — 잴 데이터가 없다
        val reason: String,           // 추천 근거
    )
}
