package com.ongo.application.videoresizer

import com.ongo.application.videoresizer.dto.*
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.videoresizer.ResizeJob
import com.ongo.domain.videoresizer.VideoResizerRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class VideoResizerUseCase(
    private val videoResizerRepository: VideoResizerRepository,
) {

    fun listResizeJobs(userId: Long): List<ResizeJobResponse> {
        return videoResizerRepository.findByUserId(userId).map { it.toResponse() }
    }

    @Transactional
    fun createResizeJob(userId: Long, request: CreateResizeJobRequest): ResizeJobResponse {
        val job = ResizeJob(
            userId = userId,
            videoId = request.videoId,
            videoTitle = request.videoTitle,
            originalAspectRatio = request.originalAspectRatio,
            targetAspectRatio = request.targetAspectRatio,
            platform = request.platform,
            smartCrop = request.smartCrop,
            focusPointX = request.focusPointX,
            focusPointY = request.focusPointY,
        )
        return videoResizerRepository.save(job).toResponse()
    }

    @Transactional
    fun deleteResizeJob(userId: Long, jobId: Long) {
        val job = videoResizerRepository.findById(jobId) ?: throw NotFoundException("리사이즈 작업", jobId)
        if (job.userId != userId) throw ForbiddenException("해당 리사이즈 작업에 대한 권한이 없습니다")
        videoResizerRepository.delete(jobId)
    }

    private fun ResizeJob.toResponse() = ResizeJobResponse(
        id = id!!,
        videoId = videoId,
        videoTitle = videoTitle,
        originalAspectRatio = originalAspectRatio,
        targetAspectRatio = targetAspectRatio,
        platform = platform,
        status = status,
        progress = progress,
        outputUrl = outputUrl,
        thumbnailUrl = thumbnailUrl,
        smartCrop = smartCrop,
        focusPointX = focusPointX,
        focusPointY = focusPointY,
        createdAt = createdAt,
        completedAt = completedAt,
    )
}
