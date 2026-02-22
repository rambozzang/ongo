package com.ongo.application.audience

import com.ongo.application.audience.dto.*
import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.audience.AudienceProfile
import com.ongo.domain.audience.AudienceRepository
import com.ongo.domain.audience.AudienceSegment
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AudienceUseCase(
    private val audienceRepository: AudienceRepository,
) {
    private val objectMapper = jacksonObjectMapper()

    fun getProfiles(userId: Long, sortBy: String?, page: Int, size: Int): List<AudienceProfileResponse> {
        val offset = page * size
        return audienceRepository.findProfilesByUserId(userId, sortBy ?: "engagement_score", size, offset)
            .map { it.toResponse() }
    }

    fun getProfileCount(userId: Long): Long = audienceRepository.countProfilesByUserId(userId)

    fun getProfile(userId: Long, profileId: Long): AudienceProfileResponse {
        val profile = audienceRepository.findProfileById(profileId) ?: throw NotFoundException("프로필", profileId)
        if (profile.userId != userId) throw BusinessException("FORBIDDEN", "해당 프로필에 대한 권한이 없습니다")
        return profile.toResponse()
    }

    @Transactional
    fun updateProfileTags(userId: Long, profileId: Long, request: UpdateProfileTagsRequest): AudienceProfileResponse {
        val profile = audienceRepository.findProfileById(profileId) ?: throw NotFoundException("프로필", profileId)
        if (profile.userId != userId) throw BusinessException("FORBIDDEN", "해당 프로필에 대한 권한이 없습니다")
        val tagsJson = objectMapper.writeValueAsString(request.tags)
        audienceRepository.updateProfileTags(profileId, tagsJson)
        return audienceRepository.findProfileById(profileId)!!.toResponse()
    }

    // Segments

    fun getSegments(userId: Long): List<AudienceSegmentResponse> =
        audienceRepository.findSegmentsByUserId(userId).map { it.toResponse() }

    @Transactional
    fun createSegment(userId: Long, request: CreateSegmentRequest): AudienceSegmentResponse {
        val segment = audienceRepository.saveSegment(
            AudienceSegment(
                userId = userId,
                name = request.name,
                description = request.description,
                conditions = request.conditions,
                autoUpdate = request.autoUpdate,
            )
        )
        return segment.toResponse()
    }

    @Transactional
    fun updateSegment(userId: Long, segmentId: Long, request: UpdateSegmentRequest): AudienceSegmentResponse {
        val segment = audienceRepository.findSegmentById(segmentId) ?: throw NotFoundException("세그먼트", segmentId)
        if (segment.userId != userId) throw BusinessException("FORBIDDEN", "해당 세그먼트에 대한 권한이 없습니다")
        audienceRepository.updateSegment(segmentId, request.name, request.description, request.conditions)
        return audienceRepository.findSegmentById(segmentId)!!.toResponse()
    }

    @Transactional
    fun deleteSegment(userId: Long, segmentId: Long) {
        val segment = audienceRepository.findSegmentById(segmentId) ?: throw NotFoundException("세그먼트", segmentId)
        if (segment.userId != userId) throw BusinessException("FORBIDDEN", "해당 세그먼트에 대한 권한이 없습니다")
        audienceRepository.deleteSegment(segmentId)
    }

    private fun AudienceProfile.toResponse(): AudienceProfileResponse {
        val tagList = try { objectMapper.readValue(tags ?: "[]", List::class.java) as List<String> } catch (_: Exception) { emptyList() }
        return AudienceProfileResponse(
            id = id!!,
            authorName = authorName,
            platform = platform,
            avatarUrl = avatarUrl,
            engagementScore = engagementScore,
            tags = tagList,
            totalInteractions = totalInteractions,
            positiveRatio = positiveRatio,
            firstSeenAt = firstSeenAt?.toString(),
            lastSeenAt = lastSeenAt?.toString(),
        )
    }

    private fun AudienceSegment.toResponse() = AudienceSegmentResponse(
        id = id!!,
        name = name,
        description = description,
        conditions = conditions,
        autoUpdate = autoUpdate,
        memberCount = memberCount,
        createdAt = createdAt?.toString(),
    )
}
