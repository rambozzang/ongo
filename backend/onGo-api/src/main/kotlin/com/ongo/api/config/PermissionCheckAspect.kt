package com.ongo.api.config

import com.ongo.common.annotation.RequiresPermission
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.UnauthorizedException
import com.ongo.application.team.PermissionService
import com.ongo.domain.team.TeamMemberRepository
import com.ongo.domain.user.UserRepository
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

@Aspect
@Component
class PermissionCheckAspect(
    private val permissionService: PermissionService,
    private val userRepository: UserRepository,
    private val teamMemberRepository: TeamMemberRepository,
) {

    @Around("@annotation(requiresPermission)")
    fun checkPermission(joinPoint: ProceedingJoinPoint, requiresPermission: RequiresPermission): Any? {
        val authentication = SecurityContextHolder.getContext().authentication
            ?: throw UnauthorizedException("인증이 필요합니다")
        val userId = authentication.principal as? Long
            ?: throw UnauthorizedException("인증이 필요합니다")

        val permission = requiresPermission.value

        // Find user to get email for team membership lookup
        val user = userRepository.findById(userId)
            ?: throw UnauthorizedException("사용자를 찾을 수 없습니다")

        // Check if user is a team member (not an owner)
        val teamMemberships = teamMemberRepository.findTeamsForMember(user.email)
        if (teamMemberships.isNotEmpty()) {
            val activeMembership = teamMemberships.first()
            if (!permissionService.hasPermissionForUser(userId, activeMembership.userId, permission)) {
                throw ForbiddenException("${permission.name} 권한이 필요합니다")
            }
        }
        // Solo users (account owners) have all permissions — proceed

        return joinPoint.proceed()
    }
}
