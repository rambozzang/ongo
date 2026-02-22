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

    fun findByPlatformAndPlatformCommentId(platform: String, platformCommentId: String): Comment?
    fun upsertBatch(comments: List<Comment>): Int
    fun findByUserIdFiltered(
        userId: Long,
        videoId: Long? = null,
        platform: String? = null,
        sentiment: String? = null,
        searchText: String? = null,
        startDate: java.time.LocalDateTime? = null,
        endDate: java.time.LocalDateTime? = null,
        page: Int = 0,
        size: Int = 20,
    ): List<Comment>

    fun countByUserIdFiltered(
        userId: Long,
        videoId: Long? = null,
        platform: String? = null,
        sentiment: String? = null,
        searchText: String? = null,
        startDate: java.time.LocalDateTime? = null,
        endDate: java.time.LocalDateTime? = null,
    ): Int

    fun countByUserIdGroupedBySentiment(userId: Long): Map<String, Int>

    fun findSentimentGroupedByDate(userId: Long, days: Int): Map<java.time.LocalDate, Map<String, Int>>

    fun findRecentNegativeComments(userId: Long, limit: Int): List<Comment>

    fun findByIds(ids: List<Long>): List<Comment>
}
