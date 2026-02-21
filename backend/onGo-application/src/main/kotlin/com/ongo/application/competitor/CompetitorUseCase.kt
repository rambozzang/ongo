package com.ongo.application.competitor

import com.ongo.application.competitor.dto.*
import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.competitor.ChannelLookupPort
import com.ongo.domain.competitor.Competitor
import com.ongo.domain.competitor.CompetitorRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class CompetitorUseCase(
    private val competitorRepository: CompetitorRepository,
    private val channelLookupPort: ChannelLookupPort,
    private val analyticsRepository: AnalyticsRepository,
    private val channelRepository: ChannelRepository,
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

    @Transactional
    fun addCompetitor(userId: Long, request: CreateCompetitorRequest): CompetitorResponse {
        val count = competitorRepository.countByUserId(userId)
        if (count >= 5) throw BusinessException("COMPETITOR_LIMIT", "경쟁자는 최대 5개까지 추가할 수 있습니다")

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
