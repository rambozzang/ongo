package com.ongo.domain.team

import com.ongo.common.enums.Permission
import com.ongo.common.enums.Permission.*

object RolePermissions {

    val OWNER: Set<Permission> = Permission.entries.toSet()

    val ADMIN: Set<Permission> = Permission.entries.toSet() - setOf(
        BILLING_MANAGE,
        TEAM_REMOVE,
    )

    val EDITOR: Set<Permission> = setOf(
        VIDEO_CREATE, VIDEO_READ, VIDEO_UPDATE, VIDEO_PUBLISH,
        SCHEDULE_CREATE, SCHEDULE_READ, SCHEDULE_UPDATE,
        ANALYTICS_READ,
        AI_USE,
        APPROVAL_CREATE,
        SETTINGS_READ,
        AUTOMATION_CREATE, AUTOMATION_UPDATE,
    )

    val VIEWER: Set<Permission> = setOf(
        VIDEO_READ,
        SCHEDULE_READ,
        ANALYTICS_READ,
        SETTINGS_READ,
    )

    fun defaultPermissions(role: String): Set<Permission> = when (role.uppercase()) {
        "OWNER" -> OWNER
        "ADMIN" -> ADMIN
        "EDITOR" -> EDITOR
        "VIEWER" -> VIEWER
        else -> VIEWER
    }
}
