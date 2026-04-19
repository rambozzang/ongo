package com.ongo.application.ai.result

data class ScheduleOptimalResult(
    val slots: List<SlotItem>,
) {
    data class SlotItem(
        val dayOfWeek: String,       // MON, TUE, WED, THU, FRI, SAT, SUN
        val hour: Int,                // 0-23
        val score: Int,               // 0-100
        val audienceOnline: Int,      // 예상 시청자 수
        val competitionLevel: String, // LOW, MEDIUM, HIGH
        val reason: String,           // 추천 근거
    )
}
