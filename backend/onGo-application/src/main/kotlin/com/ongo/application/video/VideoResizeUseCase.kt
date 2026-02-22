package com.ongo.application.video

import com.ongo.application.credit.CreditService
import com.ongo.application.video.dto.ResizeRequest
import com.ongo.application.video.dto.VideoResizeResponse
import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.event.VideoResizeRequestedEvent
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.video.VideoResizeRepository
import com.ongo.domain.video.entity.VideoResize
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class VideoResizeUseCase(
    private val videoRepository: VideoRepository,
    private val resizeRepository: VideoResizeRepository,
    private val creditService: CreditService,
    private val eventPublisher: ApplicationEventPublisher,
) {
    companion object {
        const val CREDIT_COST = 3
    }

    @Transactional
    fun requestResize(userId: Long, videoId: Long, request: ResizeRequest): List<VideoResizeResponse> {
        val video = videoRepository.findById(videoId) ?: throw NotFoundException("영상", videoId)
        if (video.userId != userId) throw BusinessException("FORBIDDEN", "해당 영상에 대한 권한이 없습니다")

        val validRatios = VideoTranscodeService.RESIZE_SPECS.keys
        val requestedRatios = request.aspectRatios.filter { it in validRatios }
        if (requestedRatios.isEmpty()) throw BusinessException("INVALID_RATIO", "유효한 비율을 선택해주세요: $validRatios")

        val totalCost = requestedRatios.size * CREDIT_COST
        creditService.validateAndDeduct(userId, totalCost, "VIDEO_RESIZE")

        val results = requestedRatios.map { ratio ->
            val spec = VideoTranscodeService.RESIZE_SPECS[ratio]!!
            val resize = resizeRepository.save(
                VideoResize(
                    userId = userId,
                    originalVideoId = videoId,
                    aspectRatio = ratio,
                    width = spec.width,
                    height = spec.height,
                )
            )
            eventPublisher.publishEvent(VideoResizeRequestedEvent(resize.id!!, videoId, userId))
            resize.toResponse()
        }
        return results
    }

    fun getResizes(userId: Long, videoId: Long): List<VideoResizeResponse> =
        resizeRepository.findByOriginalVideoId(videoId).map { it.toResponse() }

    private fun VideoResize.toResponse() = VideoResizeResponse(
        id = id!!,
        originalVideoId = originalVideoId,
        aspectRatio = aspectRatio,
        width = width,
        height = height,
        fileUrl = fileUrl,
        status = status,
        createdAt = createdAt?.toString(),
    )
}
