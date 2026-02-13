package com.ongo.domain.brandkit

import java.time.LocalDateTime

data class BrandKit(
    val id: Long? = null,
    val userId: Long,
    val name: String,
    val primaryColor: String = "#7c3aed",
    val secondaryColor: String = "#1e40af",
    val accentColor: String = "#059669",
    val fontFamily: String = "Pretendard",
    val logoUrl: String? = null,
    val introTemplateUrl: String? = null,
    val outroTemplateUrl: String? = null,
    val watermarkUrl: String? = null,
    val guidelines: String? = null,
    val isDefault: Boolean = false,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)
