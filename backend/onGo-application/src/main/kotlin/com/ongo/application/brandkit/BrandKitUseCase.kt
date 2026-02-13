package com.ongo.application.brandkit

import com.ongo.application.brandkit.dto.*
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.brandkit.BrandKit
import com.ongo.domain.brandkit.BrandKitRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class BrandKitUseCase(
    private val brandKitRepository: BrandKitRepository,
) {

    fun listBrandKits(userId: Long): List<BrandKitResponse> {
        return brandKitRepository.findByUserId(userId).map { it.toResponse() }
    }

    @Transactional
    fun createBrandKit(userId: Long, request: CreateBrandKitRequest): BrandKitResponse {
        val brandKit = BrandKit(
            userId = userId,
            name = request.name,
            primaryColor = request.primaryColor,
            secondaryColor = request.secondaryColor,
            accentColor = request.accentColor,
            fontFamily = request.fontFamily,
            logoUrl = request.logoUrl,
            introTemplateUrl = request.introTemplateUrl,
            outroTemplateUrl = request.outroTemplateUrl,
            watermarkUrl = request.watermarkUrl,
            guidelines = request.guidelines,
        )
        return brandKitRepository.save(brandKit).toResponse()
    }

    @Transactional
    fun updateBrandKit(userId: Long, id: Long, request: UpdateBrandKitRequest): BrandKitResponse {
        val brandKit = brandKitRepository.findById(id) ?: throw NotFoundException("브랜드 키트", id)
        if (brandKit.userId != userId) throw ForbiddenException("해당 브랜드 키트에 대한 권한이 없습니다")

        val updated = brandKit.copy(
            name = request.name ?: brandKit.name,
            primaryColor = request.primaryColor ?: brandKit.primaryColor,
            secondaryColor = request.secondaryColor ?: brandKit.secondaryColor,
            accentColor = request.accentColor ?: brandKit.accentColor,
            fontFamily = request.fontFamily ?: brandKit.fontFamily,
            logoUrl = request.logoUrl ?: brandKit.logoUrl,
            introTemplateUrl = request.introTemplateUrl ?: brandKit.introTemplateUrl,
            outroTemplateUrl = request.outroTemplateUrl ?: brandKit.outroTemplateUrl,
            watermarkUrl = request.watermarkUrl ?: brandKit.watermarkUrl,
            guidelines = request.guidelines ?: brandKit.guidelines,
        )
        return brandKitRepository.update(updated).toResponse()
    }

    @Transactional
    fun deleteBrandKit(userId: Long, id: Long) {
        val brandKit = brandKitRepository.findById(id) ?: throw NotFoundException("브랜드 키트", id)
        if (brandKit.userId != userId) throw ForbiddenException("해당 브랜드 키트에 대한 권한이 없습니다")
        brandKitRepository.delete(id)
    }

    @Transactional
    fun setDefault(userId: Long, id: Long): BrandKitResponse {
        val brandKit = brandKitRepository.findById(id) ?: throw NotFoundException("브랜드 키트", id)
        if (brandKit.userId != userId) throw ForbiddenException("해당 브랜드 키트에 대한 권한이 없습니다")

        brandKitRepository.clearDefault(userId)
        val updated = brandKit.copy(isDefault = true)
        return brandKitRepository.update(updated).toResponse()
    }

    private fun BrandKit.toResponse(): BrandKitResponse = BrandKitResponse(
        id = id!!,
        name = name,
        primaryColor = primaryColor,
        secondaryColor = secondaryColor,
        accentColor = accentColor,
        fontFamily = fontFamily,
        logoUrl = logoUrl,
        introTemplateUrl = introTemplateUrl,
        outroTemplateUrl = outroTemplateUrl,
        watermarkUrl = watermarkUrl,
        guidelines = guidelines,
        isDefault = isDefault,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
