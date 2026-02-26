package com.ongo.application.teamperformance

import com.ongo.application.teamperformance.dto.*
import com.ongo.domain.teamperformance.*
import org.springframework.stereotype.Service

@Service
class TeamPerformanceUseCase(
    private val memberRepository: TeamMemberPerformanceRepository,
    private val activityRepository: TeamActivityRepository,
) {

    fun getMembers(workspaceId: Long): List<TeamMemberPerformanceResponse> {
        return memberRepository.findByWorkspaceId(workspaceId).map { toMemberResponse(it) }
    }

    fun getMember(id: Long): TeamMemberPerformanceResponse? {
        return memberRepository.findById(id)?.let { toMemberResponse(it) }
    }

    fun getActivities(workspaceId: Long, limit: Int = 20): List<TeamActivityResponse> {
        return activityRepository.findByWorkspaceId(workspaceId, limit).map { toActivityResponse(it) }
    }

    fun getSummary(workspaceId: Long): TeamPerformanceSummaryResponse {
        val members = memberRepository.findByWorkspaceId(workspaceId)
        val avgRate = if (members.isNotEmpty()) members.map { it.completionRate.toDouble() }.average() else 0.0
        val totalTasks = members.sumOf { it.tasksCompleted }
        val totalContent = members.sumOf { it.contentProduced }
        val top = members.maxByOrNull { it.completionRate }?.name ?: "-"
        return TeamPerformanceSummaryResponse(members.size, avgRate, totalTasks, totalContent, top)
    }

    private fun toMemberResponse(m: TeamMemberPerformance) = TeamMemberPerformanceResponse(
        id = m.id, name = m.name, email = m.email, role = m.role, avatar = m.avatar,
        tasksCompleted = m.tasksCompleted, tasksAssigned = m.tasksAssigned,
        completionRate = m.completionRate.toDouble(), avgCompletionTime = m.avgCompletionTime.toDouble(),
        contentProduced = m.contentProduced, onTimeRate = m.onTimeRate.toDouble(),
        rating = m.rating.toDouble(), streak = m.streak, lastActiveAt = m.lastActiveAt?.toString(),
    )

    private fun toActivityResponse(a: TeamActivity) = TeamActivityResponse(
        id = a.id, memberId = a.memberId, memberName = a.memberName,
        action = a.action, target = a.target, timestamp = a.timestamp.toString(),
    )
}
