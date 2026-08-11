package com.ongo.application.competitor

import com.ongo.application.competitor.dto.*
import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.common.enums.PlanType
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.competitor.ChannelLookupPort
import com.ongo.domain.competitor.Competitor
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
                        avgViews = a.avgViews,
                        totalViews = a.totalViews,
                    )
                },
            )
        }
    }

    fun getBenchmark(userId: Long): BenchmarkResponse {
        // 내 채널 통계 집계
        val channels = channelRepository.findByUserId(userId)
        val mySubscribers = channels.sumOf { it.subscriberCount }
        val allAnalytics = analyticsRepository.findAllByUserId(userId)
        val myTotalViews = allAnalytics.sumOf { it.views.toLong() }
        val myVideoCount = allAnalytics.map { it.videoUploadId }.distinct().size
        val myAvgViews = if (myVideoCount > 0) myTotalViews / myVideoCount else 0L
        val totalEngagements = allAnalytics.sumOf { (it.likes + it.commentsCount + it.shares).toLong() }
        val myEngagementRate = if (myTotalViews > 0) (totalEngagements.toDouble() / myTotalViews * 100) else 0.0

        // 성장률: 최근 30일 구독자 변화
        val recentAnalytics = allAnalytics.filter {
            it.date.isAfter(LocalDate.now().minusDays(30))
        }
        val myGrowthSubscribers = recentAnalytics.sumOf { it.subscriberGained }
        val myGrowthRate = if (mySubscribers > 0) (myGrowthSubscribers.toDouble() / mySubscribers * 100) else 0.0

        // 경쟁자 벤치마크
        val competitors = competitorRepository.findByUserId(userId)
        val endDate = LocalDate.now()
        val startDate = endDate.minusDays(30)

        val competitorBenchmarks = competitors.map { comp ->
            val analytics = competitorRepository.findAnalyticsByCompetitorIdAndDateRange(
                comp.id!!, startDate, endDate
            )
            val firstDay = analytics.firstOrNull()
            val lastDay = analytics.lastOrNull()
            val subGrowth = if (firstDay != null && lastDay != null)
                lastDay.subscriberCount - firstDay.subscriberCount else 0L
            val growthRate = if (firstDay != null && firstDay.subscriberCount > 0)
                (subGrowth.toDouble() / firstDay.subscriberCount * 100) else 0.0

            CompetitorBenchmark(
                id = comp.id!!,
                channelName = comp.channelName,
                platform = comp.platform,
                subscriberCount = comp.subscriberCount,
                totalViews = comp.totalViews,
                videoCount = comp.videoCount,
                avgViews = comp.avgViews,
                engagementRate = 0.0, // 경쟁자 참여율은 공개 API로 정확히 알 수 없음
                growthRate = Math.round(growthRate * 10) / 10.0,
                profileImageUrl = comp.profileImageUrl,
            )
        }

        return BenchmarkResponse(
            myStats = MyChannelStats(
                subscriberCount = mySubscribers,
                totalViews = myTotalViews,
                videoCount = myVideoCount,
                avgViews = myAvgViews,
                engagementRate = Math.round(myEngagementRate * 10) / 10.0,
                growthRate = Math.round(myGrowthRate * 10) / 10.0,
            ),
            competitors = competitorBenchmarks,
        )
    }

    private fun Competitor.toResponse() = CompetitorResponse(
        id = id!!,
        platform = platform,
        platformChannelId = platformChannelId,
        channelName = channelName,
        channelUrl = channelUrl,
        subscriberCount = subscriberCount,
        totalViews = totalViews,
        videoCount = videoCount,
        avgViews = avgViews,
        profileImageUrl = profileImageUrl,
        lastSyncedAt = lastSyncedAt,
        createdAt = createdAt,
    )
}
