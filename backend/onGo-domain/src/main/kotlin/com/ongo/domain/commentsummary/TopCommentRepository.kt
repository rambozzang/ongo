package com.ongo.domain.commentsummary

interface TopCommentRepository {
    fun findBySummaryId(summaryId: Long): List<TopComment>
}
