package com.ongo.application.team.dto

import com.ongo.application.team.PermissionStatus

data class MemberPermissionsResponse(
    val memberId: Long,
    val memberName: String?,
    val memberEmail: String,
    val role: String,
    val permissions: Map<String, PermissionStatus>,
)

data class TeamPermissionsResponse(
    val members: List<MemberPermissionsResponse>,
)

data class UpdatePermissionsRequest(
    val permissions: Map<String, Boolean>,
)

data class PermissionInfo(
    val name: String,
    val category: String,
    val description: String,
)

data class PermissionCatalogResponse(
    val permissions: List<PermissionInfo>,
    val roleDefaults: Map<String, List<String>>,
)
