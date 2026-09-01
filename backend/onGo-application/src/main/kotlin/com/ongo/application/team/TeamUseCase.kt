package com.ongo.application.team

import com.ongo.application.team.dto.*
import com.ongo.common.enums.Permission
import com.ongo.common.exception.DuplicateResourceException
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.common.exception.PlanLimitExceededException
import com.ongo.domain.team.RolePermissions
import com.ongo.domain.team.TeamMember
import com.ongo.domain.team.TeamMemberRepository
import com.ongo.domain.user.UserRepository
import com.ongo.domain.workspace.Workspace
import com.ongo.domain.workspace.WorkspaceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class TeamUseCase(
    private val teamMemberRepository: TeamMemberRepository,
    private val permissionService: PermissionService,
    private val workspaceRepository: WorkspaceRepository,
    private val userRepository: UserRepository,
) {

    fun listMembers(userId: Long): List<TeamMemberResponse> =
        teamMemberRepository.findByUserId(userId).map { it.toResponse() }

    fun listIncomingInvitations(userId: Long): List<TeamInvitationResponse> {
        val email = userRepository.findById(userId)?.email
            ?: throw NotFoundException("사용자", userId)
        return teamMemberRepository.findInvitationsForMember(email)
            .map { it.toInvitationResponse() }
    }

    @Transactional
    fun acceptInvitation(userId: Long, invitationId: Long): TeamMemberResponse {
        val user = userRepository.findById(userId) ?: throw NotFoundException("사용자", userId)
        val member = invitationFor(user.email, invitationId)
        ensureInvitationActive(member)
        return teamMemberRepository.update(
            member.copy(status = "JOINED", joinedAt = LocalDateTime.now()),
        ).toResponse()
    }

    @Transactional
    fun declineInvitation(userId: Long, invitationId: Long) {
        val user = userRepository.findById(userId) ?: throw NotFoundException("사용자", userId)
        val member = invitationFor(user.email, invitationId)
        ensureInvitationActive(member)
        teamMemberRepository.delete(invitationId)
    }

    @Transactional
    fun inviteMember(userId: Long, request: InviteMemberRequest): TeamMemberResponse {
        val email = request.email.trim().lowercase()
        val role = request.role.trim().uppercase()
        if (!EMAIL_PATTERN.matches(email)) {
            throw com.ongo.common.exception.BusinessException("INVALID_TEAM_EMAIL", "유효한 초대 이메일을 입력해주세요")
        }
        if (role !in INVITABLE_ROLES) {
            throw com.ongo.common.exception.BusinessException("INVALID_TEAM_ROLE", "초대할 수 없는 팀 역할입니다: $role")
        }

        /*
         * **소유자 행을 잠근 뒤에 판정한다.**
         *
         * 아래 두 검사는 모두 "읽어 보고 없으면 넣는" 모양이라, 잠그지 않으면
         * READ COMMITTED 에서 동시에 들어온 요청이 **커밋 전 같은 상태**를 읽고 둘 다
         * 통과한다. 가상의 경합이 아니다 — 초대 화면은 쉼표로 나눈 이메일을
         * `Promise.all` 로 **동시에** 보낸다(`InviteMemberModal.vue`).
         *
         * 잠그지 않았을 때 실제로 새던 것:
         *  - 좌석 한도: Pro(2석)에 이메일 3개를 한 번에 넣으면 3석이 만들어진다.
         *  - 중복 초대: 같은 이메일 두 요청이 둘 다 "없음" 을 보고 두 행을 만든다.
         *    `team_members` 에는 이를 막을 UNIQUE 제약이 없다(`V5__collab_tables.sql`).
         *
         * 잠글 대상은 **소유자당 정확히 하나 있는 행**이어야 한다. `users` 행이 그것이고,
         * 어차피 플랜을 읽으려고 이미 조회하던 행이라 질의가 늘지 않는다. 같은 소유자의
         * 초대만 직렬화되므로 다른 사용자에게는 영향이 없다.
         *
         * 인스턴스 수와 무관하다. 이 경합은 같은 JVM 의 두 요청 스레드 사이에서 일어나므로
         * 단일 인스턴스에서도 성립한다. 그래서 인메모리 락이 아니라 **행 잠금**이어야 한다.
         *
         * 이 트랜잭션에서 잠그는 행은 이것 하나뿐이라 잠금 순서로 인한 교착은 생기지 않는다.
         */
        val user = userRepository.findByIdForUpdate(userId) ?: throw NotFoundException("사용자", userId)

        /*
         * 판정 순서는 잠금 도입 전과 같다 — **중복이 한도보다 먼저**다.
         *
         * 이미 초대한 이메일을 다시 초대하는 것은 좌석을 늘리지 않으므로 "한도 초과" 가
         * 아니라 "중복" 이라고 말해야 사용자가 무엇을 해야 할지 안다. 순서를 뒤집으면
         * 좌석이 꽉 찬 팀에서 재초대가 "업그레이드하세요" 로 안내된다.
         */
        val existing = teamMemberRepository.findByUserIdAndEmail(userId, email)
        if (existing != null) {
            throw DuplicateResourceException("팀 멤버", email)
        }

        /*
         * **좌석 한도는 서버가 강제한다.**
         *
         * `PlanType.maxTeamMembers` 는 요금제 비교 API(`SubscriptionUseCase.toPlanLimits`)로
         * 사용자에게 그대로 광고된다 — Free·Starter 0명, Pro 2명, Business 10명. 그런데 이
         * 값을 **읽어서 막는 곳이 한 군데도 없었다.** 초대 경로에는 이메일 형식·역할·중복
         * 검사만 있고, `AppCapability("team")` 은 조건 없이 켜져 있으며 컨트롤러에도 게이트가
         * 없다. 그래서 Free 사용자가 팀원을 **무제한으로** 초대할 수 있었다.
         *
         * 협업은 Pro 이상에서만 파는 기능이다. 광고한 한도를 강제하지 않으면 그 값은 가격표의
         * 장식일 뿐이고, 상위 플랜을 살 이유가 사라진다.
         *
         * ## 무엇을 한 좌석으로 세는가
         *
         * `findByUserId` 가 돌려주는 **모든 행**이다. 수락 전(INVITED)이라도 좌석을 차지한다.
         * 수락한 것만 세면 대기 중인 초대를 무제한으로 쌓아 두고 한꺼번에 수락시켜 한도를
         * 그대로 넘길 수 있다. 만료된 초대도 행이 남아 있는 동안은 좌석을 차지하며,
         * 소유자가 [removeMember] 로 지우면 즉시 비워진다.
         */
        val seatLimit = user.planType.maxTeamMembers
        if (teamMemberRepository.findByUserId(userId).size >= seatLimit) {
            throw PlanLimitExceededException("팀 멤버", seatLimit)
        }

        val member = TeamMember(
            userId = userId,
            memberEmail = email,
            role = role,
            status = "INVITED",
            workspaceId = ensureOwnerWorkspace(userId).id,
        )
        return teamMemberRepository.save(member).toResponse()
    }

    private fun ensureOwnerWorkspace(userId: Long): Workspace {
        workspaceRepository.findByOwnerId(userId).firstOrNull()?.let { return it }
        val user = userRepository.findById(userId) ?: throw NotFoundException("사용자", userId)
        return workspaceRepository.save(
            Workspace(
                ownerId = userId,
                name = user.nickname ?: user.name,
                slug = "ws-$userId",
            )
        )
    }

    private fun invitationFor(email: String, invitationId: Long): TeamMember {
        val invitation = teamMemberRepository.findById(invitationId)
            ?: throw NotFoundException("팀 초대", invitationId)
        if (invitation.memberEmail.trim().lowercase() != email.trim().lowercase()) {
            throw ForbiddenException("해당 초대에 대한 권한이 없습니다")
        }
        if (invitation.status != "INVITED") {
            throw com.ongo.common.exception.BusinessException("INVITATION_NOT_PENDING", "대기 중인 초대가 아닙니다")
        }
        return invitation
    }

    private fun ensureInvitationActive(invitation: TeamMember) {
        val expiresAt = invitation.invitedAt?.plusDays(INVITE_VALID_DAYS)
        if (expiresAt != null && expiresAt.isBefore(LocalDateTime.now())) {
            throw com.ongo.common.exception.BusinessException("INVITATION_EXPIRED", "만료된 초대입니다. 팀 소유자에게 재발송을 요청해주세요")
        }
    }

    @Transactional
    fun resendInvite(userId: Long, memberId: Long): TeamMemberResponse {
        val member = teamMemberRepository.findById(memberId)
            ?: throw NotFoundException("팀 멤버", memberId)
        if (member.userId != userId) throw ForbiddenException("해당 팀 멤버에 대한 권한이 없습니다")
        if (member.status == "JOINED") throw ForbiddenException("이미 가입한 멤버의 초대는 재발송할 수 없습니다")

        return teamMemberRepository.resendInvite(memberId, LocalDateTime.now()).toResponse()
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
        Permission.CAMPAIGN_VIEW -> "UGC 캠페인 조회"
        Permission.CAMPAIGN_MANAGE -> "UGC 캠페인 생성 및 관리"
        Permission.CAMPAIGN_REVIEW -> "UGC 제출물 검수"
        Permission.CAMPAIGN_REWARD_MANAGE -> "UGC 보상 확정 및 지급 관리"
        Permission.SHORTS_PIPELINE_VIEW -> "쇼츠 파이프라인 프롬프트·템플릿 조회"
        Permission.SHORTS_PIPELINE_MANAGE -> "쇼츠 파이프라인 프롬프트·템플릿 편집"
    }

    private fun TeamMember.toResponse(): TeamMemberResponse {
        val expiresAt = invitedAt?.plusDays(INVITE_VALID_DAYS)
        val responseStatus = when {
            status == "INVITED" && expiresAt != null && expiresAt.isBefore(LocalDateTime.now()) -> "EXPIRED"
            else -> status
        }

        return TeamMemberResponse(
            id = id!!,
            memberEmail = memberEmail,
            memberName = memberName,
            role = role,
            status = responseStatus,
            invitedAt = invitedAt,
            joinedAt = joinedAt,
            createdAt = createdAt,
            expiresAt = expiresAt,
        )
    }

    private fun TeamMember.toInvitationResponse(): TeamInvitationResponse {
        val expiresAt = invitedAt?.plusDays(INVITE_VALID_DAYS)
        val status = if (expiresAt != null && expiresAt.isBefore(LocalDateTime.now())) "EXPIRED" else status
        val workspace = workspaceId?.let { workspaceRepository.findById(it) }
        return TeamInvitationResponse(
            id = id!!,
            workspaceId = workspaceId,
            workspaceName = workspace?.name,
            ownerId = userId,
            role = role,
            status = status,
            invitedAt = invitedAt,
            expiresAt = expiresAt,
        )
    }

    private companion object {
        const val INVITE_VALID_DAYS = 7L
        val INVITABLE_ROLES = setOf("ADMIN", "EDITOR", "VIEWER")
        val EMAIL_PATTERN = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}
