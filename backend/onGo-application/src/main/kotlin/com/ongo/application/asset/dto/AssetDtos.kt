package com.ongo.application.asset.dto

import java.time.LocalDateTime

data class AssetResponse(
    val id: Long,
    val filename: String,
    val originalFilename: String?,
    val fileUrl: String,
    val fileType: String,
    val fileSizeBytes: Long?,
    val mimeType: String?,
    val tags: List<String>,
    val folder: String,
    val width: Int?,
    val height: Int?,
    val durationSeconds: Int?,
    val createdAt: LocalDateTime?,
)

data class AssetListResponse(
    val assets: List<AssetResponse>,
    val totalCount: Int,
)

data class UpdateAssetRequest(
    val tags: List<String>? = null,
    val folder: String? = null,
)
