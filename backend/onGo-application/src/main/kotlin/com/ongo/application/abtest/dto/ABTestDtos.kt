package com.ongo.application.abtest.dto

import java.math.BigDecimal
import java.time.LocalDateTime

data class ABTestResponse(
    val id: Long,
    val videoId: Long?,
    val testName: String,
    val status: String,
    val metricType: String,
    val durationHours: Int?,
    val winnerVariantId: Long?,
    val startedAt: LocalDateTime?,
    val endedAt: LocalDateTime?,
    val createdAt: LocalDateTime?,
    val variants: List<ABTestVariantResponse>,
)

/**
 * 변형 하나의 성과.
 *
 * ## 세 지표가 `null` 이면 **측정하지 않았다** — 0 이 아니다
 *
 * 노출이 0 이면 클릭도 CTR 도 존재할 수 없다. 그런데 예전에는 도메인 기본값 0 이 그대로
 * 응답에 실려 나갔고, 프론트는 `ctr: views > 0 ? ... : 0` 으로 한 번 더 0 을 만들어
 * 결과 차트에 **"0.0%" · "노출 0" · "클릭 0"** 을 정상 측정값처럼 그렸다.
 *
 * 지금 이 값들을 채우는 경로가 코드 어디에도 없다 — 변형 생성 시 기본값 0 이고 갱신하는
 * 스케줄러·엔드포인트·동기화가 없다. onGo 는 썸네일을 직접 서빙하지 않으므로 노출·클릭을
 * 관측할 수단 자체가 없다. 즉 **모든 변형이 항상 "0.0% 성과" 로 표시되고 있었다.**
 *
 * 노출이 측정된 변형(`views > 0`)의 값은 그대로 보존한다. 클릭 0 은 그때 측정된 사실이다.
 */
data class ABTestVariantResponse(
    val id: Long,
    val variantName: String,
    val title: String?,
    val description: String?,
    val thumbnailUrl: String?,
    /** 노출 수. **측정되지 않았으면 `null`.** */
    val views: Long?,
    /** 클릭 수. 노출이 측정됐을 때만 의미가 있다. 아니면 `null`. */
    val clicks: Long?,
    /** 참여율. 노출이 측정됐을 때만 의미가 있다. 아니면 `null`. */
    val engagementRate: BigDecimal?,
    /** 세 지표가 `null` 인 이유. 화면이 그대로 보여줄 수 있는 문장. */
    val metricsUnavailableReason: String?,
)

data class ABTestListResponse(
    val tests: List<ABTestResponse>,
    val totalCount: Int,
)

data class CreateABTestRequest(
    val videoId: Long? = null,
    val testName: String = "A/B 테스트",
    val metricType: String = "CTR",
    val variants: List<CreateVariantRequest> = emptyList(),
    /** 프론트의 표현형. 저장 시 metricType 으로 정규화한다. */
    val type: String? = null,
    val durationHours: Int? = null,
)

data class CreateVariantRequest(
    val variantName: String,
    val title: String? = null,
    val description: String? = null,
    val thumbnailUrl: String? = null,
)

data class UpdateABTestRequest(
    val testName: String? = null,
    val metricType: String? = null,
)

data class ABTestSummaryResponse(
    val totalTests: Int,
    val activeTests: Int,
    val completedTests: Int,
    /**
     * 완료된 실험들의 평균 CTR 개선율(%). **측정된 실험이 하나도 없으면 `null`.**
     *
     * 노출·클릭이 수집되지 않은 실험을 0% 로 세면 화면에 "평균 CTR 개선율 +0.0%" 라는
     * 성과 지표가 생긴다. 측정하지 않은 것과 개선이 없었던 것은 다르다.
     */
    val averageImprovement: Double?,
)

data class ABTestVideoResponse(
    val id: Long,
    val title: String,
    val thumbnailUrl: String?,
    val duration: Int?,
    val currentCtr: Double? = null,
    val views: Long? = null,
    val publishedAt: LocalDateTime? = null,
    val hasActiveTest: Boolean = false,
)
