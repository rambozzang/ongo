package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.teamperformance.*
import org.springframework.stereotype.Repository

@Repository
class TeamActivityStubRepository : TeamActivityRepository {
    override fun findByWorkspaceId(workspaceId: Long, limit: Int): List<TeamActivity> = emptyList()
    override fun findByMemberId(memberId: Long): List<TeamActivity> = emptyList()
    override fun save(activity: TeamActivity): TeamActivity = activity.copy(id = 1)
}

@Repository
class TeamMemberPerformanceStubRepository : TeamMemberPerformanceRepository {
    override fun findByWorkspaceId(workspaceId: Long): List<TeamMemberPerformance> = emptyList()
    override fun findById(id: Long): TeamMemberPerformance? = null
    override fun save(member: TeamMemberPerformance): TeamMemberPerformance = member.copy(id = 1)
    override fun update(member: TeamMemberPerformance): TeamMemberPerformance = member
}
