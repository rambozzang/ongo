package com.ongo.application.scheduleoptimizer.dto

import com.ongo.application.ai.estimate.AiEstimate
import java.time.LocalDateTime

/**
 * 추천 시간대.
 *
 * - [measured] true: [score] 는 이 시간대 분석 점수를 이번 결과의 최고 시간대 대비 %로 바꾼 값,
 *   [audienceOnline] 은 그 시간대에 게시한 영상의 **조회수 중앙값**(실측)이다. 동시 접속자 수가 아니다.
 * - [measured] false: 이 변경 전에 저장된 AI 값이다. 조회수는 null, 점수는 [aiEstimate] 로 표시한다.
 * - [competitionLevel] 은 잴 데이터가 없어 항상 null 이다.
 * - [reason] 은 AI 가 쓴 문장이다.
 */
data class OptimalSlotResponse(
    val id: Long,
    val platform: String,
    val dayOfWeek: String,
    val hour: Int,
    val score: Int,
    val audienceOnline: Int?,
    val competitionLevel: String?,
    val measured: Boolean,
    val aiEstimate: AiEstimate?,
    val reason: String,
    val createdAt: LocalDateTime?,
)

data class ScheduleRecommendationResponse(
    val id: Long,
    val videoId: Long,
    val channelId: Long? = null,
    val videoTitle: String,
    val currentSchedule: LocalDateTime?,
    val recommendedSchedule: LocalDateTime,
    val platform: String,
    val expectedImprovement: Int,
    val confidence: Int,
    val status: String,
    val createdAt: LocalDateTime?,
)

data class ScheduleOptimizerSummaryResponse(
    val totalRecommendations: Int,
    val appliedCount: Int,
    val avgImprovement: Int,
    val bestDay: String,
    val bestHour: Int,
)
