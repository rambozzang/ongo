package com.ongo.application.contentstudio

import com.ongo.application.contentstudio.dto.*
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.contentstudio.ContentClip
import com.ongo.domain.contentstudio.ContentStudioRepository
import com.ongo.domain.contentstudio.VideoCaption
import com.ongo.domain.contentstudio.AiThumbnail
import com.ongo.domain.video.VideoRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ContentStudioUseCase(
    private val contentStudioRepository: ContentStudioRepository,
    private val videoRepository: VideoRepository,
) {

    fun listVideos(userId: Long): List<VideoSummaryResponse> {
        return videoRepository.findByUserId(userId, page = 0, size = 100).map {
            VideoSummaryResponse(
                id = it.id!!,
                title = it.title,
                duration = it.durationSeconds,
                thumbnailUrl = it.thumbnailUrls.firstOrNull(),
                fileUrl = it.fileUrl,
            )
        }
    }

    fun listHistory(userId: Long): List<ContentStudioHistoryResponse> {
        return emptyList()
    }

    fun listClips(userId: Long): List<ContentClipResponse> {
        return contentStudioRepository.findClipsByUserId(userId).map { it.toResponse() }
    }

    @Transactional
    fun createClip(userId: Long, request: CreateClipRequest): ContentClipResponse {
        val clip = ContentClip(
            userId = userId,
            sourceVideoId = request.sourceVideoId,
            title = request.title,
            startTimeMs = request.startTimeMs,
            endTimeMs = request.endTimeMs,
            aspectRatio = request.aspectRatio,
        )
        return contentStudioRepository.saveClip(clip).toResponse()
    }

    @Transactional
    fun deleteClip(userId: Long, clipId: Long) {
        val clip = contentStudioRepository.findClipById(clipId) ?: throw NotFoundException("클립", clipId)
        if (clip.userId != userId) throw ForbiddenException("해당 클립에 대한 권한이 없습니다")
        contentStudioRepository.deleteClip(clipId)
    }

    fun listCaptions(videoId: Long): List<VideoCaptionResponse> {
        return contentStudioRepository.findCaptionsByVideoId(videoId).map { it.toResponse() }
    }

    @Transactional
    fun createCaption(userId: Long, request: CreateCaptionRequest): VideoCaptionResponse {
        val caption = VideoCaption(
            videoId = request.videoId,
            language = request.language,
            captionData = request.captionData,
        )
        return contentStudioRepository.saveCaption(caption).toResponse()
    }

    @Transactional
    fun generateCaption(userId: Long, request: GenerateCaptionRequest): VideoCaptionResponse {
        // AI 자막 생성 시뮬레이션 - 실제로는 STT 서비스를 호출해야 하지만
        // 현재는 빈 자막 데이터를 생성하여 DB에 저장
        val captionData = """[{"start":0,"end":1000,"text":"자동 생성된 자막"}]"""
        val caption = VideoCaption(
            videoId = request.videoId,
            language = request.language,
            captionData = captionData,
            status = "COMPLETED",
        )
        return contentStudioRepository.saveCaption(caption).toResponse()
    }

    @Transactional
    fun updateCaption(userId: Long, captionId: Long, request: UpdateCaptionRequest): VideoCaptionResponse {
        val caption = contentStudioRepository.findCaptionById(captionId) ?: throw NotFoundException("자막", captionId)
        val updated = caption.copy(
            language = request.language ?: caption.language,
            captionData = request.captionData ?: caption.captionData,
            status = request.status ?: caption.status,
        )
        return contentStudioRepository.updateCaption(updated).toResponse()
    }

    @Transactional
    fun deleteCaption(userId: Long, captionId: Long) {
        contentStudioRepository.findCaptionById(captionId) ?: throw NotFoundException("자막", captionId)
        contentStudioRepository.deleteCaption(captionId)
    }

    fun listThumbnails(videoId: Long): List<AiThumbnailResponse> {
        return contentStudioRepository.findThumbnailsByVideoId(videoId).map { it.toResponse() }
    }

    @Transactional
    fun generateThumbnail(userId: Long, request: GenerateThumbnailRequest): AiThumbnailResponse {
        val thumbnail = AiThumbnail(
            videoId = request.videoId,
            style = request.style,
            textOverlay = request.textOverlay,
        )
        return contentStudioRepository.saveThumbnail(thumbnail).toResponse()
    }

    @Transactional
    fun deleteThumbnail(userId: Long, thumbnailId: Long) {
        contentStudioRepository.findThumbnailById(thumbnailId) ?: throw NotFoundException("썸네일", thumbnailId)
        contentStudioRepository.deleteThumbnail(thumbnailId)
    }

    private fun ContentClip.toResponse() = ContentClipResponse(
        id = id!!,
        sourceVideoId = sourceVideoId,
        title = title,
        startTimeMs = startTimeMs,
        endTimeMs = endTimeMs,
        aspectRatio = aspectRatio,
        status = status,
        outputUrl = outputUrl,
        createdAt = createdAt,
    )

    private fun VideoCaption.toResponse() = VideoCaptionResponse(
        id = id!!,
        videoId = videoId,
        language = language,
        captionData = captionData,
        status = status,
        createdAt = createdAt,
    )

    private fun AiThumbnail.toResponse() = AiThumbnailResponse(
        id = id!!,
        videoId = videoId,
        style = style,
        textOverlay = textOverlay,
        imageUrl = imageUrl,
        createdAt = createdAt,
    )
}
