package com.ongo.application.mediakit

import com.ongo.application.mediakit.dto.*
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.mediakit.MediaKit
import com.ongo.domain.mediakit.MediaKitRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MediaKitUseCase(
    private val mediaKitRepository: MediaKitRepository,
) {

    fun getTemplates(): List<MediaKitTemplateResponse> {
        return listOf(
            MediaKitTemplateResponse(1, "모던 스타일", "MODERN", null),
            MediaKitTemplateResponse(2, "클래식 스타일", "CLASSIC", null),
            MediaKitTemplateResponse(3, "미니멀 스타일", "MINIMAL", null),
            MediaKitTemplateResponse(4, "크리에이티브 스타일", "CREATIVE", null),
        )
    }

    @Transactional
    fun generate(userId: Long, request: MediaKitGenerateRequest): MediaKitResponse {
        val kit = MediaKit(
            userId = userId,
            title = "내 미디어 킷",
            templateStyle = request.templateStyle,
            bio = request.customBio,
            rateCards = if (request.includeRateCards) "[]" else "[]",
            campaignResults = if (request.includeCampaigns) "[]" else "[]",
        )
        return mediaKitRepository.save(kit).toResponse()
    }

    fun getMyKits(userId: Long): List<MediaKitResponse> {
        return mediaKitRepository.findByUserId(userId).map { it.toResponse() }
    }

    fun getKit(userId: Long, kitId: Long): MediaKitResponse {
        val kit = mediaKitRepository.findById(kitId)
            ?: throw NotFoundException("미디어 킷", kitId)
        if (kit.userId != userId) throw ForbiddenException("해당 미디어 킷에 대한 권한이 없습니다")
        return kit.toResponse()
    }

    @Transactional
    fun updateKit(userId: Long, kitId: Long, request: UpdateMediaKitRequest): MediaKitResponse {
        val kit = mediaKitRepository.findById(kitId)
            ?: throw NotFoundException("미디어 킷", kitId)
        if (kit.userId != userId) throw ForbiddenException("해당 미디어 킷에 대한 권한이 없습니다")
        val updated = kit.copy(
            title = request.title ?: kit.title,
            bio = request.bio ?: kit.bio,
            rateCards = request.rateCards ?: kit.rateCards,
            contactEmail = request.contactEmail ?: kit.contactEmail,
        )
        return mediaKitRepository.update(updated).toResponse()
    }

    @Transactional
    fun publishKit(userId: Long, kitId: Long): MediaKitResponse {
        val kit = mediaKitRepository.findById(kitId)
            ?: throw NotFoundException("미디어 킷", kitId)
        if (kit.userId != userId) throw ForbiddenException("해당 미디어 킷에 대한 권한이 없습니다")
        val published = kit.copy(
            status = "PUBLISHED",
            publishedUrl = "/media-kit/public/$kitId",
        )
        return mediaKitRepository.update(published).toResponse()
    }

    @Transactional
    fun deleteKit(userId: Long, kitId: Long) {
        val kit = mediaKitRepository.findById(kitId)
            ?: throw NotFoundException("미디어 킷", kitId)
        if (kit.userId != userId) throw ForbiddenException("해당 미디어 킷에 대한 권한이 없습니다")
        mediaKitRepository.delete(kitId)
    }

    private fun MediaKit.toResponse() = MediaKitResponse(
        id = id!!,
        title = title,
        templateStyle = templateStyle,
        bio = bio,
        profileImageUrl = profileImageUrl,
        platforms = platforms,
        demographics = demographics,
        topContent = topContent,
        campaignResults = campaignResults,
        rateCards = rateCards,
        contactEmail = contactEmail,
        status = status,
        publishedUrl = publishedUrl,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
