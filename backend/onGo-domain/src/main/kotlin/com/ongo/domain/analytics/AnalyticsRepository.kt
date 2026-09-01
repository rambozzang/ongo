package com.ongo.domain.analytics

import com.ongo.domain.video.Video
import java.time.LocalDate

interface AnalyticsRepository {
    fun findByVideoUploadIdAndDateRange(videoUploadId: Long, from: LocalDate, to: LocalDate): List<AnalyticsDaily>
    fun findByVideoUploadIdsAndDateRange(videoUploadIds: List<Long>, from: LocalDate, to: LocalDate): Map<Long, List<AnalyticsDaily>>
    fun getDashboardKpi(userId: Long, days: Int): DashboardKpi
    fun getTrendData(userId: Long, days: Int): List<TrendData>
    fun getTopVideos(userId: Long, days: Int, limit: Int): List<Video>
    /**
     * 게시 요일·시각별 **조회수 합계**. `요일명 → (0..23시 → 조회수)`.
     *
     * 축은 `video_uploads.published_at` 이다 — 집계일이나 행 저장 시각이 아니다.
     * 게시된 적 없는 업로드(`published_at IS NULL`)와 조회수를 보고하지 않는 플랫폼의
     * 행은 제외한다. 해당 칸에 데이터가 없으면 **키 자체가 없다** — 0 을 채우지 않는다.
     */
    fun getHeatmapData(userId: Long): Map<String, Map<Int, Long>>
    fun save(analytics: AnalyticsDaily): AnalyticsDaily

    /** 일반 분석 지표만 갱신한다. **수익 컬럼은 건드리지 않는다** — [updateRevenue] 소관이다. */
    fun upsert(analytics: AnalyticsDaily): AnalyticsDaily

    /**
     * 이미 존재하는 분석 행의 수익만 갱신한다. **행을 새로 만들지 않는다.**
     *
     * 일반 분석과 분리된 이유: 수익 조회는 별도 OAuth scope 를 쓰고 실패 확률이 다르다.
     * 같은 upsert 를 쓰면 수익 조회에 실패한 주기마다 기본값 0 이 실측값을 덮어쓴다.
     *
     * **INSERT 하지 않는 이유가 따로 있다.** 수익은 분석이 있는 하루에 딸린 속성이지
     * 혼자 서는 사실이 아니다. 예전에는 upsert 라 조회 기간 30일 전체에 행을 만들었고,
     * 그 행들은 조회수·노출수가 0 이고 `created_at` 이 동기화 시각이었다. 그 결과
     * `getOptimalPublishTimes` 가 "스케줄러가 도는 시각"을 최적 업로드 시간으로 추천했다.
     * 읽는 쪽마다 가짜 행을 걸러내는 규칙을 두는 대신, 애초에 만들지 않는다.
     *
     * 일반 분석이 그 날짜의 행을 만들면 다음 주기가 수익을 채운다 — 수익 조회는 매번
     * 최근 30일을 다시 묻기 때문에 한 주기 늦을 뿐 유실되지 않는다.
     *
     * **이미 [RevenueStatus.MEASURED] 인 행은 비-MEASURED 로 덮어쓰지 않는다.** 권한이
     * 끊기거나 확정이 지연돼도 이미 확인한 금액은 남는다. 승패는 DB 가 정한다.
     *
     * @return 실제로 갱신된 행이 있으면 true (행이 없으면 false)
     */
    fun updateRevenue(videoUploadId: Long, date: LocalDate, measurement: RevenueMeasurement): Boolean
    fun saveBatch(analytics: List<AnalyticsDaily>)
    fun findDailyAnalyticsByChannelIds(userId: Long, platform: com.ongo.common.enums.Platform?): List<AnalyticsDaily>
    fun findByVideoUploadIds(uploadIds: List<Long>): List<AnalyticsDaily>
    fun findAllByUserId(userId: Long): List<AnalyticsDaily>

    /** 해당 upload의 가장 최근 동기화된 날짜를 반환 (없으면 null) */
    fun findLatestDateByVideoUploadId(videoUploadId: Long): LocalDate?
    fun upsertChannelInsights(insights: ChannelInsightsDaily)
    fun findChannelInsights(userId: Long, platform: com.ongo.common.enums.Platform?, startDate: LocalDate, endDate: LocalDate): List<ChannelInsightsDaily>
    fun findCrossPlatformMetrics(userId: Long, days: Int): List<CrossPlatformRaw>

    /**
     * 기간별 일별 집계 (조회수, 좋아요, 댓글, 시청시간, 구독자, 수익).
     *
     * **새 호출부를 만들기 전에 읽을 것 — 이 집계에는 플랫폼이 없다.**
     *
     * 구현이 `video_uploads` 와 조인하지 않고 날짜로만 묶기 때문에, 어느 플랫폼의 행인지
     * 알 수 없다. 그래서 `TumblrClient.kt:141` 의 `total_notes`(좋아요+리블로그+답글 총합)가
     * 조회수 합계에, `PinterestClient.kt:158/160` 의 `SAVE`(저장)·`PIN_CLICK`(클릭)이
     * 좋아요·공유 합계에 그대로 섞인다. 수집하지 않는 플랫폼의 하드코딩 0 과 달리
     * **다른 뜻의 큰 숫자**라 합계 자체가 틀어진다.
     *
     * 지표별 가용성이 필요하면 [findByVideoUploadIdsAndDateRange] 로 원시 행을 읽고
     * `AnalyticsRowPlatforms` 로 행마다 플랫폼을 붙여라. 라이브 대시보드가 그 방식으로
     * 옮겨 가면서 현재 이 함수의 호출부는 없다.
     */
    fun getDailyAggregates(userId: Long, from: LocalDate, to: LocalDate): List<DailyAggregate>

    /**
     * 크로스 분석용: 영상별 + 플랫폼별 상세 메트릭 (thumbnailUrl, publishedAt 포함)
     */
    fun findCrossPlatformDetailMetrics(userId: Long, days: Int): List<CrossPlatformDetailRaw>
}

/**
 * 크로스 플랫폼 분석용 원시 데이터.
 * 영상별 + 플랫폼별로 집계된 analytics 데이터를 담는다.
 */
data class CrossPlatformRaw(
    val videoId: Long,
    val videoTitle: String?,
    val platform: String,
    val videoUploadId: Long,
    val views: Long,
    val likes: Long,
    val comments: Long,
    val shares: Long,
    val watchTimeSeconds: Long,
    val revenueMicro: Long,
    val impressions: Long,
    val avgViewDurationSeconds: Long,
)

/**
 * 라이브 대시보드용 일별 집계 데이터
 */
data class DailyAggregate(
    val date: LocalDate,
    val views: Long,
    val likes: Long,
    val comments: Long,
    val shares: Long,
    val watchTimeSeconds: Long,
    val subscriberGained: Long,
    val revenueMicro: Long,
)

/**
 * 크로스 분석용 영상별 + 플랫폼별 상세 데이터 (썸네일, 게시일 포함)
 */
data class CrossPlatformDetailRaw(
    val videoId: Long,
    val videoTitle: String?,
    val thumbnailUrls: List<String>,
    val publishedAt: java.time.LocalDateTime?,
    val platform: String,
    val videoUploadId: Long,
    val views: Long,
    val likes: Long,
    val comments: Long,
    val shares: Long,
    val watchTimeSeconds: Long,
    val revenueMicro: Long,
    val impressions: Long,
    val avgViewDurationSeconds: Long,
)
