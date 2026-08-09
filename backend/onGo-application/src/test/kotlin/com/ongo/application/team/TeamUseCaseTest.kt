package com.ongo.application.team

import com.ongo.domain.team.TeamMember
import com.ongo.domain.team.TeamMemberRepository
import com.ongo.domain.workspace.Workspace
import com.ongo.domain.workspace.WorkspaceRepository
import com.ongo.domain.user.UserRepository
import com.ongo.application.team.dto.InviteMemberRequest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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

    @Test
    fun `초대는 소유자의 워크스페이스에 연결되고 이메일과 역할을 정규화한다`() {
        every { repository.findByUserIdAndEmail(1L, "new@example.com") } returns null
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
}
