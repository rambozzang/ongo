package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.creatormilestone.*
import org.springframework.stereotype.Repository

@Repository
class CreatorMilestoneStubRepository : CreatorMilestoneRepository {
    override fun findByWorkspaceId(workspaceId: Long): List<CreatorMilestone> = emptyList()
    override fun findByWorkspaceIdAndStatus(workspaceId: Long, status: String): List<CreatorMilestone> = emptyList()
    override fun findById(id: Long): CreatorMilestone? = null
    override fun save(milestone: CreatorMilestone): CreatorMilestone = milestone.copy(id = 1)
    override fun update(milestone: CreatorMilestone): CreatorMilestone = milestone
    override fun deleteById(id: Long) {}
}
