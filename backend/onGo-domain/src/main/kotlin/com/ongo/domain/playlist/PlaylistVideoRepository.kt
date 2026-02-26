package com.ongo.domain.playlist

interface PlaylistVideoRepository {
    fun findByPlaylistId(playlistId: Long): List<PlaylistVideo>
    fun findById(id: Long): PlaylistVideo?
    fun save(video: PlaylistVideo): PlaylistVideo
    fun deleteById(id: Long)
    fun deleteByPlaylistIdAndVideoId(playlistId: Long, videoId: String)
    fun reorder(playlistId: Long, videoIds: List<String>)
}
