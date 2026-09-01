package com.ongo.application.analytics.dto

import com.ongo.domain.analytics.AnomalyType
import java.time.LocalDateTime

/**
 * 영상 성과 점수. **계산할 수 없는 값은 전부 `null`** 이다.
 *
 * ## 왜 nullable 인가
 *
 * 모든 하위 점수는 **비율**이다 — 분모(조회수)나 비교 기준(채널 평균)이 없으면 값이
 * 성립하지 않는다. 예전에는 그 자리에 임의의 기준값을 넣었다: 조회수 분모에
 * `coerceAtLeast(1)`, 채널 평균에 `1.0`, 전환·공유 기준선에 `0.001`. 어디서도 측정하지
 * 않은 숫자로 점수를 만들어 화면에 "구독자 전환 100점" 같은 판정을 내보냈다.
 *
 * ## 필드를 지우지 않는 이유
 *
 * 이 응답을 읽는 화면이 이미 있다. 필드 이름과 `dataAvailable` 계약은 그대로 두고
 * **값만 nullable 로 바꾸고 사유 필드를 더한다.** 소비자는 `null` 을 `0` 으로 채우지 말고
 * "측정 불가"로 그려야 한다.
 */
data class PerformanceScoreResponse(
    val videoId: Long,
    /**
     * 하위 점수의 가중 평균. **계산 가능한 하위 점수가 하나도 없으면 `null`.**
     *
     * 측정된 하위 점수만으로 평균을 내고 **그 가중치 합으로 다시 정규화한다.** 미측정
     * 지표를 0 으로 넣으면 "그 축에서 최하위"라는 판정이 되어 총점이 구조적으로 낮아진다.
     * 시청 시간은 YouTube 만 수집하므로, 그렇게 하면 다른 플랫폼 크리에이터는 20% 를
     * 항상 잃는다.
     */
    val overallScore: Double?,
    /** 하위 점수. 계산할 수 없는 축은 `null` — [unavailableMetrics] 에 이유가 있다. */
    val breakdown: Map<String, Double?>,
    /**
     * **상위 몇 %인가.** 1~100 이며 **낮을수록 좋다**(최고 성과가 가장 작은 값).
     *
     * 분모는 **집계가 있는 영상만**이다. 미수집 영상은 점수가 0 이라 분모에 넣으면 남이
     * 측정되지 않았다는 사실이 내 순위가 된다.
     *
     * 비교할 영상이 자기 자신뿐이면 `null` 이다 — 그때의 "상위 100%"는 정보가 아니다.
     *
     * 예전에는 "이 영상보다 낮거나 같은 점수의 비율"(높을수록 좋음)이었고, 화면은 그것을
     * 그대로 `Top {값}%` 로 찍어 **최고 영상이 "Top 100%"** 로 보였다. 의미와 라벨이 서로
     * 반대였다.
     */
    val percentileRank: Double?,
    /**
     * `"up"` / `"down"` / `"stable"`. **추세를 판단할 기간이 없으면 `null`.**
     *
     * 앞뒤 절반의 평균을 비교하므로 최소 4일이 필요하다. 예전에는 그보다 짧으면
     * `"stable"` 을 돌려줬다 — 관측한 적 없는 추세를 "안정적"이라고 말한 것이다.
     * 게시 직후 하루치만 있는 영상이 전부 "안정"으로 표시됐다.
     */
    val trend: String?,
    val isAnomaly: Boolean,
    val anomalyDescription: String?,
    /**
     * 향후 7일 예상 조회수. **회귀선을 그을 점이 부족하면 `null`.**
     *
     * 예전에는 점이 2개 미만이면 `last7.sumOf { views }` — **이미 관측된 합계**를
     * 돌려줬다. 화면은 그것을 "7일 예상 조회수"로 그렸으므로, 어제 500회를 기록한
     * 영상은 "앞으로 7일간 500회 예상"이 됐다. 예측이 아니라 과거를 미래로 이름만
     * 바꾼 값이다.
     */
    val prediction7d: Long?,
    /**
     * 계산할 수 없었던 항목과 그 이유.
     *
     * 키는 하위 점수 이름(`viewVelocity`·`engagement`·`watchTime`·`conversion`·`share`)과
     * `overall`·`trend`·`prediction7d`·`percentileRank`.
     *
     * 값과 **같은 자리에서 함께 만든다.** 따로 두면 `null` 인데 이유가 없는 상태가 생기고,
     * 화면은 이유 없이 빈 칸만 그린다.
     */
    val unavailableMetrics: Map<String, String> = emptyMap(),
    /**
     * 점수를 계산할 **집계 데이터가 있었는가.**
     *
     * `false` 면 위 숫자들은 전부 계산 결과가 아니라 **채워 넣은 기본값**이다. 예전에는 이
     * 구분이 없어서, 아직 아무것도 수집되지 않은 영상이 `overallScore = 0`, `trend = stable`,
     * `prediction7d = 0` 인 **200 성공 응답**으로 내려갔다. 화면은 그것을 "0점짜리 영상,
     * 7일 예상 조회수 0회, 안정적 추세"로 그렸고, 크리에이터는 성과가 나쁘다고 읽었다 —
     * 실제로는 측정 자체가 없었다.
     *
     * 기존 필드를 지우거나 null 로 바꾸지 않는다. 이미 이 응답을 읽는 화면이 있고, 타입을
     * 바꾸면 그쪽이 깨진다. 대신 **판단 근거를 하나 더 준다.**
     */
    val dataAvailable: Boolean,
    /** `dataAvailable = false` 인 이유. 화면이 문구를 고르는 데 쓴다. */
    val unavailableReason: String? = null,
) {
    companion object {
        /** 이 영상에 업로드가 없다 — 게시된 곳이 없으니 측정할 대상도 없다. */
        const val REASON_NO_UPLOADS = "NO_UPLOADS"

        /** 업로드는 있으나 집계 행이 하나도 없다 — 아직 수집 전이거나 수집이 실패했다. */
        const val REASON_NO_ANALYTICS = "NO_ANALYTICS"
    }
}

data class AnomalyResponse(
    val videoId: Long,
    val videoTitle: String?,
    val anomalyType: AnomalyType,
    val severity: String,
    val description: String,
    val detectedAt: LocalDateTime,
)

data class AnomalyListResponse(
    val anomalies: List<AnomalyResponse>,
)
