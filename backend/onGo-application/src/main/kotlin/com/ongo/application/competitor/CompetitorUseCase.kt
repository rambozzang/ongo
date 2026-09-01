package com.ongo.application.competitor

import com.ongo.application.analytics.AnalyticsRowPlatforms
import com.ongo.application.analytics.ChannelSubscriberTotal
import com.ongo.application.analytics.PlatformMetricAvailability
import com.ongo.domain.video.VideoUploadRepository
import com.ongo.application.competitor.dto.*
import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.common.enums.PlanType
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.competitor.ChannelLookupPort
import com.ongo.domain.competitor.Competitor
import com.ongo.domain.competitor.measuredAvgViews
import com.ongo.domain.competitor.CompetitorRepository
import com.ongo.domain.subscription.SubscriptionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class CompetitorUseCase(
    private val competitorRepository: CompetitorRepository,
    private val channelLookupPort: ChannelLookupPort,
    private val competitorRefreshService: CompetitorRefreshService,
    private val analyticsRepository: AnalyticsRepository,
    /** 집계 행의 플랫폼을 알아야 지표별 수집 여부를 판정할 수 있다. */
    private val videoUploadRepository: VideoUploadRepository,
    private val channelRepository: ChannelRepository,
    private val subscriptionRepository: SubscriptionRepository,
) {

    private val log = LoggerFactory.getLogger(CompetitorUseCase::class.java)

    fun lookupChannel(userId: Long, request: ChannelLookupRequest): ChannelLookupResponse {
        log.info("채널 조회: userId={}, platform={}, query={}", userId, request.platform, request.query)
        val result = channelLookupPort.lookupChannel(
            platform = request.platform,
            query = request.query,
        )
        return ChannelLookupResponse(
            found = result.found,
            platformChannelId = result.platformChannelId,
            channelName = result.channelName,
            channelUrl = result.channelUrl,
            subscriberCount = result.subscriberCount,
            totalViews = result.totalViews,
            videoCount = result.videoCount,
            profileImageUrl = result.profileImageUrl,
            platform = result.platform,
            requiresManualInput = result.requiresManualInput,
            message = result.message,
        )
    }

    fun listCompetitors(userId: Long): CompetitorListResponse {
        val competitors = competitorRepository.findByUserId(userId)
        return CompetitorListResponse(
            competitors = competitors.map { it.toResponse() },
            totalCount = competitors.size,
        )
    }

    /**
     * 사용자의 경쟁 채널을 실제로 제공자에서 다시 읽어 갱신한다.
     *
     * 스케줄러와 같은 CompetitorRefreshService 를 쓴다. 예전에는 이 자리에서 저장된
     * 목록을 그대로 돌려주며 성공 메시지만 붙였다.
     *
     * @Transactional 을 붙이지 않는다. 갱신은 외부 HTTP 조회를 건별로 수행하므로
     * 트랜잭션 안에 두면 커넥션을 외부 I/O 시간만큼 붙잡는다. 건별 쓰기는
     * 리포지토리 수준에서 각각 커밋되며, 한 건이 실패해도 나머지는 살아 있는 편이 낫다.
     *
     * 계정 동결 가드는 HTTP 필터에서 이미 확인했으므로 여기서 다시 보지 않는다.
     */
    fun syncCompetitors(userId: Long): CompetitorSyncResponse {
        val competitors = competitorRepository.findByUserId(userId)
        if (competitors.isEmpty()) {
            return CompetitorSyncResponse(
                requested = 0,
                synced = 0,
                unsupported = 0,
                failed = 0,
                results = emptyList(),
                competitors = emptyList(),
                totalCount = 0,
            )
        }

        val summary = competitorRefreshService.refreshAll(competitors)
        log.info(
            "경쟁자 수동 동기화: userId={}, 요청={}, 성공={}, 미지원={}, 실패={}",
            userId, summary.requested, summary.synced, summary.unsupported, summary.failed,
        )

        // 한 건도 갱신하지 못했고 실패가 있었다면 오류다. 부분 성공은 오류로 보지 않는다 —
        // 갱신된 건이 있으면 사용자에게 보여줄 새 데이터가 실제로 생겼기 때문이다.
        if (summary.synced == 0 && summary.failed > 0) {
            throw BusinessException(
                "COMPETITOR_SYNC_FAILED",
                "경쟁 채널 동기화에 실패했습니다. 잠시 후 다시 시도해주세요.",
            )
        }

        val refreshed = competitorRepository.findByUserId(userId)
        return CompetitorSyncResponse(
            requested = summary.requested,
            synced = summary.synced,
            unsupported = summary.unsupported,
            failed = summary.failed,
            results = summary.outcomes.map {
                CompetitorSyncItemResponse(
                    competitorId = it.competitorId,
                    channelName = it.channelName,
                    platform = it.platform,
                    status = it.status.name,
                    message = it.message,
                )
            },
            competitors = refreshed.map { it.toResponse() },
            totalCount = refreshed.size,
        )
    }

    @Transactional
    fun addCompetitor(userId: Long, request: CreateCompetitorRequest): CompetitorResponse {
        val planType = subscriptionRepository.findByUserId(userId)?.planType ?: PlanType.FREE
        val limit = planType.competitorLimit
        val count = competitorRepository.countByUserId(userId)
        if (count >= limit) throw BusinessException(
            "COMPETITOR_LIMIT",
            "현재 요금제(${planType.displayName})에서는 경쟁 채널을 최대 ${limit}개까지 추가할 수 있습니다",
        )

        val competitor = Competitor(
            userId = userId,
            platform = request.platform,
            platformChannelId = request.platformChannelId,
            channelName = request.channelName,
            channelUrl = request.channelUrl,
            subscriberCount = request.subscriberCount,
            totalViews = request.totalViews,
            videoCount = request.videoCount,
            avgViews = request.avgViews,
            profileImageUrl = request.profileImageUrl,
        )
        val saved = competitorRepository.save(competitor)
        return saved.toResponse()
    }

    @Transactional
    fun updateCompetitor(userId: Long, id: Long, request: UpdateCompetitorRequest): CompetitorResponse {
        val existing = competitorRepository.findById(id) ?: throw NotFoundException("경쟁자", id)
        if (existing.userId != userId) throw ForbiddenException()

        val updated = existing.copy(
            channelName = request.channelName ?: existing.channelName,
            channelUrl = request.channelUrl ?: existing.channelUrl,
            subscriberCount = request.subscriberCount ?: existing.subscriberCount,
            totalViews = request.totalViews ?: existing.totalViews,
            videoCount = request.videoCount ?: existing.videoCount,
            avgViews = request.avgViews ?: existing.avgViews,
            profileImageUrl = request.profileImageUrl ?: existing.profileImageUrl,
        )
        val saved = competitorRepository.update(updated)
        return saved.toResponse()
    }

    @Transactional
    fun removeCompetitor(userId: Long, id: Long) {
        val existing = competitorRepository.findById(id) ?: throw NotFoundException("경쟁자", id)
        if (existing.userId != userId) throw ForbiddenException()
        competitorRepository.delete(id)
    }

    fun getCompetitorTrends(userId: Long, request: CompetitorTrendRequest): List<CompetitorTrendResponse> {
        val endDate = LocalDate.now()
        val startDate = endDate.minusDays(request.days.toLong())

        val competitors = if (request.competitorIds.isEmpty()) {
            competitorRepository.findByUserId(userId)
        } else {
            request.competitorIds.mapNotNull { id ->
                competitorRepository.findById(id)?.also {
                    if (it.userId != userId) throw ForbiddenException()
                }
            }
        }

        return competitors.map { competitor ->
            val analytics = competitorRepository.findAnalyticsByCompetitorIdAndDateRange(
                competitor.id!!, startDate, endDate
            )
            CompetitorTrendResponse(
                competitorId = competitor.id!!,
                channelName = competitor.channelName,
                data = analytics.map { a ->
                    CompetitorTrendPoint(
                        date = a.date.toString(),
                        subscriberCount = a.subscriberCount,
                        // 그날 영상 수가 0 이면 평균의 분모가 없다. 저장된 0 은 계산하지
                        // 못한 자리이지 "평균 0회" 라는 관측이 아니다.
                        avgViews = measuredAvgViews(a.videoCount, a.totalViews, a.avgViews),
                        totalViews = a.totalViews,
                    )
                },
            )
        }
    }

    fun getBenchmark(userId: Long): BenchmarkResponse {
        // 내 채널 통계 집계
        val channels = channelRepository.findByUserId(userId)
        /*
         * 구독자 수를 **조회하는 채널만** 더한다.
         *
         * `ThreadsClient.kt:205` 와 `LinkedInClient.kt:264` 는 팔로워 수를 묻지도 않고
         * `subscriberCount = 0` 을 박아 넣는다. 그대로 더하면 그 두 플랫폼만 연동한
         * 크리에이터가 **비교표에서 "구독자 0명"** 으로 경쟁사와 나란히 놓인다.
         *
         * 조회하는 채널이 있고 그 합이 0 이면 그 `0` 은 실측이다 —
         * [totalViews]·[avgViews] 와 같은 계약이다.
         */
        val mySubscribers = ChannelSubscriberTotal.measuredTotal(channels)
        val allAnalytics = analyticsRepository.findAllByUserId(userId)

        /*
         * **내 채널 수치는 경쟁사와 나란히 놓이는 비교 기준이다.** 오염되면 비교 결과가
         * 통째로 틀린다.
         *
         * `findAllByUserId` 는 `AnalyticsDaily` 를 주는데 그 행에는 `videoUploadId` 만 있어
         * 플랫폼을 알 수 없다. 그래서 예전에는 필터 없이 더했고, `TumblrClient.kt:141` 의
         * `total_notes`(노트 총합)가 조회수로, `PinterestClient.kt:158/160` 의 `SAVE`(저장)·
         * `PIN_CLICK`(클릭)이 참여 수로 들어갔다.
         */
        val rowPlatforms = AnalyticsRowPlatforms.of(videoUploadRepository.findByUserId(userId))

        val viewRows = rowPlatforms.rowsReporting(allAnalytics, PlatformMetricAvailability.VIEWS)
        /*
         * **합계도 `videoCount`·`avgViews` 와 같은 계약이어야 한다.**
         *
         * 측정 행이 하나도 없을 때의 `0` 은 "조회가 0회였다" 는 관측으로 읽힌다. 반면
         * 행이 있고 합이 0 이면 그것은 실측이므로 그대로 `0` 이다.
         */
        val myTotalViews = if (viewRows.isEmpty()) null else viewRows.sumOf { it.views.toLong() }
        val myVideoCount = viewRows.map { it.videoUploadId }.distinct().size
        val myAvgViews = if (myTotalViews != null && myVideoCount > 0) myTotalViews / myVideoCount else null

        /*
         * 참여율은 분자와 분모가 **같은 행**에서 나와야 한다. 좋아요·댓글·공유를 모두
         * 수집하는 행만 쓴다 — 분자에서만 빼고 조회수를 분모에 남기면 참여율이 낮아진다.
         */
        val engagementRows = rowPlatforms.rowsReporting(
            allAnalytics,
            PlatformMetricAvailability.LIKES,
            PlatformMetricAvailability.COMMENTS,
            PlatformMetricAvailability.SHARES,
            PlatformMetricAvailability.VIEWS,
        )
        val engagementViews = engagementRows.sumOf { it.views.toLong() }
        val myEngagementRate = if (engagementViews > 0) {
            val engagements = engagementRows.sumOf { (it.likes + it.commentsCount + it.shares).toLong() }
            engagements.toDouble() / engagementViews * 100
        } else {
            // 분모가 없으면 비율이 성립하지 않는다. 0.0 은 "참여가 없었다" 는 관측이 된다.
            null
        }

        /*
         * 성장률: 최근 30일 구독 증가.
         *
         * `subscriber_gained` 를 조회하는 어댑터는 `YouTubeClient` 하나뿐이다. 수집하는
         * 행이 없으면 "0명 늘었다" 가 아니라 물어볼 곳이 없다는 뜻이다.
         */
        val recentAnalytics = allAnalytics.filter {
            it.date.isAfter(LocalDate.now().minusDays(30))
        }
        val subscriberRows =
            rowPlatforms.rowsReporting(recentAnalytics, PlatformMetricAvailability.SUBSCRIBER_GAINED)
        /*
         * 분모가 **측정된 구독자 수**여야 한다. 재지 않은 채널의 0 을 분모로 쓰면 비율
         * 자체가 성립하지 않는다 — `mySubscribers` 가 `null` 이면 나눌 기준이 없다.
         */
        val myGrowthRate = if (subscriberRows.isNotEmpty() && mySubscribers != null && mySubscribers > 0) {
            subscriberRows.sumOf { it.subscriberGained }.toDouble() / mySubscribers * 100
        } else {
            null
        }

        // 경쟁자 벤치마크
        val competitors = competitorRepository.findByUserId(userId)
        val endDate = LocalDate.now()
        val startDate = endDate.minusDays(30)

        val competitorBenchmarks = competitors.map { comp ->
            val analytics = competitorRepository.findAnalyticsByCompetitorIdAndDateRange(
                comp.id!!, startDate, endDate
            )
            /*
             * **성장률은 관측된 두 시점이 있어야 말할 수 있다.**
             *
             * 예전에는 기간 내 수집 이력이 없거나 기준일 구독자가 0 이어도 `0.0` 을
             * 만들었다. 화면은 그것을 "성장률 0%" 로 그렸고, 한 번도 수집한 적 없는
             * 경쟁사가 "정체 중" 으로 보였다.
             *
             * 관측이 하나뿐이면 시작과 끝이 같은 행이라 변화를 잰 적이 없다. 기준일
             * 구독자가 0 이면 비율의 분모가 없다. 둘 다 `null` 이다 — 두 시점이 실제로
             * 관측됐고 값이 같을 때만 `0.0` 이 관측 결과가 된다.
             */
            val firstDay = analytics.firstOrNull()
            val lastDay = analytics.lastOrNull()
            /*
             * **두 끝점이 모두 실제로 관측돼야 한다.**
             *
             * 구독자 수를 숨긴 채널은 스냅샷의 `subscriberCount` 가 `null` 이다. 그것을
             * 0 으로 읽으면 어제 10,000 → 오늘 0 이 되어 **-100% 폭락**을 지어낸다.
             */
            val firstSubs = firstDay?.subscriberCount
            val lastSubs = lastDay?.subscriberCount
            val growthRate = if (
                analytics.size >= 2 && firstSubs != null && lastSubs != null && firstSubs > 0
            ) {
                val subGrowth = lastSubs - firstSubs
                subGrowth.toDouble() / firstSubs * 100
            } else {
                null
            }

            CompetitorBenchmark(
                id = comp.id!!,
                channelName = comp.channelName,
                platform = comp.platform,
                subscriberCount = comp.subscriberCount,
                totalViews = comp.totalViews,
                videoCount = comp.videoCount,
                avgViews = measuredAvgViews(comp.videoCount, comp.totalViews, comp.avgViews),
                // 공개 API 로 남의 채널의 좋아요·댓글·공유를 얻을 수 없다. 분자가 없으니
                // 참여율을 만들 수 없다. 0 을 넣으면 "참여율 0%" 라는 측정 결과가 되어
                // 내가 모든 경쟁사를 압도하는 것처럼 보인다 — 그래서 비워 둔다.
                engagementRate = null,
                engagementRateUnavailableReason = COMPETITOR_ENGAGEMENT_UNAVAILABLE,
                // 반올림하려다 null 을 0 으로 만들면 미측정이 "성장률 0%" 가 된다.
                growthRate = growthRate?.let { Math.round(it * 10) / 10.0 },
                profileImageUrl = comp.profileImageUrl,
            )
        }

        return BenchmarkResponse(
            myStats = MyChannelStats(
                subscriberCount = mySubscribers,
                totalViews = myTotalViews,
                videoCount = myVideoCount,
                avgViews = myAvgViews,
                // 반올림하려다 null 을 0 으로 만들면 미측정이 "0" 이라는 관측이 된다.
                engagementRate = myEngagementRate?.let { Math.round(it * 10) / 10.0 },
                growthRate = myGrowthRate?.let { Math.round(it * 10) / 10.0 },
            ),
            competitors = competitorBenchmarks,
        )
    }

    /**
     * 영상당 평균 조회수. **분자(총 조회수)나 분모(영상 수)를 모르거나 영상 수가 0 이면 `null`.**
     *
     * 저장 모델은 `Long` non-null 이라 그 자리에 `0` 이 들어 있다. `CompetitorRefreshService`
     * 가 `if (videoCount > 0) totalViews / videoCount else 0` 으로 채우기 때문인데, 그 `0` 은
     * **분모가 없어 계산하지 못한 자리**이지 관측이 아니다. 스키마를 바꾸지 않고 응답
     * 경계에서 `videoCount` 를 근거로 갈라낸다.
     *
     * 영상이 있고 총 조회수가 실제로 0 이면 `0 / n = 0` 은 관측이므로 그대로 낸다.
     */


    private fun Competitor.toResponse() = CompetitorResponse(
        id = id!!,
        platform = platform,
        platformChannelId = platformChannelId,
        channelName = channelName,
        channelUrl = channelUrl,
        subscriberCount = subscriberCount,
        totalViews = totalViews,
        videoCount = videoCount,
        avgViews = measuredAvgViews(videoCount, totalViews, avgViews),
        profileImageUrl = profileImageUrl,
        lastSyncedAt = lastSyncedAt,
        createdAt = createdAt,
    )

    companion object {
        /**
         * 경쟁자 참여율을 낼 수 없는 이유. 화면이 그대로 보여준다.
         *
         * 값이 아니라 **문장**인 이유: 숫자를 넣으면 어떤 숫자든 측정 결과로 읽힌다.
         */
        const val COMPETITOR_ENGAGEMENT_UNAVAILABLE =
            "공개 API로 경쟁 채널의 좋아요·댓글 수를 얻을 수 없어 참여율을 계산할 수 없습니다"
    }
}
