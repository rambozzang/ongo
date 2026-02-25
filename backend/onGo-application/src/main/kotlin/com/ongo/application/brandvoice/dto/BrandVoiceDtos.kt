package com.ongo.application.brandvoice.dto

import java.time.LocalDateTime

data class BrandVoiceProfileResponse(
    val id: Long,
    val name: String,
    val tone: String,
    val trainStatus: String,
    val sampleCount: Int,
    val vocabulary: String,
    val avoidWords: String,
    val emojiUsage: String,
    val avgSentenceLength: Int,
    val signaturePhrase: String?,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
)

data class CreateBrandVoiceProfileRequest(
    val name: String,
    val tone: String,
    val trainStatus: String = "PENDING",
    val sampleCount: Int = 0,
    val vocabulary: String = "[]",
    val avoidWords: String = "[]",
    val emojiUsage: String = "NONE",
    val avgSentenceLength: Int = 0,
    val signaturePhrase: String? = null,
)
