package com.ongo.application.team

import com.fasterxml.jackson.databind.ObjectMapper
import com.ongo.common.enums.Permission
import com.ongo.domain.team.TeamMember
import com.ongo.domain.team.TeamMemberRepository
import io.mockk.every
import io.mockk.mockk
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class PermissionServiceTest {
    private val repository = mockk<TeamMemberRepository>()
    private val service = PermissionService(repository, ObjectMapper())

    @Test
    fun `joined membership is checked by team_members row id rather than authenticated user id`() {
        every { repository.findById(41L) } returns TeamMember(
            id = 41L,
            userId = 7L,
            memberEmail = "editor@example.com",
            role = "EDITOR",
            status = "JOINED",
        )

        assertTrue(service.hasPermissionForMember(41L, 7L, Permission.VIDEO_PUBLISH))
        assertFalse(service.hasPermissionForMember(41L, 7L, Permission.TEAM_REMOVE))
    }

    @Test
    fun `pending or another teams membership cannot authorize a request`() {
        every { repository.findById(41L) } returns TeamMember(
            id = 41L,
            userId = 7L,
            memberEmail = "editor@example.com",
            role = "ADMIN",
            status = "INVITED",
        )

        assertFalse(service.hasPermissionForMember(41L, 7L, Permission.VIDEO_READ))
        assertFalse(service.hasPermissionForMember(41L, 99L, Permission.VIDEO_READ))
    }
}
