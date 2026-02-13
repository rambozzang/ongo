package com.ongo.application.watermark

import com.ongo.application.watermark.dto.CreateWatermarkRequest
import com.ongo.application.watermark.dto.UpdateWatermarkRequest
import com.ongo.application.watermark.dto.WatermarkListResponse
import com.ongo.application.watermark.dto.WatermarkResponse
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.watermark.Watermark
import com.ongo.domain.watermark.WatermarkRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class WatermarkUseCase(
    private val watermarkRepository: WatermarkRepository,
) {

    fun listWatermarks(userId: Long): WatermarkListResponse {
        val watermarks = watermarkRepository.findByUserId(userId)
        return WatermarkListResponse(
            watermarks = watermarks.map { it.toResponse() },
        )
    }

    @Transactional
    fun createWatermark(userId: Long, request: CreateWatermarkRequest): WatermarkResponse {
        if (request.isDefault) {
            watermarkRepository.clearDefault(userId)
        }
        val watermark = Watermark(
            userId = userId,
            name = request.name,
            imageUrl = request.imageUrl,
            position = request.position,
            opacity = request.opacity,
            size = request.size,
            isDefault = request.isDefault,
        )
        return watermarkRepository.save(watermark).toResponse()
    }

    @Transactional
    fun updateWatermark(userId: Long, watermarkId: Long, request: UpdateWatermarkRequest): WatermarkResponse {
        val watermark = watermarkRepository.findById(watermarkId) ?: throw NotFoundException("워터마크", watermarkId)
        if (watermark.userId != userId) throw ForbiddenException("해당 워터마크에 대한 권한이 없습니다")

        val updated = watermark.copy(
            name = request.name,
            imageUrl = request.imageUrl,
            position = request.position,
            opacity = request.opacity,
            size = request.size,
        )
        return watermarkRepository.update(updated).toResponse()
    }

    @Transactional
    fun deleteWatermark(userId: Long, watermarkId: Long) {
        val watermark = watermarkRepository.findById(watermarkId) ?: throw NotFoundException("워터마크", watermarkId)
        if (watermark.userId != userId) throw ForbiddenException("해당 워터마크에 대한 권한이 없습니다")
        watermarkRepository.delete(watermarkId)
    }

    @Transactional
    fun setDefault(userId: Long, watermarkId: Long): WatermarkResponse {
        val watermark = watermarkRepository.findById(watermarkId) ?: throw NotFoundException("워터마크", watermarkId)
        if (watermark.userId != userId) throw ForbiddenException("해당 워터마크에 대한 권한이 없습니다")

        watermarkRepository.clearDefault(userId)
        val updated = watermark.copy(isDefault = true)
        return watermarkRepository.update(updated).toResponse()
    }

    private fun Watermark.toResponse(): WatermarkResponse = WatermarkResponse(
        id = id!!,
        name = name,
        imageUrl = imageUrl,
        position = position,
        opacity = opacity,
        size = size,
        isDefault = isDefault,
        createdAt = createdAt,
    )
}
