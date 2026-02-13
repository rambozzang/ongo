package com.ongo.application.team

import com.ongo.application.team.dto.*
import com.ongo.common.enums.Permission
import com.ongo.common.exception.DuplicateResourceException
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.team.RolePermissions
import com.ongo.domain.team.TeamMember
import com.ongo.domain.team.TeamMemberRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TeamUseCase(
    private val teamMemberRepository: TeamMemberRepository,
    private val permissionService: PermissionService,
) {

    fun listMembers(userId: Long): List<TeamMemberResponse> =
        teamMemberRepository.findByUserId(userId).map { it.toResponse() }

    @Transactional
    fun inviteMember(userId: Long, request: InviteMemberRequest): TeamMemberResponse {
        val existing = teamMemberRepository.findByUserIdAndEmail(userId, request.email)
        if (existing != null) {
            throw DuplicateResourceException("팀 멤버", request.email)
        }

        val member = TeamMember(
            userId = userId,
            memberEmail = request.email,
            role = request.role,
            status = "INVITED",
        )
        return teamMemberRepository.save(member).toResponse()
    }

    @Transactional
    fun updateRole(userId: Long, memberId: Long, request: UpdateRoleRequest): TeamMemberResponse {
        val member = teamMemberRepository.findById(memberId)
            ?: throw NotFoundException("팀 멤버", memberId)
        if (member.userId != userId) throw ForbiddenException("해당 팀 멤버에 대한 권한이 없습니다")

        val updated = member.copy(role = request.role)
        return teamMemberRepository.update(updated).toResponse()
    }

    @Transactional
    fun removeMember(userId: Long, memberId: Long) {
        val member = teamMemberRepository.findById(memberId)
            ?: throw NotFoundException("팀 멤버", memberId)
        if (member.userId != userId) throw ForbiddenException("해당 팀 멤버에 대한 권한이 없습니다")
        teamMemberRepository.delete(memberId)
    }

    fun getTeamPermissions(userId: Long): TeamPermissionsResponse {
        val members = teamMemberRepository.findByUserId(userId)
        val memberPermissions = members.map { member ->
            val memberId = member.id!!
            MemberPermissionsResponse(
                memberId = memberId,
                memberName = member.memberName,
                memberEmail = member.memberEmail,
                role = member.role,
                permissions = permissionService.getEffectivePermissionsMap(memberId),
            )
        }
        return TeamPermissionsResponse(members = memberPermissions)
    }

    fun getMemberPermissions(userId: Long, memberId: Long): MemberPermissionsResponse {
        val member = teamMemberRepository.findById(memberId)
            ?: throw NotFoundException("팀 멤버", memberId)
        if (member.userId != userId) throw ForbiddenException("해당 팀 멤버에 대한 권한이 없습니다")

        val memberIdVal = member.id!!
        return MemberPermissionsResponse(
            memberId = memberIdVal,
            memberName = member.memberName,
            memberEmail = member.memberEmail,
            role = member.role,
            permissions = permissionService.getEffectivePermissionsMap(memberIdVal),
        )
    }

    @Transactional
    fun updateMemberPermissions(
        userId: Long,
        memberId: Long,
        request: UpdatePermissionsRequest,
    ): MemberPermissionsResponse {
        val member = teamMemberRepository.findById(memberId)
            ?: throw NotFoundException("팀 멤버", memberId)
        if (member.userId != userId) throw ForbiddenException("해당 팀 멤버에 대한 권한이 없습니다")

        val updatedPermissions = permissionService.updatePermissions(memberId, request.permissions)

        val memberIdVal = member.id!!
        return MemberPermissionsResponse(
            memberId = memberIdVal,
            memberName = member.memberName,
            memberEmail = member.memberEmail,
            role = member.role,
            permissions = updatedPermissions,
        )
    }

    fun getPermissionCatalog(): PermissionCatalogResponse {
        val permissionInfos = Permission.entries.map { perm ->
            val category = perm.name.substringBefore("_")
            PermissionInfo(
                name = perm.name,
                category = category,
                description = getPermissionDescription(perm),
            )
        }

        val roleDefaults = mapOf(
            "OWNER" to RolePermissions.OWNER.map { it.name },
            "ADMIN" to RolePermissions.ADMIN.map { it.name },
            "EDITOR" to RolePermissions.EDITOR.map { it.name },
            "VIEWER" to RolePermissions.VIEWER.map { it.name },
        )

        return PermissionCatalogResponse(
            permissions = permissionInfos,
            roleDefaults = roleDefaults,
        )
    }

    private fun getPermissionDescription(permission: Permission): String = when (permission) {
        Permission.VIDEO_CREATE -> "영상 업로드"
        Permission.VIDEO_READ -> "영상 목록 및 상세 조회"
        Permission.VIDEO_UPDATE -> "영상 메타데이터 수정"
        Permission.VIDEO_DELETE -> "영상 삭제"
        Permission.VIDEO_PUBLISH -> "영상 플랫폼 게시"
        Permission.SCHEDULE_CREATE -> "예약 게시 등록"
        Permission.SCHEDULE_READ -> "예약 목록 조회"
        Permission.SCHEDULE_UPDATE -> "예약 수정"
        Permission.SCHEDULE_DELETE -> "예약 취소"
        Permission.ANALYTICS_READ -> "분석 데이터 조회"
        Permission.ANALYTICS_EXPORT -> "분석 데이터 내보내기"
        Permission.AI_USE -> "AI 기능 사용"
        Permission.AI_BATCH -> "AI 배치 처리"
        Permission.TEAM_MANAGE -> "팀 설정 관리"
        Permission.TEAM_INVITE -> "팀 멤버 초대"
        Permission.TEAM_REMOVE -> "팀 멤버 제거"
        Permission.APPROVAL_CREATE -> "승인 요청 생성"
        Permission.APPROVAL_APPROVE -> "승인 요청 승인"
        Permission.APPROVAL_REJECT -> "승인 요청 거절"
        Permission.SETTINGS_READ -> "설정 조회"
        Permission.SETTINGS_UPDATE -> "설정 변경"
        Permission.BILLING_READ -> "결제 정보 조회"
        Permission.BILLING_MANAGE -> "결제 정보 관리"
        Permission.AUTOMATION_CREATE -> "자동화 규칙 생성"
        Permission.AUTOMATION_UPDATE -> "자동화 규칙 수정"
        Permission.AUTOMATION_DELETE -> "자동화 규칙 삭제"
    }

    private fun TeamMember.toResponse(): TeamMemberResponse = TeamMemberResponse(
        id = id!!,
        memberEmail = memberEmail,
        memberName = memberName,
        role = role,
        status = status,
        invitedAt = invitedAt,
        joinedAt = joinedAt,
        createdAt = createdAt,
    )
}
