package com.ongo.application.creatornetwork.dto

import com.ongo.domain.creatornetwork.CreatorProfile
import com.ongo.domain.creatornetwork.CollaborationRequest

data class CreatorProfileResponse(
    val id: Long,
    val name: String,
    val avatarUrl: String?,
    val platform: String,
    val subscribers: Long,
    val category: String?,
    val matchScore: Int,
    val isConnected: Boolean,
    val bio: String?,
    val joinedAt: String,
) {
    companion object {
        fun from(e: CreatorProfile) = CreatorProfileResponse(
            id = e.id, name = e.name, avatarUrl = e.avatarUrl,
            platform = e.platform, subscribers = e.subscribers,
            category = e.category, matchScore = e.matchScore,
            isConnected = e.isConnected, bio = e.bio,
            joinedAt = e.joinedAt.toString(),
        )
    }
}

data class CollaborationRequestResponse(
    val id: Long,
    val fromCreatorId: Long,
    val fromCreatorName: String?,
    val toCreatorId: Long,
    val toCreatorName: String?,
    val message: String?,
    val status: String,
    val proposedType: String?,
    val createdAt: String,
) {
    companion object {
        fun from(e: CollaborationRequest, fromName: String? = null, toName: String? = null) = CollaborationRequestResponse(
            id = e.id, fromCreatorId = e.fromCreatorId, fromCreatorName = fromName,
            toCreatorId = e.toCreatorId, toCreatorName = toName,
            message = e.message, status = e.status,
            proposedType = e.proposedType, createdAt = e.createdAt.toString(),
        )
    }
}

data class CreatorNetworkSummaryResponse(
    val totalConnections: Int,
    val pendingRequests: Int,
    val collaborations: Int,
    val avgMatchScore: Double,
    val topCategory: String?,
)

data class SendRequestDto(
    val toCreatorId: Long,
    val message: String,
    val proposedType: String,
)
