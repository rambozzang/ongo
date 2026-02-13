package com.ongo.application.brandkit.dto

import java.time.LocalDateTime

data class BrandKitResponse(
    val id: Long,
    val name: String,
    val primaryColor: String,
    val secondaryColor: String,
    val accentColor: String,
    val fontFamily: String,
    val logoUrl: String?,
    val introTemplateUrl: String?,
    val outroTemplateUrl: String?,
    val watermarkUrl: String?,
    val guidelines: String?,
    val isDefault: Boolean,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
)

data class CreateBrandKitRequest(
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
)

data class UpdateBrandKitRequest(
    val name: String? = null,
    val primaryColor: String? = null,
    val secondaryColor: String? = null,
    val accentColor: String? = null,
    val fontFamily: String? = null,
    val logoUrl: String? = null,
    val introTemplateUrl: String? = null,
    val outroTemplateUrl: String? = null,
    val watermarkUrl: String? = null,
    val guidelines: String? = null,
)
