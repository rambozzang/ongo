package com.ongo.application.asset

import com.ongo.application.asset.dto.AssetListResponse
import com.ongo.application.asset.dto.AssetResponse
import com.ongo.application.asset.dto.UpdateAssetRequest
import com.ongo.application.common.FileStoragePort
import com.ongo.application.storage.StorageQuotaUseCase
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.asset.Asset
import com.ongo.domain.asset.AssetRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.InputStream

@Service
class AssetUseCase(
    private val assetRepository: AssetRepository,
    private val storageQuotaUseCase: StorageQuotaUseCase,
    private val fileStoragePort: FileStoragePort,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun listAssets(userId: Long, fileType: String?, folder: String?, page: Int, size: Int): AssetListResponse {
        val assets = when {
            fileType != null -> assetRepository.findByUserIdAndFileType(userId, fileType, page, size)
            folder != null -> assetRepository.findByUserIdAndFolder(userId, folder, page, size)
            else -> assetRepository.findByUserId(userId, page, size)
        }
        val totalCount = assetRepository.countByUserId(userId)
        return AssetListResponse(
            assets = assets.map { it.toResponse() },
            totalCount = totalCount,
        )
    }

    @Transactional
    fun createAsset(userId: Long, filename: String, originalFilename: String?,
                    fileType: String, fileSizeBytes: Long?, mimeType: String?,
                    tags: List<String>, folder: String, width: Int?, height: Int?,
                    durationSeconds: Int?, inputStream: InputStream,
                    storageKey: String): AssetResponse {
        if (fileSizeBytes != null && fileSizeBytes > 0) {
            storageQuotaUseCase.checkQuota(userId, fileSizeBytes)
        }

        require(fileSizeBytes != null && fileSizeBytes > 0) { "업로드 파일 크기를 확인할 수 없습니다" }
        val uploadedUrl = fileStoragePort.uploadByKey(
            key = storageKey,
            inputStream = inputStream,
            contentType = mimeType ?: "application/octet-stream",
            size = fileSizeBytes,
        )

        try {
            val asset = Asset(
                userId = userId,
                filename = filename,
                originalFilename = originalFilename,
                fileUrl = uploadedUrl,
                // 탈퇴 정리가 추측 없이 지울 수 있도록 실제 업로드 키를 기록한다.
                storageObjectKey = storageKey,
                fileType = fileType,
                fileSizeBytes = fileSizeBytes,
                mimeType = mimeType,
                tags = tags,
                folder = folder,
                width = width,
                height = height,
                durationSeconds = durationSeconds,
            )
            return assetRepository.save(asset).toResponse()
        } catch (e: Exception) {
            runCatching { fileStoragePort.deleteByKey(storageKey) }
                .onFailure { cleanupError -> log.warn("에셋 업로드 보상 삭제 실패: key={}, error={}", storageKey, cleanupError.message) }
            throw e
        }
    }

    @Transactional
    fun updateAsset(userId: Long, assetId: Long, request: UpdateAssetRequest): AssetResponse {
        val asset = assetRepository.findById(assetId) ?: throw NotFoundException("에셋", assetId)
        if (asset.userId != userId) throw ForbiddenException("해당 에셋에 대한 권한이 없습니다")

        val updated = asset.copy(
            tags = request.tags ?: asset.tags,
            folder = request.folder ?: asset.folder,
        )
        return assetRepository.update(updated).toResponse()
    }

    @Transactional
    fun deleteAsset(userId: Long, assetId: Long) {
        val asset = assetRepository.findById(assetId) ?: throw NotFoundException("에셋", assetId)
        if (asset.userId != userId) throw ForbiddenException("해당 에셋에 대한 권한이 없습니다")

        // 스토리지에서 파일 삭제
        try {
            fileStoragePort.deleteByKey("assets/$userId/${asset.filename}")
        } catch (e: Exception) {
            log.warn("에셋 파일 삭제 실패 (계속 진행): assetId={}, error={}", assetId, e.message)
        }

        assetRepository.delete(assetId)
    }

    private fun Asset.toResponse(): AssetResponse = AssetResponse(
        id = id!!,
        filename = filename,
        originalFilename = originalFilename,
        fileUrl = fileUrl,
        fileType = fileType,
        fileSizeBytes = fileSizeBytes,
        mimeType = mimeType,
        tags = tags,
        folder = folder,
        width = width,
        height = height,
        durationSeconds = durationSeconds,
        createdAt = createdAt,
    )
}
