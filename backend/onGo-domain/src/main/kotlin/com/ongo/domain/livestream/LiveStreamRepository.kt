package com.ongo.domain.livestream

interface LiveStreamRepository {
    fun findById(id: Long): LiveStream?
    fun findByUserId(userId: Long): List<LiveStream>
    fun findByStatus(userId: Long, status: String): List<LiveStream>
    fun save(stream: LiveStream): LiveStream
    fun updateStatus(id: Long, status: String)
}
