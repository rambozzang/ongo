package com.ongo.application.analytics

import com.ongo.application.analytics.dto.AnomalyListResponse
import com.ongo.application.analytics.dto.AnomalyResponse
import com.ongo.application.analytics.dto.PerformanceScoreResponse
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.analytics.AnalyticsDaily
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.analytics.AnomalyType
import com.ongo.domain.analytics.VideoPerformanceScore
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.video.VideoUploadRepository
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.sqrt

@Service
class PerformanceScoreUseCase(
    private val analyticsRepository: AnalyticsRepository,
    private val videoRepository: VideoRepository,
    private val videoUploadRepository: VideoUploadRepository,
) {

    companion object {
        const val VIEW_VELOCITY = "viewVelocity"
        const val ENGAGEMENT = "engagement"
        const val WATCH_TIME = "watchTime"
        const val CONVERSION = "conversion"
        const val SHARE = "share"

        /** 하위 점수의 가중치. 계산된 축의 가중치 합으로 다시 정규화한다. */
        private val WEIGHTS = linkedMapOf(
            VIEW_VELOCITY to 0.30,
            ENGAGEMENT to 0.25,
            WATCH_TIME to 0.20,
            CONVERSION to 0.15,
            SHARE to 0.10,
        )

        private const val ANOMALY_Z_THRESHOLD = 2.0

        /**
         * 순위를 매기려면 **자기 자신 말고도** 측정된 영상이 있어야 한다.
         * 하나뿐이면 언제나 "상위 100%"라 정보가 없다.
         */
        private const val MIN_PEERS_FOR_PERCENTILE = 2

        /**
         * 추세와 예측에 **공통으로 요구하는 최소 관측일**.
         *
         * 두 값이 같은 수를 쓰는 이유는 서로 다르지만 결론이 같다.
         *
         * - **추세**: 앞뒤 절반의 평균을 비교하므로 양쪽에 최소 2일씩 있어야 한다.
         * - **예측**: 점이 둘이면 직선이 **항상 완전적합**(잔차 0)이라, 아무 두 점이나
         *   이어도 결정계수가 1 이 나온다. 적합도가 추세의 근거가 되지 못하므로 그 선을
         *   7일 앞으로 늘린 값은 예측이라 부를 수 없다. 점 3개도 자유도가 1 뿐이다.
         *
         * 둘을 하나로 묶어 두면 "추세는 판단 불가인데 예측은 있다" 같은 어긋난 조합이
         * 화면에 나오지 않는다.
         */
        private const val MIN_OBSERVED_DAYS = 4

        // ── 측정 불가 사유. 숫자가 아니라 문장이어야 한다 ──────────────────────

        const val REASON_NO_CHANNEL_VIEW_BASELINE = "채널에 조회수가 집계된 영상이 없어 비교 기준을 만들 수 없습니다"
        const val REASON_NO_VIEWS = "조회수가 집계되지 않아 비율의 분모가 없습니다"
        const val REASON_NO_WATCH_TIME = "시청 시간이 수집되지 않아 비교 기준을 만들 수 없습니다"
        const val REASON_NO_CONVERSION_BASELINE = "채널 전체 구독 전환이 집계되지 않아 비교 기준을 만들 수 없습니다"
        const val REASON_NO_SHARE_BASELINE = "채널 전체 공유가 집계되지 않아 비교 기준을 만들 수 없습니다"

        // ── 플랫폼이 그 지표를 아예 수집하지 않는 경우 ────────────────────────
        //
        // 기준선이 없는 것과 다른 상태다. 기준선은 "채널에 아직 실적이 없다" 이고,
        // 이쪽은 "이 영상이 올라간 곳에서는 그 숫자를 물어볼 수조차 없다" 이다.
        // 크리에이터가 할 수 있는 일이 다르므로 문구를 구분한다.

        const val REASON_ENGAGEMENT_NOT_COLLECTED =
            "좋아요·댓글·공유를 모두 수집하는 플랫폼에 게시된 기록이 없습니다"
        const val REASON_WATCH_TIME_NOT_COLLECTED =
            "시청 시간을 수집하는 플랫폼에 게시된 기록이 없습니다"
        const val REASON_SUBSCRIBER_NOT_COLLECTED =
            "구독 증가를 수집하는 플랫폼에 게시된 기록이 없습니다"
        const val REASON_SHARES_NOT_COLLECTED =
            "공유 수를 수집하는 플랫폼에 게시된 기록이 없습니다"

        const val REASON_NO_SUBSCORE = "계산할 수 있는 하위 점수가 하나도 없습니다"
        const val REASON_TREND_TOO_SHORT = "추세를 판단할 만큼 기간이 쌓이지 않았습니다"
        const val REASON_PREDICTION_TOO_SHORT = "회귀선을 그을 만큼 관측일이 쌓이지 않았습니다"
        const val REASON_NO_COMPARABLE_PEERS = "같은 지표로 측정된 비교 대상 영상이 부족합니다"
    }

    /**
     * 집계 행 → 그 행이 올라간 플랫폼. **행마다 "이 숫자를 정말 수집하는가" 를 묻는다.**
     *
     * ## 왜 필요한가
     *
     * `analytics_daily` 는 숫자 컬럼이라 어댑터가 수집하지 않는 지표도 `0` 으로 저장된다.
     * 13개 어댑터 중 시청 시간·구독 증가를 실제로 조회하는 것은 `YouTubeClient` 하나뿐이고,
     * 공유는 Facebook·WordPress·Vimeo 가, 댓글은 Pinterest 가 주지 않는다
     * ([PlatformMetricAvailability]).
     *
     * 그 `0` 들을 관측값으로 섞으면 두 가지가 한꺼번에 무너진다.
     *
     * - **점수**: 하드코딩 0 이 분자에 들어가 그 축 점수가 실제보다 낮아진다.
     * - **기준선**: 채널 평균의 분모에 미수집 행이 들어가 평균이 낮아지고, 그러면 비교
     *   대상 전체가 실제보다 좋아 보인다.
     *
     * TikTok 과 YouTube 에 함께 올린 영상이라면 TikTok 행의 시청 시간 0 이 YouTube 의
     * 실측을 절반으로 희석했다.
     *
     * ## 매핑이 없는 행
     *
     * fail-closed 로 제외한다. [PlatformMetricAvailability.isAvailable] 과 같은 정책이다 —
     * 알 수 없는 플랫폼을 "지원한다" 고 추정하면 다시 하드코딩 0 이 섞인다.
     */
    private class RowPlatforms(private val byUploadId: Map<Long, String>) {
        fun reports(row: AnalyticsDaily, metric: String): Boolean {
            val platform = byUploadId[row.videoUploadId] ?: return false
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
    }

    /**
     * 하위 점수와 그 미측정 사유. **값과 사유를 함께 들고 다닌다.**
     *
     * 따로 두면 `null` 인데 사유가 없는 상태가 생기고, 화면은 이유 없이 빈 칸만 그린다.
     */
    private data class Breakdown(
        val scores: Map<String, Double?>,
        val reasons: Map<String, String>,
    ) {
        /**
         * 실제로 계산된 축의 이름. **비교 모집단을 가르는 열쇠다.**
         *
         * {조회속도, 참여율} 로만 매긴 점수와 다섯 축 전부로 매긴 점수는 척도가 다르다.
         * 같은 축 집합끼리만 비교해야 백분위와 z-score 가 의미를 갖는다.
         */
        val measuredMetrics: Set<String> get() = scores.filterValues { it != null }.keys

        /**
         * 측정된 축만의 가중 평균. 그 축들의 **가중치 합으로 정규화**한다.
         *
         * 미측정 축을 0 으로 넣으면 "그 축에서 최하위"라는 판정이 된다. 시청 시간은
         * YouTube 만 수집하므로 다른 플랫폼 크리에이터는 총점의 20% 를 항상 잃는다.
         */
        val overall: Double? get() {
            val measured = scores.entries.mapNotNull { (key, value) -> value?.let { key to it } }
            if (measured.isEmpty()) return null
            val weightSum = measured.sumOf { (key, _) -> WEIGHTS.getValue(key) }
            if (weightSum <= 0.0) return null
            return measured.sumOf { (key, value) -> value * WEIGHTS.getValue(key) } / weightSum
        }
    }

    /**
     * 채널 전체에서 뽑은 비교 기준. **관측되지 않은 기준은 `null`** 이다.
     *
     * 예전에는 여기에 `1.0`(채널 평균 조회수·시청시간)과 `0.001`(전환율·공유율)을 넣었다.
     * 그 숫자들은 어디서도 측정한 적이 없고, 비율의 분모로 들어가 점수를 통째로 바꿨다 —
     * 예컨대 채널 평균 조회수가 `1.0` 이면 조회수 2회짜리 영상이 조회속도 100점을 받았다.
     */
    private data class ChannelBaseline(
        val avgViewsPerRow: Double?,
        val avgWatchTimePerRow: Double?,
        val conversionRate: Double?,
        val shareRate: Double?,
    ) {
        companion object {
            /**
             * **축마다 다른 행 집합**에서 기준선을 만든다.
             *
             * 예전에는 넷 모두 `rows` 전체를 썼다. 그래서 시청 시간 평균의 분모에
             * TikTok·Instagram 행이 들어갔고, 그 행들의 하드코딩 0 이 YouTube 실측을
             * 희석해 **채널 평균이 실제보다 낮아졌다.** 기준선이 낮으면 비교 대상 전체가
             * 실제보다 좋아 보인다 — 점수 하나가 아니라 순위 전체가 틀어진다.
             *
             * 분자와 분모는 반드시 **같은 필터를 통과한 행**에서 나온다.
             */
            fun from(rows: List<AnalyticsDaily>, platforms: RowPlatforms): ChannelBaseline {
                if (rows.isEmpty()) return ChannelBaseline(null, null, null, null)

                // 조회수는 모든 플랫폼이 수집한다. 플랫폼을 알 수 없는 행만 빠진다.
                val viewRows = rows.filter { platforms.reports(it, PlatformMetricAvailability.VIEWS) }
                val totalViews = viewRows.sumOf { it.views.toLong() }

                val watchRows = rows.filter {
                    platforms.reports(it, PlatformMetricAvailability.WATCH_TIME_SECONDS)
                }
                val totalWatch = watchRows.sumOf { it.watchTimeSeconds }

                val subRows = rows.filter {
                    platforms.reportsAll(
                        it,
                        PlatformMetricAvailability.SUBSCRIBER_GAINED,
                        PlatformMetricAvailability.VIEWS,
                    )
                }
                val subViews = subRows.sumOf { it.views.toLong() }
                val totalSubs = subRows.sumOf { it.subscriberGained.toLong() }

                val shareRows = rows.filter {
                    platforms.reportsAll(
                        it,
                        PlatformMetricAvailability.SHARES,
                        PlatformMetricAvailability.VIEWS,
                    )
                }
                val shareViews = shareRows.sumOf { it.views.toLong() }
                val totalShares = shareRows.sumOf { it.shares.toLong() }

                return ChannelBaseline(
                    // 0 은 기준이 될 수 없다. 그것으로 나누면 어떤 값도 무한대가 된다.
                    avgViewsPerRow = if (totalViews > 0) totalViews.toDouble() / viewRows.size else null,
                    avgWatchTimePerRow = if (totalWatch > 0) totalWatch.toDouble() / watchRows.size else null,
                    conversionRate = if (subViews > 0 && totalSubs > 0) totalSubs.toDouble() / subViews else null,
                    shareRate = if (shareViews > 0 && totalShares > 0) totalShares.toDouble() / shareViews else null,
                )
            }
        }
    }

    /** 비교 모집단의 한 항목. 점수만으로는 척도가 같은지 알 수 없어 축 집합을 함께 든다. */
    private data class PeerScore(val metrics: Set<String>, val score: Double)

    fun getPerformanceScore(userId: Long, videoId: Long): PerformanceScoreResponse {
        val video = videoRepository.findById(videoId) ?: throw NotFoundException("영상", videoId)
        if (video.userId != userId) {
            throw ForbiddenException("해당 영상에 대한 접근 권한이 없습니다")
        }
        val uploads = videoUploadRepository.findByVideoId(videoId)
        val uploadIds = uploads.mapNotNull { it.id }

        if (uploadIds.isEmpty()) {
            return emptyScoreResponse(videoId, PerformanceScoreResponse.REASON_NO_UPLOADS)
        }

        // Get analytics for this video
        val videoAnalytics = analyticsRepository.findByVideoUploadIds(uploadIds)

        /*
         * 집계가 하나도 없으면 **점수를 만들지 않는다.**
         *
         * 예전에는 그대로 진행해 모든 하위 점수 0 으로 계산하고 200 을 돌려줬다. 그 응답의
         * `percentileRank` 가 특히 나빴다 — 신규 계정처럼 모든 영상의 점수가 0 이면
         * `count { it <= 0 }` 이 전체 개수가 되어 **"상위 100%"** 가 나온다. 아무것도 측정되지
         * 않은 영상이 최상위 성과처럼 보였다.
         *
         * `trend = "stable"` 도 마찬가지다. 관측한 적 없는 추세를 "안정적"이라고 말한다.
         */
        if (videoAnalytics.isEmpty()) {
            return emptyScoreResponse(videoId, PerformanceScoreResponse.REASON_NO_ANALYTICS)
        }

        // Get all channel analytics for percentile/anomaly calculation
        val allChannelAnalytics = analyticsRepository.findAllByUserId(userId)

        /*
         * 업로드 → 영상·플랫폼 매핑을 **한 번의 조회로** 함께 만든다.
         * `findByUserId` 가 이미 `platform` 컬럼을 함께 선택하므로 추가 쿼리가 없다
         * (`VideoUploadJooqRepository:206`).
         */
        val channelUploads = videoUploadRepository.findByUserId(userId)
        val videoIdByUploadId = channelUploads
            .mapNotNull { upload -> upload.id?.let { it to upload.videoId } }
            .toMap()
        val platforms = RowPlatforms(
            (channelUploads + uploads).mapNotNull { upload -> upload.id?.let { it to upload.platform.name } }.toMap(),
        )

        // 기준선과 모집단은 **한 번만** 만들어 순위와 이상 감지가 같은 것을 보게 한다.
        val baseline = ChannelBaseline.from(allChannelAnalytics, platforms)
        val peers = measuredPeerScores(videoIdByUploadId, allChannelAnalytics, baseline, platforms)

        val score = calculateScore(videoId, videoAnalytics, baseline, peers, platforms)
        // 다시 계산하지 않고 **결과에서 읽는다.** 두 번 계산하면 언젠가 한쪽만 바뀐다.
        val measuredMetrics = measuredMetricsOf(score)

        // 총점이 없으면 순위도 없다. 같은 축으로 매겨진 비교군하고만 견준다.
        val percentileRank = score.overallScore?.let {
            calculateTopPercent(it, comparablePeers(peers, measuredMetrics))
        }
        val trend = calculateTrend(videoAnalytics)

        val anomalyDescription = if (score.isAnomaly) {
            getAnomalyDescription(score.anomalyType)
        } else null

        val unavailable = buildMap {
            putAll(score.unavailableMetrics)
            if (trend == null) put("trend", REASON_TREND_TOO_SHORT)
            if (score.predictedViews7d == null) put("prediction7d", REASON_PREDICTION_TOO_SHORT)
            if (percentileRank == null) put("percentileRank", REASON_NO_COMPARABLE_PEERS)
        }

        return PerformanceScoreResponse(
            videoId = videoId,
            overallScore = score.overallScore?.let { Math.round(it * 10) / 10.0 },
            breakdown = mapOf(
                VIEW_VELOCITY to score.viewVelocityScore?.let { Math.round(it * 10) / 10.0 },
                ENGAGEMENT to score.engagementScore?.let { Math.round(it * 10) / 10.0 },
                WATCH_TIME to score.watchTimeScore?.let { Math.round(it * 10) / 10.0 },
                CONVERSION to score.conversionScore?.let { Math.round(it * 10) / 10.0 },
                SHARE to score.shareScore?.let { Math.round(it * 10) / 10.0 },
            ),
            // 반올림은 계산부에서 이미 했다. 여기서 다시 하면 null 을 0 으로 만들게 된다.
            percentileRank = percentileRank,
            trend = trend,
            isAnomaly = score.isAnomaly,
            anomalyDescription = anomalyDescription,
            prediction7d = score.predictedViews7d,
            unavailableMetrics = unavailable,
            /*
             * **집계 행은 있었다**는 뜻이다. 개별 지표를 계산할 수 있었는지는 별개이며
             * `breakdown` 의 `null` 과 [unavailableMetrics] 가 말한다.
             *
             * 둘을 뭉치면 "조회수만 있고 시청시간은 없는" 흔한 경우에 응답 전체가 미수집이
             * 되어, 실제로 측정된 조회속도·참여율까지 화면에서 사라진다.
             */
            dataAvailable = true,
        )
    }

    fun getAnomalies(userId: Long): AnomalyListResponse {
        val channelUploads = videoUploadRepository.findByUserId(userId)
        val allVideoIds = channelUploads.groupBy { it.videoId }.keys
        val allChannelAnalytics = analyticsRepository.findAllByUserId(userId)

        // 순위·이상 감지가 점수와 **같은 플랫폼 판정**을 쓰도록 매핑을 한 번만 만든다.
        val videoIdByUploadId = channelUploads
            .mapNotNull { upload -> upload.id?.let { it to upload.videoId } }
            .toMap()
        val platforms = RowPlatforms(
            channelUploads.mapNotNull { upload -> upload.id?.let { it to upload.platform.name } }.toMap(),
        )

        // 모집단은 루프 **밖에서 한 번만** 만든다. 영상마다 다시 만들면 같은 값을 반복해서
        // 계산할 뿐 아니라, 언젠가 한쪽만 바뀌어 척도가 어긋날 여지를 남긴다.
        val baseline = ChannelBaseline.from(allChannelAnalytics, platforms)
        val peers = measuredPeerScores(videoIdByUploadId, allChannelAnalytics, baseline, platforms)

        val anomalies = mutableListOf<AnomalyResponse>()

        for (vid in allVideoIds) {
            val video = videoRepository.findById(vid) ?: continue
            val uploads = videoUploadRepository.findByVideoId(vid)
            val uploadIds = uploads.mapNotNull { it.id }
            val videoAnalytics = analyticsRepository.findByVideoUploadIds(uploadIds)

            val score = calculateScore(vid, videoAnalytics, baseline, peers, platforms)
            val detectedAnomalyType = score.anomalyType
            val overall = score.overallScore
            // 총점이 없으면 이상 여부도 판단할 수 없다. `isAnomaly` 도 이미 false 다.
            if (score.isAnomaly && detectedAnomalyType != null && overall != null) {
                val severity = when {
                    overall >= 90 -> "critical"
                    overall >= 70 -> "warning"
                    else -> "info"
                }
                anomalies.add(
                    AnomalyResponse(
                        videoId = vid,
                        videoTitle = video.title,
                        anomalyType = detectedAnomalyType,
                        severity = severity,
                        description = getAnomalyDescription(detectedAnomalyType) ?: "",
                        detectedAt = score.calculatedAt,
                    )
                )
            }
        }

        return AnomalyListResponse(anomalies = anomalies)
    }

    /**
     * 비교 모집단: **측정된 영상별** canonical 점수와 그 점수를 만든 축 집합.
     *
     * 순위와 이상 감지가 **같은 목록**을 쓴다. 각자 만들면 한쪽만 고쳐져 척도가 다시
     * 어긋난다 — 실제로 그렇게 어긋나 있었다.
     *
     * ## 업로드가 아니라 영상 단위인 이유
     *
     * `allChannelAnalytics` 행에는 `videoUploadId` 만 있다. 예전 이상 감지는 그것으로 바로
     * 묶어(`groupBy { it.videoUploadId }`) **업로드 하나를 영상 하나로** 셌다. 그런데 비교
     * 상대인 요청 영상의 점수는 그 영상의 **모든 업로드를 합친** 값이다. 3개 플랫폼에 올린
     * 영상은 비교군에서 각각 1/3 짜리 셋으로 쪼개져, 합쳐진 내 점수가 늘 높아 보였다.
     *
     * `findByUserId` 가 `id` 와 `video_id` 를 함께 주므로(`VideoUploadJooqRepository:202-206`)
     * 업로드 → 영상 매핑은 추가 쿼리 없이 만든다.
     *
     * ## 축 집합을 함께 드는 이유
     *
     * 총점을 낼 수 없는 영상(계산된 축이 하나도 없음)은 아예 빠진다.
     */
    private fun measuredPeerScores(
        videoIdByUploadId: Map<Long, Long>,
        allChannelAnalytics: List<AnalyticsDaily>,
        baseline: ChannelBaseline,
        platforms: RowPlatforms,
    ): List<PeerScore> =
        allChannelAnalytics
            .groupBy { videoIdByUploadId[it.videoUploadId] }
            // 키가 null 인 묶음은 이 사용자의 업로드로 매핑되지 않은 행이다. 섞으면 안 된다.
            .filterKeys { it != null }
            .values
            .filter { it.isNotEmpty() }
            .mapNotNull { rows ->
                val breakdown = calculateBreakdown(rows, baseline, platforms)
                breakdown.overall?.let { PeerScore(breakdown.measuredMetrics, it) }
            }

    /**
     * **같은 축으로 매겨진** 비교 대상만 남긴다.
     *
     * {조회속도, 참여율} 두 축으로 낸 점수와 다섯 축 전부로 낸 점수는 서로 다른 척도다.
     * 섞어서 순위를 매기면 "측정된 지표가 적은 영상이 유리하다" 같은 일이 벌어지고,
     * z-score 는 척도가 다른 분포와 비교돼 임계값 2.0 이 아무것도 뜻하지 않는다.
     */
    private fun comparablePeers(peers: List<PeerScore>, metrics: Set<String>): List<Double> =
        peers.filter { it.metrics == metrics }.map { it.score }

    /** 실제로 계산된 축. 점수 객체에서 직접 읽어 재계산과 어긋나지 않게 한다. */
    private fun measuredMetricsOf(score: VideoPerformanceScore): Set<String> = buildSet {
        if (score.viewVelocityScore != null) add(VIEW_VELOCITY)
        if (score.engagementScore != null) add(ENGAGEMENT)
        if (score.watchTimeScore != null) add(WATCH_TIME)
        if (score.conversionScore != null) add(CONVERSION)
        if (score.shareScore != null) add(SHARE)
    }

    /**
     * **상위 몇 %인가**(1~100, 낮을수록 좋음). 비교할 대상이 없으면 `null`.
     *
     * ## 방향
     *
     * "나와 같거나 높은 점수의 비율"이다. 예전 값은 그 반대("낮거나 같은 비율")여서
     * 최고 점수 영상이 100 을 받고 화면은 그것을 **"Top 100%"** 로 찍었다 — 가장 잘한
     * 영상이 가장 못한 것처럼 보였다.
     *
     * ## 비교 대상이 하나뿐일 때
     *
     * 자기 자신만 있으면 순위는 언제나 "상위 100%"다. 그것은 정보가 아니라 자리채움이므로
     * `null` 로 두고 화면이 배지를 감춘다.
     */
    private fun calculateTopPercent(overallScore: Double, peerScores: List<Double>): Double? {
        if (peerScores.size < MIN_PEERS_FOR_PERCENTILE) return null

        // 나와 같거나 높은 점수의 비율. 최고 점수면 1/n → 가장 작은 값이 된다.
        val atLeastAsGood = peerScores.count { it >= overallScore }
        return Math.round((atLeastAsGood.toDouble() / peerScores.size) * 1000) / 10.0
    }

    /**
     * @param peerScores 이상 감지 z-score 의 비교 모집단. [measuredPeerScores] 가 만든
     *   **canonical 점수 목록**이어야 한다. 여기에 다른 공식으로 만든 값을 넘기면 z-score 가
     *   척도가 다른 분포와 비교돼 임계값(2.0)이 아무 의미도 갖지 않는다.
     */
    private fun calculateScore(
        videoId: Long,
        videoAnalytics: List<AnalyticsDaily>,
        baseline: ChannelBaseline,
        peers: List<PeerScore>,
        platforms: RowPlatforms,
    ): VideoPerformanceScore {
        if (videoAnalytics.isEmpty()) {
            return VideoPerformanceScore(
                videoId = videoId,
                overallScore = null,
                viewVelocityScore = null,
                engagementScore = null,
                watchTimeScore = null,
                conversionScore = null,
                shareScore = null,
                unavailableMetrics = mapOf("overall" to REASON_NO_SUBSCORE),
            )
        }

        val breakdown = calculateBreakdown(videoAnalytics, baseline, platforms)
        val overallScore = breakdown.overall

        /*
         * 이상 감지는 **같은 축으로 매겨진** 비교군과만 비교한다.
         *
         * z-score 는 분포의 평균·표준편차로 계산하므로 척도가 다르면 임계값 2.0 이
         * 아무것도 뜻하지 않는다 — 정상 영상이 "바이럴"로 뜨거나 진짜 급등을 놓친다.
         * 총점을 못 낸 영상은 비교 자체가 불가능하다.
         */
        val (isAnomaly, anomalyType) = if (overallScore != null) {
            detectAnomaly(overallScore, comparablePeers(peers, breakdown.measuredMetrics), videoAnalytics, platforms)
        } else {
            Pair(false, null)
        }

        val reasons = buildMap {
            putAll(breakdown.reasons)
            if (overallScore == null) put("overall", REASON_NO_SUBSCORE)
        }

        return VideoPerformanceScore(
            videoId = videoId,
            overallScore = overallScore?.coerceIn(0.0, 100.0),
            viewVelocityScore = breakdown.scores[VIEW_VELOCITY],
            engagementScore = breakdown.scores[ENGAGEMENT],
            watchTimeScore = breakdown.scores[WATCH_TIME],
            conversionScore = breakdown.scores[CONVERSION],
            shareScore = breakdown.scores[SHARE],
            isAnomaly = isAnomaly,
            anomalyType = anomalyType,
            predictedViews7d = predictViews7d(videoAnalytics),
            unavailableMetrics = reasons,
        )
    }

    /**
     * 하위 점수 다섯 축을 한 번에 계산한다. **계산할 수 없는 축은 `null` 과 사유.**
     *
     * 축마다 필요한 것이 다르다.
     *
     * - 조회속도·시청시간: **채널 기준선**만 있으면 된다. 이 영상의 값이 0 이어도 그것은
     *   측정된 사실이므로 0 점을 준다.
     * - 참여율·전환·공유: 이 영상의 **조회수(분모)** 가 있어야 한다. 조회가 0 이면
     *   비율 자체가 정의되지 않는다 — 예전에는 `coerceAtLeast(1)` 로 분모를 1 로 만들어
     *   "참여율 0%" 라는 관측을 지어냈다.
     * - 전환·공유는 그 위에 **채널 기준선**도 필요하다.
     */
    private fun calculateBreakdown(
        videoAnalytics: List<AnalyticsDaily>,
        baseline: ChannelBaseline,
        platforms: RowPlatforms,
    ): Breakdown {
        val scores = mutableMapOf<String, Double?>()
        val reasons = mutableMapOf<String, String>()

        fun put(key: String, value: Double?, reason: String) {
            scores[key] = value
            if (value == null) reasons[key] = reason
        }

        // ── 조회속도: 첫 48시간 조회수를 채널 평균과 견준다 ────────────────────
        val viewRows = videoAnalytics.filter { platforms.reports(it, PlatformMetricAvailability.VIEWS) }
        val views48h = viewRows.sortedBy { it.date }.take(2).sumOf { it.views.toLong() }
        put(
            VIEW_VELOCITY,
            if (viewRows.isEmpty()) null else baseline.avgViewsPerRow?.let { ratioScore(views48h.toDouble(), it) },
            REASON_NO_CHANNEL_VIEW_BASELINE,
        )

        /*
         * ── 참여율 ───────────────────────────────────────────────────────────
         *
         * 정의가 **좋아요 + 댓글 + 공유** 이므로, 셋 중 하나라도 그 플랫폼이 주지 않으면
         * 그 행의 합계는 정의대로 계산된 값이 아니다. 행 자체를 뺀다 — 분자만 빼고 조회수는
         * 분모에 남기면 참여율이 실제보다 낮아진다.
         *
         * Facebook·WordPress·Vimeo 는 공유를, Pinterest 는 댓글을 주지 않는다.
         */
        val engagementRows = videoAnalytics.filter {
            platforms.reportsAll(
                it,
                PlatformMetricAvailability.LIKES,
                PlatformMetricAvailability.COMMENTS,
                PlatformMetricAvailability.SHARES,
                PlatformMetricAvailability.VIEWS,
            )
        }
        val engagementViews = engagementRows.sumOf { it.views.toLong() }
        val engagements = engagementRows.sumOf { (it.likes + it.commentsCount + it.shares).toLong() }
        put(
            ENGAGEMENT,
            if (engagementRows.isNotEmpty() && engagementViews > 0) {
                // 참여율 10% 를 100 점으로 본다.
                ((engagements.toDouble() / engagementViews) * 1000).coerceIn(0.0, 100.0)
            } else null,
            if (engagementRows.isEmpty()) REASON_ENGAGEMENT_NOT_COLLECTED else REASON_NO_VIEWS,
        )

        // ── 시청 시간: YouTube 만 수집한다 ───────────────────────────────────
        val watchRows = videoAnalytics.filter {
            platforms.reports(it, PlatformMetricAvailability.WATCH_TIME_SECONDS)
        }
        put(
            WATCH_TIME,
            if (watchRows.isEmpty()) {
                null
            } else {
                val avgWatchTime = watchRows.sumOf { it.watchTimeSeconds }.toDouble() / watchRows.size
                baseline.avgWatchTimePerRow?.let { ratioScore(avgWatchTime, it) }
            },
            if (watchRows.isEmpty()) REASON_WATCH_TIME_NOT_COLLECTED else REASON_NO_WATCH_TIME,
        )

        // ── 구독 전환: 분모(조회수)와 기준선(채널 전환율) 둘 다 필요하다 ──────
        val subRows = videoAnalytics.filter {
            platforms.reportsAll(
                it,
                PlatformMetricAvailability.SUBSCRIBER_GAINED,
                PlatformMetricAvailability.VIEWS,
            )
        }
        val subViews = subRows.sumOf { it.views.toLong() }
        val subs = subRows.sumOf { it.subscriberGained.toLong() }
        put(
            CONVERSION,
            if (subRows.isNotEmpty() && subViews > 0 && baseline.conversionRate != null) {
                ratioScore(subs.toDouble() / subViews, baseline.conversionRate)
            } else null,
            when {
                subRows.isEmpty() -> REASON_SUBSCRIBER_NOT_COLLECTED
                subViews == 0L -> REASON_NO_VIEWS
                else -> REASON_NO_CONVERSION_BASELINE
            },
        )

        // ── 공유율 ───────────────────────────────────────────────────────────
        val shareRows = videoAnalytics.filter {
            platforms.reportsAll(it, PlatformMetricAvailability.SHARES, PlatformMetricAvailability.VIEWS)
        }
        val shareViews = shareRows.sumOf { it.views.toLong() }
        val shares = shareRows.sumOf { it.shares.toLong() }
        put(
            SHARE,
            if (shareRows.isNotEmpty() && shareViews > 0 && baseline.shareRate != null) {
                ratioScore(shares.toDouble() / shareViews, baseline.shareRate)
            } else null,
            when {
                shareRows.isEmpty() -> REASON_SHARES_NOT_COLLECTED
                shareViews == 0L -> REASON_NO_VIEWS
                else -> REASON_NO_SHARE_BASELINE
            },
        )

        return Breakdown(scores, reasons)
    }

    /**
     * 기준선 대비 비율을 0~100 점으로. **기준선과 같으면 50 점**이다.
     *
     * 호출 전에 `baseline > 0` 을 보장할 것 — 0 으로 나누면 어떤 값도 무한대가 된다.
     */
    private fun ratioScore(value: Double, baseline: Double): Double =
        ((value / baseline) * 50).coerceIn(0.0, 100.0)

    /*
     * `calculateOverallScore` / `calculateAllVideoScores` 는 **삭제했다.**
     *
     * 조회수/참여율 2개 지표만 쓰는 별도 공식이었고, 업로드 단위로 묶었다. 순위와 이상
     * 감지가 그것을 비교군으로 쓰는 동안 요청 영상은 5개 지표 가중식으로 채점됐다.
     * 남겨 두면 언젠가 다시 "비교용 점수"로 불려 같은 불일치가 재발한다.
     * 점수 공식은 [weightedOverallScore] 하나뿐이다.
     */

    /**
     * 이상 여부와 **그 원인 분류**.
     *
     * ## 두 단계가 서로 다른 근거를 쓴다
     *
     * - **여부**: `overallScore` 의 z-score. 그 점수는 이미 지원 플랫폼 행으로만 계산되고
     *   같은 축 집합의 비교군하고만 견준다.
     * - **분류**: 아래 비율. 이쪽이 필터 없이 전체 행을 더하고 있었다.
     *
     * ## 무엇이 거짓이었나
     *
     * ```
     * val totalViews = videoAnalytics.sumOf { it.views }
     * val totalShares = videoAnalytics.sumOf { it.shares }
     * val totalEngagement = videoAnalytics.sumOf { it.likes + it.commentsCount + it.shares }
     * ```
     *
     * Facebook·WordPress·Vimeo 는 공유를, Pinterest 는 댓글을 주지 않는다. 그 하드코딩
     * 0 이 분자에 들어가고 그 행의 조회수는 분모에 남았다. **오차는 항상 희석 방향**이라
     * 임계값(공유 10%, 참여 20%)을 넘지 못하고 구체적 분류가 일반 `VIRAL_SPIKE` 로
     * 떨어졌다 — 공유로 퍼진 영상이 "바이럴"로만 보고됐다.
     *
     * 이제 축마다 **자기를 수집하는 행에서만** 분자와 분모를 만든다. 판정할 행이 없으면
     * 그 분류를 주장하지 않고 다음으로 넘어간다.
     *
     * `VIRAL_SPIKE` 가 최종 기본값인 것은 그대로 둔다. 조회수는 모든 플랫폼이 수집하므로
     * "점수가 비정상적으로 높다" 는 사실 자체는 이미 z-score 가 확인했다.
     */
    private fun detectAnomaly(
        score: Double,
        allScores: List<Double>,
        videoAnalytics: List<AnalyticsDaily>,
        platforms: RowPlatforms,
    ): Pair<Boolean, AnomalyType?> {
        if (allScores.size < 3) return Pair(false, null)

        val mean = allScores.average()
        val variance = allScores.map { (it - mean) * (it - mean) }.average()
        val stddev = sqrt(variance)
        if (stddev == 0.0) return Pair(false, null)

        val zScore = (score - mean) / stddev

        if (zScore > ANOMALY_Z_THRESHOLD) {
            return Pair(true, classifySpike(videoAnalytics, platforms))
        } else if (zScore < -ANOMALY_Z_THRESHOLD) {
            return Pair(true, AnomalyType.UNUSUAL_DROP)
        }

        return Pair(false, null)
    }

    /**
     * 급등의 원인 분류. 각 비율은 **그 지표를 수집하는 행에서만** 분자와 분모를 만든다.
     *
     * 분모가 없으면 그 분류를 시험할 수 없다는 뜻이므로 조용히 넘어간다 — 없는 근거로
     * 유형을 단정하지 않는다.
     */
    private fun classifySpike(
        videoAnalytics: List<AnalyticsDaily>,
        platforms: RowPlatforms,
    ): AnomalyType {
        val shareRows = videoAnalytics.filter {
            platforms.reportsAll(it, PlatformMetricAvailability.SHARES, PlatformMetricAvailability.VIEWS)
        }
        val shareViews = shareRows.sumOf { it.views.toLong() }
        if (shareViews > 0 && shareRows.sumOf { it.shares.toLong() } > shareViews * 0.1) {
            return AnomalyType.SHARE_SPIKE
        }

        // 참여율은 좋아요+댓글+공유 전체가 정의다. 하나라도 빠진 행은 합계가 정의대로가 아니다.
        val engagementRows = videoAnalytics.filter {
            platforms.reportsAll(
                it,
                PlatformMetricAvailability.LIKES,
                PlatformMetricAvailability.COMMENTS,
                PlatformMetricAvailability.SHARES,
                PlatformMetricAvailability.VIEWS,
            )
        }
        val engagementViews = engagementRows.sumOf { it.views.toLong() }
        val engagement = engagementRows.sumOf { (it.likes + it.commentsCount + it.shares).toLong() }
        if (engagementViews > 0 && engagement > engagementViews * 0.2) {
            return AnomalyType.ENGAGEMENT_SURGE
        }

        return AnomalyType.VIRAL_SPIKE
    }

    /**
     * 향후 7일 예상 조회수. **회귀선을 그을 점이 부족하면 `null`.**
     *
     * 예전에는 점이 2개 미만이면 `last7.sumOf { views }` — **이미 관측된 합계**를 돌려줬고
     * 화면은 그것을 "7일 예상 조회수"로 그렸다. 어제 500회를 기록한 영상이 "앞으로 7일간
     * 500회 예상"이 됐다. 예측이 아니라 과거를 미래로 이름만 바꾼 값이다.
     *
     * 임계값은 [MIN_OBSERVED_DAYS] 로 추세와 통일했다. 점 2개는 직선이 **항상 완전적합**
     * 이라 적합도가 아무것도 말해 주지 못하고, 그 선을 7일 앞으로 늘린 값은 예측이라
     * 부를 근거가 없다.
     *
     * ## 조회수가 계속 0 이면
     *
     * 기울기 0, 절편 0 → **예측도 0** 이다. 그것은 자리채움이 아니라 관측에서 나온 결과다.
     * `analytics_daily.views` 는 수집 상태 컬럼도, 미수집 플랫폼도 없다 — `revenue_micro`
     * 의 `revenue_status` 나 시청 시간의 플랫폼 허용 목록 같은 장치가 views 에는 없다.
     * 행이 있다는 것 자체가 그 날 수집이 돌았다는 뜻이므로 `0` 은 "0회 조회됐다" 는
     * 관측이다. 여기서 null 로 바꾸면 실제 관찰을 버리게 된다.
     */
    private fun predictViews7d(videoAnalytics: List<AnalyticsDaily>): Long? {
        val last7 = videoAnalytics.sortedBy { it.date }.takeLast(7)
        if (last7.size < MIN_OBSERVED_DAYS) return null

        // Simple linear regression
        val n = last7.size
        val xValues = (0 until n).map { it.toDouble() }
        val yValues = last7.map { it.views.toDouble() }

        val xMean = xValues.average()
        val yMean = yValues.average()

        val numerator = xValues.zip(yValues).sumOf { (x, y) -> (x - xMean) * (y - yMean) }
        val denominator = xValues.sumOf { (it - xMean) * (it - xMean) }

        if (denominator == 0.0) {
            return (yMean * 7).toLong().coerceAtLeast(0)
        }

        val slope = numerator / denominator
        val intercept = yMean - slope * xMean

        // Predict next 7 days
        var totalPredicted = 0.0
        for (day in n until n + 7) {
            val predicted = (slope * day + intercept).coerceAtLeast(0.0)
            totalPredicted += predicted
        }

        return totalPredicted.toLong().coerceAtLeast(0)
    }

    /**
     * 앞뒤 절반의 평균 조회수를 견준 추세. **기간이 짧으면 `null`.**
     *
     * 예전에는 4일 미만이면 `"stable"` 을 돌려줬다 — 관측한 적 없는 추세를 "안정적"이라고
     * 말한 것이다. 게시 직후 하루치만 있는 영상이 전부 "안정"으로 표시됐고, 화면은 그것을
     * 추세 라벨로 그렸다.
     *
     * ## 조회수가 계속 0 이면 "stable" 이다
     *
     * 앞뒤 절반이 모두 0 이면 **조회수가 오르지도 내리지도 않았다는 관측**이다. 하위
     * 점수들과 갈리는 지점이 여기다.
     *
     * - 하위 점수는 **비율**이라 조회수가 분모다. 분모가 0 이면 값이 정의되지 않아 `null`.
     * - 추세는 **방향**만 말한다. 나누지 않으므로 0 과 0 을 견주는 데 아무 문제가 없다.
     *
     * 같은 이유로 [com.ongo.domain.analytics.MetricChange] 의 증감률은 기준선이 0 이면
     * `null` 이다 — 그쪽은 퍼센트라서 0 으로 나눠야 하지만, 여기는 그렇지 않다.
     */
    private fun calculateTrend(videoAnalytics: List<AnalyticsDaily>): String? {
        val sorted = videoAnalytics.sortedBy { it.date }
        if (sorted.size < MIN_OBSERVED_DAYS) return null

        val midpoint = sorted.size / 2
        val firstHalfAvg = sorted.take(midpoint).map { it.views }.average()
        val secondHalfAvg = sorted.drop(midpoint).map { it.views }.average()

        return when {
            secondHalfAvg > firstHalfAvg * 1.1 -> "up"
            secondHalfAvg < firstHalfAvg * 0.9 -> "down"
            else -> "stable"
        }
    }

    private fun getAnomalyDescription(type: AnomalyType?): String? = when (type) {
        AnomalyType.VIRAL_SPIKE -> "이 영상의 조회수가 채널 평균 대비 비정상적으로 높습니다. 바이럴 현상이 감지되었습니다."
        AnomalyType.ENGAGEMENT_SURGE -> "이 영상의 참여율(좋아요, 댓글, 공유)이 평균 대비 매우 높습니다."
        AnomalyType.UNUSUAL_DROP -> "이 영상의 성과가 채널 평균 대비 비정상적으로 낮습니다."
        AnomalyType.SHARE_SPIKE -> "이 영상의 공유 수가 비정상적으로 높습니다. 외부 확산이 감지되었습니다."
        null -> null
    }

    /**
     * 측정할 데이터가 없을 때의 응답. **숫자는 하나도 없다.**
     *
     * 예전에는 전부 `0` 과 `"stable"` 을 채우고 `dataAvailable = false` 로만 구분했다.
     * 그 플래그를 놓친 소비자는 "0점 · 7일 예상 0회 · 안정적 추세"를 그렸다. 자리채움을
     * 아예 두지 않으면 놓칠 값 자체가 없다.
     */
    private fun emptyScoreResponse(videoId: Long, reason: String) = PerformanceScoreResponse(
        videoId = videoId,
        overallScore = null,
        breakdown = mapOf(
            VIEW_VELOCITY to null,
            ENGAGEMENT to null,
            WATCH_TIME to null,
            CONVERSION to null,
            SHARE to null,
        ),
        // 측정값이 없으면 순위도 없다. 0 을 넣으면 "상위 0%"(최상위)로 읽힌다.
        percentileRank = null,
        trend = null,
        isAnomaly = false,
        anomalyDescription = null,
        prediction7d = null,
        unavailableMetrics = WEIGHTS.keys.associateWith { reason } +
            mapOf("overall" to reason, "trend" to reason, "prediction7d" to reason, "percentileRank" to reason),
        dataAvailable = false,
        unavailableReason = reason,
    )
}
