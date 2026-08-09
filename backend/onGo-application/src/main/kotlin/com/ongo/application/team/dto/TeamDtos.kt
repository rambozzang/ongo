package com.ongo.application.team.dto

import java.time.LocalDateTime

data class TeamMemberResponse(
    val id: Long,
    val memberEmail: String,
    val memberName: String?,
    val role: String,
    val status: String,
    val invitedAt: LocalDateTime?,
    val joinedAt: LocalDateTime?,
    val createdAt: LocalDateTime?,
    val expiresAt: LocalDateTime?,
)

data class TeamInvitationResponse(
    val id: Long,
    val workspaceId: Long?,
    val workspaceName: String?,
    val ownerId: Long,
    val role: String,
    val status: String,
    val invitedAt: LocalDateTime?,
    val expiresAt: LocalDateTime?,
)

data class InviteMemberRequest(
    val email: String,
    val role: String = "VIEWER",
)

data class UpdateRoleRequest(
    val role: String,
)
