package com.ongo.domain.commentsummary

interface CommentSummaryResultRepository {
    fun findByWorkspaceId(workspaceId: Long): List<CommentSummaryResult>
    fun findByWorkspaceIdAndPlatform(workspaceId: Long, platform: String): List<CommentSummaryResult>
    fun save(result: CommentSummaryResult): CommentSummaryResult
}
