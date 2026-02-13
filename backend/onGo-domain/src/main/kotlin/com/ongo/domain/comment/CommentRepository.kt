package com.ongo.domain.comment

interface CommentRepository {
    fun findById(id: Long): Comment?
    fun findByUserId(userId: Long, page: Int, size: Int): List<Comment>
    fun findByUserIdAndVideoId(userId: Long, videoId: Long, page: Int, size: Int): List<Comment>
    fun findByUserIdAndPlatform(userId: Long, platform: String, page: Int, size: Int): List<Comment>
    fun findByUserIdAndSentiment(userId: Long, sentiment: String, page: Int, size: Int): List<Comment>
    fun countByUserId(userId: Long): Int
    fun save(comment: Comment): Comment
    fun update(comment: Comment): Comment
    fun delete(id: Long)
}
