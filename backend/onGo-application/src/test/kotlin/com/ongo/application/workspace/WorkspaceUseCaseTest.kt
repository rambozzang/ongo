package com.ongo.application.workspace

import com.ongo.common.exception.NotFoundException
import com.ongo.domain.team.TeamMemberRepository
import com.ongo.domain.user.UserRepository
import com.ongo.domain.workspace.Workspace
import com.ongo.domain.workspace.WorkspaceRepository
import io.mockk.every
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test

class WorkspaceUseCaseTest {
    private val workspaces = mockk<WorkspaceRepository>()
    private val members = mockk<TeamMemberRepository>()
    private val users = mockk<UserRepository>()
    private val useCase = WorkspaceUseCase(workspaces, members, users)

    private val workspace = Workspace(
        id = 12L,
        ownerId = 7L,
        name = "Creator workspace",
        slug = "creator",
    )

    @Test
    fun `workspace detail is denied when the user is not an owner or joined member`() {
        every { workspaces.findById(12L) } returns workspace
        every { workspaces.findAccessibleByUserId(99L) } returns emptyList()

        assertFailsWith<NotFoundException> {
            useCase.getWorkspace(99L, 12L)
        }
    }

    @Test
    fun `workspace detail reports owner plus active joined members`() {
        every { workspaces.findById(12L) } returns workspace
        every { workspaces.findAccessibleByUserId(7L) } returns listOf(workspace)
        every { members.countByWorkspaceId(12L) } returns 3

        assertEquals(4, useCase.getWorkspace(7L, 12L).memberCount)
    }
}
