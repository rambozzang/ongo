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

data class TrainVoiceRequest(
    val profileId: Long? = null,
    val trainingSamples: List<String> = emptyList(),
    val tone: String? = null,
    val style: String? = null,
)

data class TrainVoiceResponse(
    val id: Long,
    val name: String,
    val status: String,
)

data class GenerateTextRequest(
    val profileId: Long,
    val prompt: String,
    val maxLength: Int = 500,
)

data class GenerateTextResponse(
    val generatedText: String,
    val voiceProfileId: Long,
)

data class AnalyzeTextRequest(
    val text: String,
)

data class AnalyzeTextResponse(
    val tone: String,
    val formality: Double,
    val emotion: String,
    val keywords: List<String>,
)
