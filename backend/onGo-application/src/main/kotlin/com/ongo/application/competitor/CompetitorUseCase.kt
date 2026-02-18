package com.ongo.application.competitor

import com.ongo.application.competitor.dto.*
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.competitor.ChannelLookupPort
import com.ongo.domain.competitor.Competitor
import com.ongo.domain.competitor.CompetitorRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CompetitorUseCase(
    private val competitorRepository: CompetitorRepository,
    private val channelLookupPort: ChannelLookupPort,
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
