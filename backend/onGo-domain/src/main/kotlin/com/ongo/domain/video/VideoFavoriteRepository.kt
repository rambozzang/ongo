package com.ongo.domain.video

interface VideoFavoriteRepository {
    fun findVideoIdsByUserId(userId: Long): List<Long>
    fun exists(userId: Long, videoId: Long): Boolean
    fun add(userId: Long, videoId: Long)
    fun remove(userId: Long, videoId: Long)
    fun removeAll(userId: Long)
}
