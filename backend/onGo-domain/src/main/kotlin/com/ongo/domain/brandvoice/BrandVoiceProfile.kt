package com.ongo.domain.brandvoice

import java.time.LocalDateTime

data class BrandVoiceProfile(
    val id: Long? = null,
    val userId: Long,
    val name: String,
    val tone: String,
    val trainStatus: String = "PENDING",
    val sampleCount: Int = 0,
    val vocabulary: String = "[]", // JSONB as String
    val avoidWords: String = "[]", // JSONB as String
    val emojiUsage: String = "NONE",
    val avgSentenceLength: Int = 0,
    val signaturePhrase: String? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)
