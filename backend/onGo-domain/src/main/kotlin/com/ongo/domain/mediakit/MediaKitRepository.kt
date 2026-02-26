package com.ongo.domain.mediakit

interface MediaKitRepository {
    fun findById(id: Long): MediaKit?
    fun findByUserId(userId: Long): List<MediaKit>
    fun save(kit: MediaKit): MediaKit
    fun update(kit: MediaKit): MediaKit
    fun delete(id: Long)
}
