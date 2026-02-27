package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.playlist.*
import org.springframework.stereotype.Repository

@Repository
class PlaylistStubRepository : PlaylistRepository {
    override fun findByUserId(userId: Long): List<Playlist> = emptyList()
    override fun findByUserIdAndPlatform(userId: Long, platform: String): List<Playlist> = emptyList()
    override fun findById(id: Long): Playlist? = null
    override fun save(playlist: Playlist): Playlist = playlist.copy(id = 1)
    override fun update(playlist: Playlist): Playlist = playlist
    override fun deleteById(id: Long) {}
}

@Repository
class PlaylistVideoStubRepository : PlaylistVideoRepository {
    override fun findByPlaylistId(playlistId: Long): List<PlaylistVideo> = emptyList()
    override fun findById(id: Long): PlaylistVideo? = null
    override fun save(video: PlaylistVideo): PlaylistVideo = video.copy(id = 1)
    override fun deleteById(id: Long) {}
    override fun deleteByPlaylistIdAndVideoId(playlistId: Long, videoId: String) {}
    override fun reorder(playlistId: Long, videoIds: List<String>) {}
}
