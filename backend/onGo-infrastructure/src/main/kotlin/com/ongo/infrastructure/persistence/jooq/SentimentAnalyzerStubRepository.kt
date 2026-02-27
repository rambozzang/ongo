package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.sentimentanalyzer.*
import org.springframework.stereotype.Repository

@Repository
class SentimentResultStubRepository : SentimentResultRepository {
    override fun findByWorkspaceId(workspaceId: Long): List<SentimentResult> = emptyList()
    override fun findById(id: Long): SentimentResult? = null
    override fun save(result: SentimentResult): SentimentResult = result.copy(id = 1)
}

@Repository
class CommentSentimentStubRepository : CommentSentimentRepository {
    override fun findByResultId(resultId: Long): List<CommentSentiment> = emptyList()
    override fun findByResultIdAndSentiment(resultId: Long, sentiment: String): List<CommentSentiment> = emptyList()
    override fun save(comment: CommentSentiment): CommentSentiment = comment.copy(id = 1)
    override fun saveBatch(comments: List<CommentSentiment>): List<CommentSentiment> = comments
}
