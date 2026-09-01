package com.ongo.application.team

import com.ongo.domain.team.TeamMember
import com.ongo.domain.team.TeamMemberRepository
import com.ongo.domain.workspace.Workspace
import com.ongo.domain.workspace.WorkspaceRepository
import com.ongo.domain.user.UserRepository
import com.ongo.domain.user.User
import com.ongo.common.enums.AuthProvider
import com.ongo.common.enums.PlanType
import com.ongo.common.exception.PlanLimitExceededException
import com.ongo.application.team.dto.InviteMemberRequest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TeamUseCaseTest {
    private val repository = mockk<TeamMemberRepository>()
    private val permissionService = mockk<PermissionService>(relaxed = true)
    private val workspaces = mockk<WorkspaceRepository>()
    private val users = mockk<UserRepository>()
    private val useCase = TeamUseCase(repository, permissionService, workspaces, users)

    @Test
    fun `초대 응답은 만료 시각과 만료 상태를 서버에서 계산한다`() {
        val invitedAt = LocalDateTime.now().minusDays(8)
        every { repository.findByUserId(1L) } returns listOf(
            TeamMember(id = 7L, userId = 1L, memberEmail = "expired@example.com", invitedAt = invitedAt),
        )

        val result = useCase.listMembers(1L).single()

        assertEquals("EXPIRED", result.status)
        assertEquals(invitedAt.plusDays(7), result.expiresAt)
    }

    @Test
    fun `소유자만 초대를 재발송하고 저장소에 새 발송 시각을 기록한다`() {
        val member = TeamMember(id = 7L, userId = 1L, memberEmail = "pending@example.com", status = "INVITED")
        every { repository.findById(7L) } returns member
        every { repository.resendInvite(7L, any()) } answers { member.copy(invitedAt = secondArg()) }

        val result = useCase.resendInvite(1L, 7L)

        assertEquals("INVITED", result.status)
        verify { repository.resendInvite(7L, any()) }
    }

    @Test
    fun `다른 소유자의 초대 재발송은 거부한다`() {
        every { repository.findById(7L) } returns TeamMember(id = 7L, userId = 99L, memberEmail = "other@example.com")

        assertFailsWith<com.ongo.common.exception.ForbiddenException> {
            useCase.resendInvite(1L, 7L)
        }
        verify(exactly = 0) { repository.resendInvite(any(), any()) }
    }

    /** 좌석 한도가 있는 플랜의 소유자. 한도 검사가 읽는 값이다. */
    private fun owner(plan: PlanType) = User(
        id = 1L,
        email = "owner@example.com",
        name = "Owner",
        provider = AuthProvider.GOOGLE,
        providerId = "google-1",
        planType = plan,
    )

    @Test
    fun `초대는 소유자의 워크스페이스에 연결되고 이메일과 역할을 정규화한다`() {
        every { repository.findByUserIdAndEmail(1L, "new@example.com") } returns null
        // 좌석 한도 검사가 읽는 값. 협업은 Pro 이상에서만 파는 기능이다.
        every { users.findByIdForUpdate(1L) } returns owner(PlanType.PRO)
        every { repository.findByUserId(1L) } returns emptyList()
        every { workspaces.findByOwnerId(1L) } returns listOf(
            Workspace(id = 22L, ownerId = 1L, name = "내 작업공간", slug = "mine"),
        )
        every { repository.save(any()) } answers { firstArg<TeamMember>().copy(id = 8L) }

        val result = useCase.inviteMember(
            1L,
            InviteMemberRequest(email = " New@Example.com ", role = "editor"),
        )

        assertEquals("new@example.com", result.memberEmail)
        assertEquals("EDITOR", result.role)
        verify { repository.save(match { it.workspaceId == 22L && it.memberEmail == "new@example.com" }) }
    }

    @Test
    fun `초대받은 사용자는 자신의 이메일로만 초대를 수락하고 joined 멤버가 된다`() {
        val user = User(id = 9L, email = "invitee@example.com", name = "Invitee", provider = AuthProvider.GOOGLE, providerId = "google-9")
        val invitation = TeamMember(
            id = 12L,
            userId = 1L,
            memberEmail = "invitee@example.com",
            role = "EDITOR",
            status = "INVITED",
            invitedAt = LocalDateTime.now().minusHours(1),
            workspaceId = 22L,
        )
        every { users.findById(9L) } returns user
        every { repository.findById(12L) } returns invitation
        every { repository.update(any()) } answers { firstArg<TeamMember>() }

        val result = useCase.acceptInvitation(9L, 12L)

        assertEquals("JOINED", result.status)
        verify { repository.update(match { it.id == 12L && it.status == "JOINED" && it.joinedAt != null }) }
    }

    @Test
    fun `다른 이메일로는 초대를 수락할 수 없다`() {
        val user = User(id = 9L, email = "other@example.com", name = "Other", provider = AuthProvider.GOOGLE, providerId = "google-9")
        every { users.findById(9L) } returns user
        every { repository.findById(12L) } returns TeamMember(id = 12L, userId = 1L, memberEmail = "invitee@example.com")

        assertFailsWith<com.ongo.common.exception.ForbiddenException> {
            useCase.acceptInvitation(9L, 12L)
        }
        verify(exactly = 0) { repository.update(any()) }
    }

    /* ── 팀 좌석 한도 ────────────────────────────────────────────────── */

    /*
     * `PlanType.maxTeamMembers` 는 요금제 비교 API 로 사용자에게 광고된다
     * (Free·Starter 0명 / Pro 2명 / Business 10명). 그런데 그 값을 읽어 막는 곳이 없어
     * **Free 사용자가 팀원을 무제한 초대**할 수 있었다. 협업은 Pro 이상에서만 파는
     * 기능이므로, 강제하지 않으면 상위 플랜을 살 이유가 사라진다.
     */

    private fun givenInvitable(plan: PlanType, seatsUsed: Int) {
        every { repository.findByUserIdAndEmail(1L, any()) } returns null
        every { users.findByIdForUpdate(1L) } returns owner(plan)
        every { repository.findByUserId(1L) } returns (1..seatsUsed).map {
            TeamMember(id = it.toLong(), userId = 1L, memberEmail = "member$it@example.com", status = "JOINED")
        }
        every { workspaces.findByOwnerId(1L) } returns listOf(
            Workspace(id = 22L, ownerId = 1L, name = "내 작업공간", slug = "mine"),
        )
        every { repository.save(any()) } answers { firstArg<TeamMember>().copy(id = 99L) }
    }

    private fun invite(email: String = "new@example.com") =
        useCase.inviteMember(1L, InviteMemberRequest(email = email, role = "EDITOR"))

    @Test
    fun `팀 좌석이 없는 플랜은 초대를 저장하지 않는다`() {
        for (plan in listOf(PlanType.FREE, PlanType.STARTER)) {
            givenInvitable(plan, seatsUsed = 0)

            val error = assertFailsWith<PlanLimitExceededException> { invite() }

            assertEquals("PLAN_LIMIT_EXCEEDED", error.code)
            assertEquals(0, error.limit)
        }
        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `유료 플랜은 한도까지 초대하고 그 다음을 막는다`() {
        givenInvitable(PlanType.PRO, seatsUsed = 1)
        invite("second@example.com")

        givenInvitable(PlanType.PRO, seatsUsed = 2)
        val error = assertFailsWith<PlanLimitExceededException> { invite("third@example.com") }

        assertEquals(2, error.limit)
        verify(exactly = 1) { repository.save(any()) }
    }

    @Test
    fun `Business 는 더 많은 좌석을 갖는다`() {
        givenInvitable(PlanType.BUSINESS, seatsUsed = 9)
        invite("tenth@example.com")

        givenInvitable(PlanType.BUSINESS, seatsUsed = 10)
        assertFailsWith<PlanLimitExceededException> { invite("eleventh@example.com") }

        verify(exactly = 1) { repository.save(any()) }
    }

    /**
     * **대기 중인 초대도 좌석을 차지한다.**
     *
     * 수락한 멤버만 세면 대기 초대를 무제한으로 쌓아 두고 한꺼번에 수락시켜 한도를 그대로
     * 넘길 수 있다. 수락 경로에는 한도 검사가 없으므로 이 자리가 유일한 방어선이다.
     */
    @Test
    fun `수락 전 초대도 좌석으로 센다`() {
        every { repository.findByUserIdAndEmail(1L, any()) } returns null
        every { users.findByIdForUpdate(1L) } returns owner(PlanType.PRO)
        every { repository.findByUserId(1L) } returns listOf(
            TeamMember(id = 1L, userId = 1L, memberEmail = "pending1@example.com", status = "INVITED"),
            TeamMember(id = 2L, userId = 1L, memberEmail = "pending2@example.com", status = "INVITED"),
        )
        every { workspaces.findByOwnerId(1L) } returns listOf(
            Workspace(id = 22L, ownerId = 1L, name = "내 작업공간", slug = "mine"),
        )

        assertFailsWith<PlanLimitExceededException> { invite("third@example.com") }

        verify(exactly = 0) { repository.save(any()) }
    }

    /* ── 동시 초대 직렬화 ────────────────────────────────────────────── */

    /**
     * **판정은 소유자 행을 잠그고 시작한다.**
     *
     * 중복 검사와 좌석 검사는 둘 다 "읽어 보고 없으면 넣는" 모양이라, 잠그지 않으면
     * READ COMMITTED 에서 동시 요청이 커밋 전 같은 상태를 읽고 **둘 다 통과**한다.
     * 초대 화면이 쉼표로 나눈 이메일을 `Promise.all` 로 동시에 보내므로 정상 흐름에서
     * 일어난다. 잠그지 않는 조회로 되돌아가면 이 테스트가 깨진다.
     */
    @Test
    fun `초대 판정은 소유자 행을 잠그고 시작한다`() {
        givenInvitable(PlanType.PRO, seatsUsed = 0)

        invite()

        verify(exactly = 1) { users.findByIdForUpdate(1L) }
        // 잠그지 않는 조회로 플랜을 읽으면 직렬화되지 않는다.
        verify(exactly = 0) { users.findById(1L) }
    }

    /**
     * **잠금이 두 판정보다 먼저**여야 한다.
     *
     * 잠금을 중복 검사 뒤로 미루면 같은 이메일 두 요청이 둘 다 "없음" 을 보고 두 행을
     * 만든다. `team_members` 에는 이를 막을 UNIQUE 제약이 없다.
     */
    @Test
    fun `잠금은 중복·좌석 조회보다 먼저 일어난다`() {
        givenInvitable(PlanType.PRO, seatsUsed = 0)

        invite()

        verifyOrder {
            users.findByIdForUpdate(1L)
            repository.findByUserIdAndEmail(1L, any())
            repository.findByUserId(1L)
            repository.save(any())
        }
    }

    /**
     * 이미 초대한 이메일을 다시 초대해도 좌석은 늘지 않는다. 한도가 아니라 **중복**으로
     * 알려야 사용자가 무엇을 해야 할지 안다.
     */
    @Test
    fun `중복 초대는 한도보다 먼저 중복으로 거절한다`() {
        every { repository.findByUserIdAndEmail(1L, "dup@example.com") } returns
            TeamMember(id = 5L, userId = 1L, memberEmail = "dup@example.com")
        every { users.findByIdForUpdate(1L) } returns owner(PlanType.FREE)

        assertFailsWith<com.ongo.common.exception.DuplicateResourceException> { invite("dup@example.com") }
    }
}
