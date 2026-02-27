package com.ongo.application.creatornetwork

import com.ongo.application.creatornetwork.dto.*
import com.ongo.domain.creatornetwork.CollaborationRequest
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

    @Transactional
    fun connectCreator(userId: Long, creatorId: Long) {
        val request = CollaborationRequest(
            fromCreatorId = userId,
            toCreatorId = creatorId,
            message = "연결 요청",
            status = "PENDING",
            proposedType = "CONNECT",
        )
        requestRepo.save(request)
    }

    @Transactional
    fun disconnectCreator(userId: Long, creatorId: Long) {
        val requests = requestRepo.findByCreatorId(userId)
        val target = requests.find {
            (it.fromCreatorId == userId && it.toCreatorId == creatorId) ||
            (it.fromCreatorId == creatorId && it.toCreatorId == userId)
        }
        if (target != null) {
            requestRepo.updateStatus(target.id, "DISCONNECTED")
        }
    }

    @Transactional
    fun createCollabRequest(userId: Long, request: CreateCollabRequest): CollaborationRequestResponse {
        val collabRequest = CollaborationRequest(
            fromCreatorId = userId,
            toCreatorId = request.toCreatorId,
            message = request.message,
            status = "PENDING",
            proposedType = request.proposedType,
        )
        val saved = requestRepo.save(collabRequest)
        return CollaborationRequestResponse.from(saved)
    }

    @Transactional
    fun respondCollabRequest(userId: Long, id: Long, accept: Boolean): CollaborationRequestResponse {
        val status = if (accept) "ACCEPTED" else "DECLINED"
        requestRepo.updateStatus(id, status)
        val updated = requestRepo.findById(id)
        return CollaborationRequestResponse.from(updated!!)
    }

    fun getSummary(userId: Long): CreatorNetworkSummaryResponse {
        val profiles = profileRepo.findByUserId(userId)
        val connected = profiles.filter { it.isConnected }
        val pendingRequests = requestRepo.findByCreatorId(userId).count { it.status == "PENDING" }
        return CreatorNetworkSummaryResponse(
            totalConnections = connected.size,
            pendingRequests = pendingRequests,
            collaborations = 0,
            avgMatchScore = if (profiles.isNotEmpty()) profiles.sumOf { it.matchScore } / profiles.size.toDouble() else 0.0,
            topCategory = profiles.groupBy { it.category }.maxByOrNull { it.value.size }?.key,
        )
    }
}
