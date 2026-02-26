package com.ongo.domain.teamperformance

interface TeamMemberPerformanceRepository {
    fun findByWorkspaceId(workspaceId: Long): List<TeamMemberPerformance>
    fun findById(id: Long): TeamMemberPerformance?
    fun save(member: TeamMemberPerformance): TeamMemberPerformance
    fun update(member: TeamMemberPerformance): TeamMemberPerformance
}
