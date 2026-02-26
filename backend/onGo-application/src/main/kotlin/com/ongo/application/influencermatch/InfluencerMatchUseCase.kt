package com.ongo.application.influencermatch

import com.ongo.application.influencermatch.dto.*
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.influencermatch.CollabRequest
import com.ongo.domain.influencermatch.CollabRequestRepository
import com.ongo.domain.influencermatch.InfluencerProfile
import com.ongo.domain.influencermatch.InfluencerProfileRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class InfluencerMatchUseCase(
    private val influencerProfileRepository: InfluencerProfileRepository,
    private val collabRequestRepository: CollabRequestRepository,
) {

    fun findMatches(userId: Long, request: FindMatchRequest): List<InfluencerMatchResponse> {
        val profiles = influencerProfileRepository.findByUserId(userId)
        return profiles.map { it.toResponse() }
    }

    fun listCollabs(userId: Long): List<CollabRequestResponse> {
        return collabRequestRepository.findByUserId(userId).map { it.toResponse() }
    }

    @Transactional
    fun sendCollabRequest(userId: Long, request: CreateCollabRequest): CollabRequestResponse {
        val collab = CollabRequest(
            userId = userId,
            influencerProfileId = request.influencerProfileId,
            message = request.message,
            proposedBudget = request.proposedBudget,
            proposedType = request.proposedType,
            status = "PENDING",
        )
        return collabRequestRepository.save(collab).toResponse()
    }

    @Transactional
    fun updateCollabStatus(userId: Long, collabId: Long, request: UpdateCollabStatusRequest): CollabRequestResponse {
        val collab = collabRequestRepository.findById(collabId)
            ?: throw NotFoundException("협업 요청", collabId)
        if (collab.userId != userId) throw ForbiddenException("해당 협업 요청에 대한 권한이 없습니다")
        val updated = collab.copy(
            status = request.status,
            responseMessage = request.responseMessage,
            respondedAt = LocalDateTime.now(),
        )
        return collabRequestRepository.update(updated).toResponse()
    }

    private fun InfluencerProfile.toResponse() = InfluencerMatchResponse(
        id = id!!,
        channelName = channelName,
        platform = platform,
        subscriberCount = subscriberCount,
        avgViews = avgViews,
        engagementRate = engagementRate,
        categories = categories,
        audienceDemographics = audienceDemographics,
        contactEmail = contactEmail,
        profileUrl = profileUrl,
        matchScore = matchScore,
        createdAt = createdAt,
    )

    private fun CollabRequest.toResponse() = CollabRequestResponse(
        id = id!!,
        influencerProfileId = influencerProfileId,
        message = message,
        proposedBudget = proposedBudget,
        proposedType = proposedType,
        status = status,
        responseMessage = responseMessage,
        respondedAt = respondedAt,
        createdAt = createdAt,
    )
}
