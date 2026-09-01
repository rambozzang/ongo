package com.ongo.application.analytics

import com.ongo.domain.analytics.AnalyticsDaily
import com.ongo.domain.video.VideoUpload

/**
 * 집계 행 → 그 행이 올라간 플랫폼. **행마다 "이 숫자를 정말 수집하는가" 를 묻는다.**
 *
 * ## 왜 필요한가
 *
 * `analytics_daily` 는 숫자 컬럼이라 어댑터가 수집하지 않는 지표도 `0` 으로 저장된다.
 * 게다가 **이름이 다른 지표가 들어 있는 자리**도 있다.
 *
 * | 어댑터 | 필드 | 실제 값 |
 * |---|---|---|
 * | `TumblrClient.kt:141` | `views` | `total_notes` — 노트 총합 |
 * | `PinterestClient.kt:158` | `likes` | `SAVE` — 저장 수 |
 * | `PinterestClient.kt:160` | `shares` | `PIN_CLICK` — 클릭 수 |
 * | `DailymotionClient.kt:121` | `shares` | `bookmarks_total` — 북마크 수 |
 *
 * `AnalyticsDaily` 에는 `videoUploadId` 만 있어 행만 봐서는 플랫폼을 알 수 없다. 그래서
 * 합산하는 쪽이 업로드 매핑을 들고 와야 한다.
 *
 * ## 매핑이 없는 행
 *
 * fail-closed 로 제외한다. [PlatformMetricAvailability.isAvailable] 과 같은 정책이다 —
 * 알 수 없는 플랫폼을 "지원한다" 고 추정하면 다시 하드코딩 0 과 오매핑 값이 섞인다.
 *
 * ## 합계가 오염되지 않는 경우도 있다
 *
 * 미수집이 **하드코딩 0** 이면 합계에 더해도 값이 바뀌지 않는다. 그때 문제는 "그 지표를
 * 주는 플랫폼이 하나도 없을 때의 0" 이 실측과 구분되지 않는 것뿐이다. 반면 위 표처럼
 * **다른 뜻의 숫자**가 들어 있으면 합계 자체가 틀어진다 — 그래서 행 단위로 거른다.
 */
class AnalyticsRowPlatforms(private val platformByUploadId: Map<Long, String>) {

    /** 이 행의 [metric] 을 실제로 수집하는가. 매핑이 없으면 `false`. */
    fun reports(row: AnalyticsDaily, metric: String): Boolean {
        val platform = platformByUploadId[row.videoUploadId] ?: return false
        return PlatformMetricAvailability.isAvailable(platform, metric)
    }

    /**
     * 지표 **전부**를 수집하는 행인가.
     *
     * 참여율처럼 여러 지표의 합이 정의인 축에 쓴다. 하나라도 빠지면 그 행의 합계는
     * 정의대로 계산된 값이 아니다.
     */
    fun reportsAll(row: AnalyticsDaily, vararg metrics: String): Boolean =
        metrics.all { reports(row, it) }

    /** [metrics] 를 모두 수집하는 행만. */
    fun rowsReporting(rows: List<AnalyticsDaily>, vararg metrics: String): List<AnalyticsDaily> =
        rows.filter { row -> metrics.all { reports(row, it) } }

    /**
     * 측정된 행의 합계. **수집하는 행이 하나도 없으면 `null`.**
     *
     * 행은 있는데 합이 0 인 것은 **실측 0** 이므로 그대로 `0` 을 돌려준다.
     */
    fun sumMeasured(
        rows: List<AnalyticsDaily>,
        vararg metrics: String,
        pick: (AnalyticsDaily) -> Long,
    ): Long? {
        val measured = rowsReporting(rows, *metrics)
        return if (measured.isEmpty()) null else measured.sumOf(pick)
    }

    companion object {
        fun of(uploads: List<VideoUpload>): AnalyticsRowPlatforms = AnalyticsRowPlatforms(
            uploads.mapNotNull { upload -> upload.id?.let { it to upload.platform.name } }.toMap(),
        )
    }
}
