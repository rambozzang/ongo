package com.ongo.domain.ai

interface WeeklyDigestRepository {
    fun findById(id: Long): WeeklyDigest?
    fun findLatestByUserId(userId: Long): WeeklyDigest?
    fun findByUserId(userId: Long, page: Int, size: Int): List<WeeklyDigest>
    fun countByUserId(userId: Long): Int
    fun save(digest: WeeklyDigest): WeeklyDigest
}
