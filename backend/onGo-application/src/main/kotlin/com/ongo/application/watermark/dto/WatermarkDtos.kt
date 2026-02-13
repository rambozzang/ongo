package com.ongo.application.watermark.dto

import java.time.LocalDateTime

data class WatermarkResponse(
    val id: Long,
    val name: String,
    val imageUrl: String,
    val position: String,
    val opacity: Double,
    val size: Int,
    val isDefault: Boolean,
    val createdAt: LocalDateTime?,
)

data class WatermarkListResponse(
    val watermarks: List<WatermarkResponse>,
)

data class CreateWatermarkRequest(
    val name: String,
    val imageUrl: String,
    val position: String = "BOTTOM_RIGHT",
    val opacity: Double = 0.8,
    val size: Int = 100,
    val isDefault: Boolean = false,
)

data class UpdateWatermarkRequest(
    val name: String,
    val imageUrl: String,
    val position: String = "BOTTOM_RIGHT",
    val opacity: Double = 0.8,
    val size: Int = 100,
)
