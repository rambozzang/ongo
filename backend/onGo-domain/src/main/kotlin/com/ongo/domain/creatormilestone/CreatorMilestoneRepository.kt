package com.ongo.domain.creatormilestone

interface CreatorMilestoneRepository {
    fun findByWorkspaceId(workspaceId: Long): List<CreatorMilestone>
    fun findByWorkspaceIdAndStatus(workspaceId: Long, status: String): List<CreatorMilestone>
    fun findById(id: Long): CreatorMilestone?
    fun save(milestone: CreatorMilestone): CreatorMilestone
    fun update(milestone: CreatorMilestone): CreatorMilestone
    fun deleteById(id: Long)
}
