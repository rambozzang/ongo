package com.ongo.domain.playlist

interface PlaylistRepository {
    fun findByUserId(userId: Long): List<Playlist>
    fun findByUserIdAndPlatform(userId: Long, platform: String): List<Playlist>
    fun findById(id: Long): Playlist?
    fun save(playlist: Playlist): Playlist
    fun update(playlist: Playlist): Playlist
    fun deleteById(id: Long)
}
