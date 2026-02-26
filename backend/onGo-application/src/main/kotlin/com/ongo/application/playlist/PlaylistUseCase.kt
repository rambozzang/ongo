package com.ongo.application.playlist

import com.ongo.application.playlist.dto.*
import com.ongo.domain.playlist.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class PlaylistUseCase(
    private val playlistRepository: PlaylistRepository,
    private val videoRepository: PlaylistVideoRepository,
) {

    fun getPlaylists(userId: Long, platform: String? = null): List<PlaylistResponse> {
        val list = if (platform != null) playlistRepository.findByUserIdAndPlatform(userId, platform)
        else playlistRepository.findByUserId(userId)
        return list.map { toResponse(it) }
    }

    fun getPlaylist(userId: Long, id: Long): PlaylistResponse {
        val pl = playlistRepository.findById(id)
            ?: throw IllegalArgumentException("재생목록을 찾을 수 없습니다")
        return toResponse(pl)
    }

    @Transactional
    fun createPlaylist(userId: Long, request: CreatePlaylistRequest): PlaylistResponse {
        val saved = playlistRepository.save(
            Playlist(
                userId = userId, title = request.title, description = request.description,
                platform = request.platform, visibility = request.visibility,
                tags = request.tags ?: emptyList(),
            )
        )
        return toResponse(saved)
    }

    @Transactional
    fun updatePlaylist(userId: Long, id: Long, request: UpdatePlaylistRequest): PlaylistResponse {
        val pl = playlistRepository.findById(id)
            ?: throw IllegalArgumentException("재생목록을 찾을 수 없습니다")
        val updated = playlistRepository.update(
            pl.copy(
                title = request.title ?: pl.title,
                description = request.description ?: pl.description,
                visibility = request.visibility ?: pl.visibility,
                tags = request.tags ?: pl.tags,
            )
        )
        return toResponse(updated)
    }

    @Transactional
    fun deletePlaylist(userId: Long, id: Long) {
        playlistRepository.deleteById(id)
    }

    fun getVideos(userId: Long, playlistId: Long): List<PlaylistVideoResponse> {
        return videoRepository.findByPlaylistId(playlistId).map { toVideoResponse(it) }
    }

    @Transactional
    fun addVideo(userId: Long, playlistId: Long, request: AddVideoRequest): PlaylistVideoResponse {
        val saved = videoRepository.save(
            PlaylistVideo(playlistId = playlistId, videoId = request.videoId, title = "비디오", position = request.position ?: 0)
        )
        val pl = playlistRepository.findById(playlistId)
        if (pl != null) playlistRepository.update(pl.copy(videoCount = pl.videoCount + 1))
        return toVideoResponse(saved)
    }

    @Transactional
    fun removeVideo(userId: Long, playlistId: Long, videoId: String) {
        videoRepository.deleteByPlaylistIdAndVideoId(playlistId, videoId)
        val pl = playlistRepository.findById(playlistId)
        if (pl != null && pl.videoCount > 0) playlistRepository.update(pl.copy(videoCount = pl.videoCount - 1))
    }

    @Transactional
    fun reorderVideos(userId: Long, playlistId: Long, request: ReorderVideosRequest) {
        videoRepository.reorder(playlistId, request.videoIds)
    }

    @Transactional
    fun syncPlaylist(userId: Long, id: Long): PlaylistResponse {
        val pl = playlistRepository.findById(id)
            ?: throw IllegalArgumentException("재생목록을 찾을 수 없습니다")
        val updated = playlistRepository.update(pl.copy(isSynced = true, lastSyncedAt = LocalDateTime.now()))
        return toResponse(updated)
    }

    fun getSummary(userId: Long): PlaylistSummaryResponse {
        val all = playlistRepository.findByUserId(userId)
        val totalVideos = all.sumOf { it.videoCount }
        val totalViews = all.sumOf { it.totalViews }
        val breakdown = all.groupBy { it.platform }.map { PlatformCount(it.key, it.value.size) }
        return PlaylistSummaryResponse(all.size, totalVideos, totalViews, breakdown)
    }

    private fun toResponse(pl: Playlist) = PlaylistResponse(
        id = pl.id, title = pl.title, description = pl.description, platform = pl.platform,
        platformPlaylistId = pl.platformPlaylistId, visibility = pl.visibility, thumbnailUrl = pl.thumbnailUrl,
        videoCount = pl.videoCount, totalViews = pl.totalViews, totalDuration = pl.totalDuration,
        tags = pl.tags, isSynced = pl.isSynced, lastSyncedAt = pl.lastSyncedAt?.toString(),
        createdAt = pl.createdAt.toString(), updatedAt = pl.updatedAt.toString(),
    )

    private fun toVideoResponse(v: PlaylistVideo) = PlaylistVideoResponse(
        id = v.id, playlistId = v.playlistId, videoId = v.videoId, title = v.title,
        thumbnailUrl = v.thumbnailUrl, duration = v.duration, views = v.views,
        position = v.position, addedAt = v.addedAt.toString(),
    )
}
