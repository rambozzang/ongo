package com.ongo.domain.sentimentanalyzer

interface CommentSentimentRepository {
    fun findByResultId(resultId: Long): List<CommentSentiment>
    fun findByResultIdAndSentiment(resultId: Long, sentiment: String): List<CommentSentiment>
    fun save(comment: CommentSentiment): CommentSentiment
    fun saveBatch(comments: List<CommentSentiment>): List<CommentSentiment>
}
