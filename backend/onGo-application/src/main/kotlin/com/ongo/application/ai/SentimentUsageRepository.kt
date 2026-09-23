package com.ongo.application.ai

import java.time.LocalDate

/** 자동 감정 분석 배치의 하루 허용 횟수를 원자적으로 센다(DB 에 저장 — 재기동·다중 요청에도 상한이 유지된다). */
interface SentimentUsageRepository {
    fun tryConsumeBatch(userId: Long, date: LocalDate, dailyLimit: Int): Boolean
}
