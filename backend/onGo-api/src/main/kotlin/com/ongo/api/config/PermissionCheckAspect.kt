package com.ongo.api.config

import com.ongo.application.team.PermissionService
import com.ongo.common.annotation.RequiresPermission
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.UnauthorizedException
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

@Aspect
@Component
class PermissionCheckAspect(
    private val permissionService: PermissionService,
) {

    @Around("@annotation(com.ongo.common.annotation.RequiresPermission)")
    fun checkPermission(joinPoint: ProceedingJoinPoint): Any? {
        val signature = joinPoint.signature as MethodSignature
        val method = signature.method
        val annotation = method.getAnnotation(RequiresPermission::class.java)
            ?: return joinPoint.proceed()

        val permission = annotation.value

        // Extract userId from SecurityContext
        val authentication = SecurityContextHolder.getContext().authentication
            ?: throw UnauthorizedException()
        val userId = authentication.principal as? Long
            ?: throw UnauthorizedException()

        // Extract teamId from method parameters (look for @PathVariable("teamId") or param named "teamId")
        val paramNames = signature.parameterNames
        val args = joinPoint.args

        var teamId: Long? = null
        for (i in paramNames.indices) {
            if (paramNames[i] == "teamId" && args[i] is Long) {
                teamId = args[i] as Long
                break
            }
        }

        // If no teamId found, assume user is operating on their own resources (owner)
        val effectiveTeamId = teamId ?: userId

        if (!permissionService.hasPermissionForUser(userId, effectiveTeamId, permission)) {
            throw ForbiddenException("'${permission.name}' 권한이 없습니다")
        }

        return joinPoint.proceed()
    }
}
