package com.ongo.domain.sentimentanalyzer

interface SentimentResultRepository {
    fun findByWorkspaceId(workspaceId: Long): List<SentimentResult>
    fun findById(id: Long): SentimentResult?
    fun save(result: SentimentResult): SentimentResult
}
