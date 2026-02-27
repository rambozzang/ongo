package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.commentsummary.*
import org.springframework.stereotype.Repository

@Repository
class CommentSummaryResultStubRepository : CommentSummaryResultRepository {
    override fun findByWorkspaceId(workspaceId: Long): List<CommentSummaryResult> = emptyList()
    override fun findByWorkspaceIdAndPlatform(workspaceId: Long, platform: String): List<CommentSummaryResult> = emptyList()
    override fun save(result: CommentSummaryResult): CommentSummaryResult = result.copy(id = 1)
}

@Repository
class TopCommentStubRepository : TopCommentRepository {
    override fun findBySummaryId(summaryId: Long): List<TopComment> = emptyList()
}
