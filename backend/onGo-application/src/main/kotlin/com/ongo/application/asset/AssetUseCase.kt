package com.ongo.application.asset

import com.ongo.application.asset.dto.AssetListResponse
import com.ongo.application.asset.dto.AssetResponse
import com.ongo.application.asset.dto.UpdateAssetRequest
import com.ongo.application.common.FileStoragePort
import com.ongo.application.storage.StorageQuotaUseCase
import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.asset.Asset
import com.ongo.domain.asset.AssetQuery
import com.ongo.domain.asset.AssetRepository
import com.ongo.domain.brandkit.BrandKitRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.InputStream

/**
 * 다른 곳이 쓰고 있어 지울 수 없는 에셋.
 *
 * 지우고 나면 되돌릴 수 없다. 브랜드킷이 그 파일을 가리키고 있으면 로고나 워터마크가
 * **아무 예고 없이** 깨지고, 화면에는 이유가 표시되지 않는다. 그래서 지우기 전에 막고
 * 어디를 고쳐야 하는지 알려 준다.
 */
class AssetInUseException(message: String) :
    BusinessException("ASSET_IN_USE", message)

@Service
class AssetUseCase(
    private val assetRepository: AssetRepository,
    private val storageQuotaUseCase: StorageQuotaUseCase,
    private val fileStoragePort: FileStoragePort,
    private val brandKitRepository: BrandKitRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 한 페이지와 **그 조건의 총 개수**.
     *
     * 예전에는 조건으로 걸러 놓고 총계는 `countByUserId(userId)` — 전체 개수 — 를 돌려줬다.
     * 화면이 총계를 쓰지 않아 드러나지 않았을 뿐, 페이지네이션을 붙이는 순간 "영상 3개"를
     * 보면서 "총 240개"라고 말하게 된다. 목록과 총계는 같은 [AssetQuery] 를 쓴다.
     *
     * 조건 없이 부르면 조건 없는 총계다 — 예전 동작과 같다.
     */
    fun listAssets(userId: Long, query: AssetQuery, page: Int, size: Int): AssetListResponse {
        val assets = assetRepository.findByUserId(userId, query, page, size)
        return AssetListResponse(
            assets = assets.map { it.toResponse(overrideFileUrl = currentFileUrl(it)) },
            totalCount = assetRepository.count(userId, query),
        )
    }

    /**
     * 조회 시점에 **유효한** 다운로드 URL.
     *
     * `assets.file_url`에는 업로드 당시 받은 값이 그대로 들어 있다. 운영(S3/R2)에서 그 값은
     * 7일짜리 서명 URL이라 8일째부터 목록의 썸네일·미리보기·다운로드가 전부 403이 된다.
     * 파일은 멀쩡한데 사용자에게는 라이브러리가 통째로 깨진 것으로 보인다.
     *
     * 영상은 이미 같은 이유로 조회마다 재서명하고 있다([com.ongo.application.video.VideoQueryUseCase]).
     * 에셋만 빠져 있었다.
     *
     * ## 두 가지 정책
     *
     * **키가 없으면 시도하지 않는다.** V96 이전 행은 `storage_object_key`가 비어 있다.
     * URL에서 키를 되짚어 발급하고 싶겠지만 하지 않는다 — 경로·서명 형식이 어댑터마다 달라
     * 추측이 빗나가고, 빗나간 키는 남의 오브젝트를 가리킨다. 추측을 새 경로의 근거로
     * 넓히지 않는다.
     *
     * **발급이 실패하면 저장된 값을 그대로 준다.** 저장된 URL은 우리가 실제로 발급했던
     * 진짜 값이고, 7일 안이라면 지금도 동작한다 — 지어낸 값이 아니다. 여기서 예외를 올리면
     * 오브젝트 하나 때문에 목록 전체가 500이 되고, 빈 문자열을 주면 아직 살아 있는 링크까지
     * 죽인다. 다만 **조용히 넘어가지 않는다** — 만료된 URL이 나갈 수 있다는 사실이
     * 로그에 남아야 원인을 찾을 수 있다.
     */
    private fun currentFileUrl(asset: Asset): String {
        val key = asset.storageObjectKey?.takeIf { it.isNotBlank() } ?: return asset.fileUrl
        return runCatching { fileStoragePort.downloadUrlByKey(key) }
            .onFailure {
                log.warn(
                    "에셋 다운로드 URL 재발급 실패 — 저장된 URL로 응답한다(만료됐을 수 있음). assetId={} key={}",
                    asset.id, key, it,
                )
            }
            .getOrDefault(asset.fileUrl)
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

        /*
         * **쓰고 있는 곳이 있으면 지우지 않는다.**
         *
         * 스토리지·DB 를 건드리기 **전에** 본다. 지운 뒤에 알려 주는 것은 의미가 없다 —
         * 오브젝트는 이미 사라졌고 브랜드킷은 깨진 링크를 들고 남는다.
         *
         * `assetId` 로만 찾는다. `assetId` 가 없는 예전 브랜드킷 항목은 이 에셋과 이어져
         * 있다는 증거가 없으므로 막지 않는다 — URL 문자열로 맞춰 보는 추측은 하지 않는다.
         * 그 항목의 URL 은 원래도 만료된 값이라 이 삭제로 새로 나빠지지 않는다.
         */
        val usedBy = brandKitRepository.findNamesReferencingAsset(userId, assetId)
        if (usedBy.isNotEmpty()) {
            throw AssetInUseException(
                "브랜드 키트에서 사용 중이라 삭제할 수 없습니다: ${usedBy.joinToString(", ")}. " +
                    "해당 브랜드 키트에서 먼저 교체하거나 제거해 주세요.",
            )
        }

        /*
         * 스토리지에서 파일 삭제. **저장된 키가 있으면 그것만 쓴다.**
         *
         * 예전에는 `assets/{userId}/{filename}`을 매번 조립했다. 지금 포맷과는 우연히
         * 일치하지만, 키 생성 규칙이 한 번이라도 바뀌면 조용히 다른 곳을 지운다 —
         * 지우지 못한 파일은 아무도 못 찾는 고아가 되고, 잘못 맞은 경우는 되돌릴 수 없다.
         * 그래서 업로드가 남겨 둔 실제 키를 근거로 삼는다.
         *
         * `storage_object_key`가 없는 과거 행만 예전 조립 방식으로 떨어진다. 그 경로는
         * 유지하되 **여기서 끝이다** — 새로 만드는 경로(재발급 등)의 근거로는 쓰지 않는다.
         */
        val objectKey = asset.storageObjectKey?.takeIf { it.isNotBlank() }
            ?: "assets/$userId/${asset.filename}"
        try {
            fileStoragePort.deleteByKey(objectKey)
        } catch (e: Exception) {
            log.warn("에셋 파일 삭제 실패 (계속 진행): assetId={}, key={}, error={}", assetId, objectKey, e.message)
        }

        assetRepository.delete(assetId)
    }

    /**
     * @param overrideFileUrl 조회 시점에 다시 발급한 URL. 넘기지 않으면 저장된 값을 쓴다
     *        (업로드 직후처럼 방금 발급받은 값이 이미 최신인 경우).
     */
    private fun Asset.toResponse(overrideFileUrl: String? = null): AssetResponse = AssetResponse(
        id = id!!,
        filename = filename,
        originalFilename = originalFilename,
        fileUrl = overrideFileUrl ?: fileUrl,
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
