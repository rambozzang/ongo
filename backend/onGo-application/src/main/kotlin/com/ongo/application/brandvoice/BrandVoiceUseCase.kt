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

    @Transactional
    fun trainVoice(userId: Long, request: TrainVoiceRequest): TrainVoiceResponse {
        val profile = if (request.profileId != null) {
            val existing = brandVoiceRepository.findById(request.profileId)
                ?: throw NotFoundException("브랜드 보이스 프로필", request.profileId)
            if (existing.userId != userId) throw ForbiddenException("해당 브랜드 보이스 프로필에 대한 권한이 없습니다")
            val updated = existing.copy(
                tone = request.tone ?: existing.tone,
                trainStatus = "TRAINED",
                sampleCount = request.trainingSamples.size,
            )
            brandVoiceRepository.update(updated)
        } else {
            val newProfile = BrandVoiceProfile(
                userId = userId,
                name = "Voice Profile ${System.currentTimeMillis()}",
                tone = request.tone ?: "NEUTRAL",
                trainStatus = "TRAINED",
                sampleCount = request.trainingSamples.size,
            )
            brandVoiceRepository.save(newProfile)
        }
        return TrainVoiceResponse(
            id = profile.id!!,
            name = profile.name,
            status = profile.trainStatus,
        )
    }

    fun generateText(userId: Long, request: GenerateTextRequest): GenerateTextResponse {
        val profile = brandVoiceRepository.findById(request.profileId)
            ?: throw NotFoundException("브랜드 보이스 프로필", request.profileId)
        if (profile.userId != userId) throw ForbiddenException("해당 브랜드 보이스 프로필에 대한 권한이 없습니다")
        val generatedText = "[${profile.tone} 톤] ${request.prompt}"
        return GenerateTextResponse(
            generatedText = generatedText,
            voiceProfileId = profile.id!!,
        )
    }

    fun analyzeText(userId: Long, request: AnalyzeTextRequest): AnalyzeTextResponse {
        val text = request.text
        val words = text.split("\\s+".toRegex())
        val tone = when {
            text.contains("!") -> "ENTHUSIASTIC"
            text.contains("?") -> "QUESTIONING"
            else -> "NEUTRAL"
        }
        val formality = if (words.any { it.length > 6 }) 0.7 else 0.3
        val emotion = when {
            text.contains("좋") || text.contains("great") -> "POSITIVE"
            text.contains("나쁘") || text.contains("bad") -> "NEGATIVE"
            else -> "NEUTRAL"
        }
        val keywords = words.filter { it.length > 2 }.take(5)
        return AnalyzeTextResponse(
            tone = tone,
            formality = formality,
            emotion = emotion,
            keywords = keywords,
        )
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
