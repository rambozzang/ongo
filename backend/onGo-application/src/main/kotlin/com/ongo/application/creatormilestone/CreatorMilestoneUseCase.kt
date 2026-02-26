package com.ongo.application.creatormilestone

import com.ongo.application.creatormilestone.dto.*
import com.ongo.domain.creatormilestone.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class CreatorMilestoneUseCase(
    private val milestoneRepository: CreatorMilestoneRepository,
) {

    fun getMilestones(workspaceId: Long, status: String? = null): List<CreatorMilestoneResponse> {
        val list = if (status != null) milestoneRepository.findByWorkspaceIdAndStatus(workspaceId, status)
        else milestoneRepository.findByWorkspaceId(workspaceId)
        return list.map { toResponse(it) }
    }

    fun getMilestone(id: Long): CreatorMilestoneResponse? {
        return milestoneRepository.findById(id)?.let { toResponse(it) }
    }

    @Transactional
    fun createMilestone(workspaceId: Long, request: CreateMilestoneRequest): CreatorMilestoneResponse {
        val saved = milestoneRepository.save(
            CreatorMilestone(
                workspaceId = workspaceId,
                type = request.type,
                title = request.title,
                description = request.description,
                targetValue = request.targetValue,
                platform = request.platform,
                targetDate = request.targetDate?.let { LocalDate.parse(it) },
            )
        )
        return toResponse(saved)
    }

    @Transactional
    fun deleteMilestone(workspaceId: Long, id: Long) {
        milestoneRepository.deleteById(id)
    }

    fun getSummary(workspaceId: Long): CreatorMilestoneSummaryResponse {
        val all = milestoneRepository.findByWorkspaceId(workspaceId)
        val achieved = all.count { it.status == "ACHIEVED" }
        val inProgress = all.count { it.status == "IN_PROGRESS" }
        val rate = if (all.isNotEmpty()) achieved.toDouble() / all.size * 100 else 0.0
        val next = all.filter { it.status == "IN_PROGRESS" }.maxByOrNull { it.progress }?.title ?: "-"
        return CreatorMilestoneSummaryResponse(all.size, achieved, inProgress, next, rate)
    }

    private fun toResponse(m: CreatorMilestone) = CreatorMilestoneResponse(
        id = m.id, type = m.type, title = m.title, description = m.description,
        targetValue = m.targetValue, currentValue = m.currentValue,
        progress = m.progress.toDouble(), platform = m.platform, status = m.status,
        achievedAt = m.achievedAt?.toString(), targetDate = m.targetDate?.toString(),
        createdAt = m.createdAt.toString(),
    )
}
