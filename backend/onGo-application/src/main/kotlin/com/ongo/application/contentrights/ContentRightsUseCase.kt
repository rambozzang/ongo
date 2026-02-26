package com.ongo.application.contentrights

import com.ongo.application.contentrights.dto.*
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.contentrights.ContentRight
import com.ongo.domain.contentrights.ContentRightRepository
import com.ongo.domain.contentrights.RightsAlert
import com.ongo.domain.contentrights.RightsAlertRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ContentRightsUseCase(
    private val contentRightRepository: ContentRightRepository,
    private val rightsAlertRepository: RightsAlertRepository,
) {

    fun getSummary(userId: Long): RightsSummaryResponse {
        val rights = contentRightRepository.findByUserId(userId)
        return RightsSummaryResponse(
            totalAssets = rights.size,
            activeCount = rights.count { it.licenseStatus == "ACTIVE" },
            expiringCount = rights.count { it.licenseStatus == "EXPIRING_SOON" },
            expiredCount = rights.count { it.licenseStatus == "EXPIRED" },
            totalCost = rights.sumOf { it.cost },
            byType = "[]",
            byLicense = "[]",
        )
    }

    fun getAll(userId: Long): List<ContentRightResponse> {
        return contentRightRepository.findByUserId(userId).map { it.toResponse() }
    }

    @Transactional
    fun create(userId: Long, request: CreateRightRequest): ContentRightResponse {
        val right = ContentRight(
            userId = userId,
            videoId = request.videoId,
            videoTitle = request.videoTitle,
            assetName = request.assetName,
            assetType = request.assetType,
            licenseType = request.licenseType,
            source = request.source,
            licenseUrl = request.licenseUrl,
            expiresAt = request.expiresAt,
            cost = request.cost,
            currency = request.currency,
            notes = request.notes,
        )
        return contentRightRepository.save(right).toResponse()
    }

    @Transactional
    fun update(userId: Long, rightId: Long, request: UpdateRightRequest): ContentRightResponse {
        val right = contentRightRepository.findById(rightId)
            ?: throw NotFoundException("저작권 정보", rightId)
        if (right.userId != userId) throw ForbiddenException("해당 저작권 정보에 대한 권한이 없습니다")
        val updated = right.copy(
            assetName = request.assetName ?: right.assetName,
            licenseType = request.licenseType ?: right.licenseType,
            licenseStatus = request.licenseStatus ?: right.licenseStatus,
            expiresAt = request.expiresAt ?: right.expiresAt,
            notes = request.notes ?: right.notes,
        )
        return contentRightRepository.update(updated).toResponse()
    }

    @Transactional
    fun delete(userId: Long, rightId: Long) {
        val right = contentRightRepository.findById(rightId)
            ?: throw NotFoundException("저작권 정보", rightId)
        if (right.userId != userId) throw ForbiddenException("해당 저작권 정보에 대한 권한이 없습니다")
        contentRightRepository.delete(rightId)
    }

    fun getAlerts(userId: Long): List<RightsAlertResponse> {
        return rightsAlertRepository.findByUserId(userId).map { it.toAlertResponse() }
    }

    @Transactional
    fun markAlertRead(userId: Long, alertId: Long) {
        rightsAlertRepository.markRead(alertId)
    }

    private fun ContentRight.toResponse() = ContentRightResponse(
        id = id!!, videoId = videoId, videoTitle = videoTitle, assetName = assetName,
        assetType = assetType, licenseType = licenseType, licenseStatus = licenseStatus,
        source = source, licenseUrl = licenseUrl, expiresAt = expiresAt, purchasedAt = purchasedAt,
        cost = cost, currency = currency, notes = notes, createdAt = createdAt, updatedAt = updatedAt,
    )

    private fun RightsAlert.toAlertResponse() = RightsAlertResponse(
        id = id!!, contentRightId = contentRightId, assetName = assetName, assetType = assetType,
        message = message, severity = severity, daysUntilExpiry = daysUntilExpiry, isRead = isRead,
        createdAt = createdAt,
    )
}
