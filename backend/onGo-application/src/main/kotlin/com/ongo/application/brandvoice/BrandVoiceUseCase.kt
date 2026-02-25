package com.ongo.application.brandvoice

import com.ongo.application.brandvoice.dto.*
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.brandvoice.BrandVoiceProfile
import com.ongo.domain.brandvoice.BrandVoiceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class BrandVoiceUseCase(
    private val brandVoiceRepository: BrandVoiceRepository,
) {

    fun listProfiles(userId: Long): List<BrandVoiceProfileResponse> {
        return brandVoiceRepository.findByUserId(userId).map { it.toResponse() }
    }

    fun getProfile(userId: Long, profileId: Long): BrandVoiceProfileResponse {
        val profile = brandVoiceRepository.findById(profileId) ?: throw NotFoundException("브랜드 보이스 프로필", profileId)
        if (profile.userId != userId) throw ForbiddenException("해당 브랜드 보이스 프로필에 대한 권한이 없습니다")
        return profile.toResponse()
    }

    @Transactional
    fun createProfile(userId: Long, request: CreateBrandVoiceProfileRequest): BrandVoiceProfileResponse {
        val profile = BrandVoiceProfile(
            userId = userId,
            name = request.name,
            tone = request.tone,
            trainStatus = request.trainStatus,
            sampleCount = request.sampleCount,
            vocabulary = request.vocabulary,
            avoidWords = request.avoidWords,
            emojiUsage = request.emojiUsage,
            avgSentenceLength = request.avgSentenceLength,
            signaturePhrase = request.signaturePhrase,
        )
        return brandVoiceRepository.save(profile).toResponse()
    }

    @Transactional
    fun deleteProfile(userId: Long, profileId: Long) {
        val profile = brandVoiceRepository.findById(profileId) ?: throw NotFoundException("브랜드 보이스 프로필", profileId)
        if (profile.userId != userId) throw ForbiddenException("해당 브랜드 보이스 프로필에 대한 권한이 없습니다")
        brandVoiceRepository.delete(profileId)
    }

    private fun BrandVoiceProfile.toResponse() = BrandVoiceProfileResponse(
        id = id!!,
        name = name,
        tone = tone,
        trainStatus = trainStatus,
        sampleCount = sampleCount,
        vocabulary = vocabulary,
        avoidWords = avoidWords,
        emojiUsage = emojiUsage,
        avgSentenceLength = avgSentenceLength,
        signaturePhrase = signaturePhrase,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
