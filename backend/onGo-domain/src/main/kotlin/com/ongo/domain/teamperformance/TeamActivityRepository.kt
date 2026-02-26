package com.ongo.domain.teamperformance

interface TeamActivityRepository {
    fun findByWorkspaceId(workspaceId: Long, limit: Int = 20): List<TeamActivity>
    fun findByMemberId(memberId: Long): List<TeamActivity>
    fun save(activity: TeamActivity): TeamActivity
}
