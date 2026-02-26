package com.ongo.application.creatornetwork

import com.ongo.application.creatornetwork.dto.*
import com.ongo.domain.creatornetwork.CollaborationRequestRepository
import com.ongo.domain.creatornetwork.CreatorProfileRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class CreatorNetworkUseCase(
    private val profileRepo: CreatorProfileRepository,
    private val requestRepo: CollaborationRequestRepository,
) {
    fun getCreators(userId: Long, category: String?): List<CreatorProfileResponse> {
        val profiles = if (category != null) profileRepo.findByCategory(category)
        else profileRepo.findByUserId(userId)
        return profiles.map { CreatorProfileResponse.from(it) }
    }

    fun getCreator(id: Long): CreatorProfileResponse? =
        profileRepo.findById(id)?.let { CreatorProfileResponse.from(it) }

    fun getRequests(creatorId: Long): List<CollaborationRequestResponse> =
        requestRepo.findByCreatorId(creatorId).map { CollaborationRequestResponse.from(it) }

    @Transactional
    fun respondRequest(id: Long, accept: Boolean) {
        val status = if (accept) "ACCEPTED" else "DECLINED"
        requestRepo.updateStatus(id, status)
    }

    fun getSummary(userId: Long): CreatorNetworkSummaryResponse {
        val profiles = profileRepo.findByUserId(userId)
        val connected = profiles.filter { it.isConnected }
        return CreatorNetworkSummaryResponse(
            totalConnections = connected.size,
            pendingRequests = 0,
            collaborations = 0,
            avgMatchScore = if (profiles.isNotEmpty()) profiles.sumOf { it.matchScore } / profiles.size.toDouble() else 0.0,
            topCategory = profiles.groupBy { it.category }.maxByOrNull { it.value.size }?.key,
        )
    }
}
