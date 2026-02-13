package com.ongo.application.team

import com.ongo.common.enums.Permission
import com.ongo.domain.team.RolePermissions
import com.ongo.domain.team.TeamMemberRepository
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service

@Service
class PermissionService(
    private val teamMemberRepository: TeamMemberRepository,
    private val objectMapper: ObjectMapper,
) {

    @Cacheable(value = ["permissions"], key = "#userId + '-' + #teamId + '-' + #permission.name()")
    fun hasPermission(userId: Long, teamId: Long, permission: Permission): Boolean {
        // Team owner (teamId == userId) always has all permissions
        if (userId == teamId) return true

        val member = teamMemberRepository.findById(userId)
        // If checking a team member by their member record
        if (member != null && member.userId == teamId) {
            return checkMemberPermission(member.role, member.permissions, permission)
        }

        // Find if the user is a member of the team owned by teamId
        val members = teamMemberRepository.findByUserId(teamId)
        val teamMember = members.find { it.memberEmail.isNotBlank() }
        // Fallback: the userId IS the team owner
        return userId == teamId
    }

    /**
     * Check if a user has a specific permission, considering both role defaults and overrides.
     */
    fun hasPermissionForUser(userId: Long, teamOwnerId: Long, permission: Permission): Boolean {
        // Owner always has all permissions
        if (userId == teamOwnerId) return true

        // Find the member record for this user in the team
        val members = teamMemberRepository.findByUserId(teamOwnerId)
        // Match by looking for member whose id matches userId, or by email lookup
        val member = members.find { it.id == userId }
            ?: return false

        return checkMemberPermission(member.role, member.permissions, permission)
    }

    fun getEffectivePermissions(memberId: Long): Set<Permission> {
        val member = teamMemberRepository.findById(memberId) ?: return emptySet()
        val rolePerms = RolePermissions.defaultPermissions(member.role)
        val overrides = parseOverrides(member.permissions)

        val result = rolePerms.toMutableSet()
        for ((perm, granted) in overrides) {
            if (granted) result.add(perm) else result.remove(perm)
        }
        return result
    }

    fun getEffectivePermissionsMap(memberId: Long): Map<String, PermissionStatus> {
        val member = teamMemberRepository.findById(memberId) ?: return emptyMap()
        val rolePerms = RolePermissions.defaultPermissions(member.role)
        val overrides = parseOverrides(member.permissions)

        return Permission.entries.associate { perm ->
            val overrideValue = overrides[perm]
            val status = when {
                overrideValue == true -> PermissionStatus.GRANTED
                overrideValue == false -> PermissionStatus.DENIED
                perm in rolePerms -> PermissionStatus.INHERITED
                else -> PermissionStatus.DENIED
            }
            perm.name to status
        }
    }

    @CacheEvict(value = ["permissions"], allEntries = true)
    fun updatePermissions(memberId: Long, overrides: Map<String, Boolean>): Map<String, PermissionStatus> {
        val member = teamMemberRepository.findById(memberId)
            ?: throw com.ongo.common.exception.NotFoundException("팀 멤버", memberId)

        val permOverrides = mutableMapOf<String, Boolean>()
        val rolePerms = RolePermissions.defaultPermissions(member.role)

        for ((permName, granted) in overrides) {
            val perm = try {
                Permission.valueOf(permName)
            } catch (_: IllegalArgumentException) {
                continue
            }
            // Only store override if it differs from role default
            val isDefault = (granted && perm in rolePerms) || (!granted && perm !in rolePerms)
            if (!isDefault) {
                permOverrides[permName] = granted
            }
        }

        val permJson = objectMapper.writeValueAsString(permOverrides)
        val updated = member.copy(permissions = permJson)
        teamMemberRepository.update(updated)

        return getEffectivePermissionsMap(memberId)
    }

    private fun checkMemberPermission(role: String, permissionsJson: String, permission: Permission): Boolean {
        val rolePerms = RolePermissions.defaultPermissions(role)
        val overrides = parseOverrides(permissionsJson)

        // Check override first
        val overrideValue = overrides[permission]
        if (overrideValue != null) return overrideValue

        // Fall back to role default
        return permission in rolePerms
    }

    private fun parseOverrides(permissionsJson: String): Map<Permission, Boolean> {
        if (permissionsJson.isBlank() || permissionsJson == "{}") return emptyMap()

        return try {
            val raw: Map<String, Boolean> = objectMapper.readValue(
                permissionsJson,
                object : TypeReference<Map<String, Boolean>>() {}
            )
            raw.mapNotNull { (key, value) ->
                try {
                    Permission.valueOf(key) to value
                } catch (_: IllegalArgumentException) {
                    null
                }
            }.toMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }
}

enum class PermissionStatus {
    GRANTED,
    DENIED,
    INHERITED,
}
