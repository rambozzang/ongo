package com.ongo.application.thumbnailgenerator.dto

data class ThumbnailTemplateResponse(
    val id: Long,
    val name: String,
    val style: String,
    val previewUrl: String,
    val popularity: Int,
)

data class GeneratedThumbnailResponse(
    val id: Long,
    val imageUrl: String,
    val style: String,
    val ctrPrediction: Double,
    val prompt: String,
    val createdAt: String,
)

data class ThumbnailGenerateRequest(
    val videoTitle: String,
    val style: String,
    val keywords: List<String>,
    val platform: String,
    val includeText: Boolean,
    val textOverlay: String? = null,
    val colorScheme: String? = null,
)

data class ThumbnailGenerateResponse(
    val thumbnails: List<GeneratedThumbnailResponse>,
    val creditsUsed: Int,
    val creditsRemaining: Int,
)

data class ThumbnailTemplatesWrapper(
    val templates: List<ThumbnailTemplateResponse>,
)

data class ThumbnailHistoryResponse(
    val id: Long,
    val videoTitle: String,
    val thumbnails: List<GeneratedThumbnailResponse>,
    val selectedThumbnailId: Long? = null,
    val createdAt: String,
)

data class ThumbnailHistoryWrapper(
    val history: List<ThumbnailHistoryResponse>,
)
