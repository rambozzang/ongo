package com.ongo.application.video

import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.video.VideoFavoriteRepository
import com.ongo.domain.video.VideoRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class VideoFavoriteResponse(
    val videoId: Long,
    val favorite: Boolean,
)

@Service
class VideoFavoriteUseCase(
    private val videoRepository: VideoRepository,
    private val favoriteRepository: VideoFavoriteRepository,
) {
    fun list(userId: Long): List<Long> = favoriteRepository.findVideoIdsByUserId(userId)

    @Transactional
    fun toggle(userId: Long, videoId: Long): VideoFavoriteResponse {
        val video = videoRepository.findById(videoId) ?: throw NotFoundException("영상", videoId)
        if (video.userId != userId) throw ForbiddenException("해당 영상에 대한 접근 권한이 없습니다")
        val next = !favoriteRepository.exists(userId, videoId)
        if (next) favoriteRepository.add(userId, videoId) else favoriteRepository.remove(userId, videoId)
        return VideoFavoriteResponse(videoId, next)
    }

    @Transactional
    fun remove(userId: Long, videoId: Long) = favoriteRepository.remove(userId, videoId)

    @Transactional
    fun removeAll(userId: Long) = favoriteRepository.removeAll(userId)
}
