package com.ongo.application.video

import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.video.VideoProcessingProgressRepository
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.video.entity.VideoProcessingProgress
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class VideoProcessingProgressUseCase(
    private val videoRepository: VideoRepository,
    private val progressRepository: VideoProcessingProgressRepository,
) {

    fun getProgress(userId: Long, videoId: Long): List<VideoProcessingProgress> {
        val video = videoRepository.findById(videoId)
            ?: throw NotFoundException("영상", videoId)
        if (video.userId != userId) throw ForbiddenException("해당 영상에 대한 접근 권한이 없습니다")

        return progressRepository.findByVideoId(videoId)
    }

    fun validateAccess(userId: Long, videoId: Long) {
        val video = videoRepository.findById(videoId)
            ?: throw NotFoundException("영상", videoId)
        if (video.userId != userId) throw ForbiddenException("해당 영상에 대한 접근 권한이 없습니다")
    }

    fun fetchProgress(videoId: Long): List<VideoProcessingProgress> {
        return progressRepository.findByVideoId(videoId)
    }
}
