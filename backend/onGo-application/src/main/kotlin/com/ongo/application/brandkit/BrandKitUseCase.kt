package com.ongo.application.brandkit

import com.ongo.application.brandkit.dto.*
import com.ongo.application.common.FileStoragePort
import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.asset.Asset
import com.ongo.domain.asset.AssetRepository
import com.ongo.domain.brandkit.BrandKit
import com.ongo.domain.brandkit.BrandKitRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper

/**
 * 브랜드킷에 연결할 수 없는 에셋. **사용자에게 그대로 보여줄 수 있는 문장**만 담는다.
 *
 * 저장 위치를 모르는 과거 에셋은 URL 을 새로 발급할 수 없다. 그 상태로 연결해 두면
 * 지금은 되는 것처럼 보이다가 7 일 뒤 조용히 깨진다 — 붙일 때 막는 편이 낫다.
 */
class BrandKitAssetNotLinkableException(message: String) :
    BusinessException("BRAND_KIT_ASSET_NOT_LINKABLE", message)

@Service
class BrandKitUseCase(
    private val brandKitRepository: BrandKitRepository,
    private val objectMapper: ObjectMapper,
    private val assetRepository: AssetRepository,
    private val fileStoragePort: FileStoragePort,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun listBrandKits(userId: Long): List<BrandKitResponse> {
        return brandKitRepository.findByUserId(userId).map { it.toResponse() }
    }

    @Transactional
    fun createBrandKit(userId: Long, request: CreateBrandKitRequest): BrandKitResponse {
        validateAssetReferences(userId, request.assets)
        val assets = withFreshUrls(userId, request.assets)
        val brandKit = BrandKit(
            userId = userId,
            name = request.name,
            primaryColor = request.primaryColor,
            secondaryColor = request.secondaryColor,
            accentColor = request.accentColor,
            fontFamily = request.fontFamily,
            logoUrl = scalarUrl(assets, TYPE_LOGO, request.logoUrl),
            introTemplateUrl = scalarUrl(assets, TYPE_INTRO, request.introTemplateUrl),
            outroTemplateUrl = scalarUrl(assets, TYPE_OUTRO, request.outroTemplateUrl),
            watermarkUrl = scalarUrl(assets, TYPE_WATERMARK, request.watermarkUrl),
            guidelines = request.guidelines,
            colorsJson = objectMapper.writeValueAsString(request.colors),
            fontsJson = objectMapper.writeValueAsString(request.fonts),
            assetsJson = objectMapper.writeValueAsString(assets),
        )
        return brandKitRepository.save(brandKit).toResponse(assets)
    }

    @Transactional
    fun updateBrandKit(userId: Long, id: Long, request: UpdateBrandKitRequest): BrandKitResponse {
        val brandKit = brandKitRepository.findById(id) ?: throw NotFoundException("브랜드 키트", id)
        if (brandKit.userId != userId) throw ForbiddenException("해당 브랜드 키트에 대한 권한이 없습니다")

        request.assets?.let { validateAssetReferences(userId, it) }
        val assets = request.assets?.let { withFreshUrls(userId, it) }
        val updated = brandKit.copy(
            name = request.name ?: brandKit.name,
            primaryColor = request.primaryColor ?: brandKit.primaryColor,
            secondaryColor = request.secondaryColor ?: brandKit.secondaryColor,
            accentColor = request.accentColor ?: brandKit.accentColor,
            fontFamily = request.fontFamily ?: brandKit.fontFamily,
            logoUrl = scalarUrl(assets, TYPE_LOGO, request.logoUrl ?: brandKit.logoUrl),
            introTemplateUrl = scalarUrl(assets, TYPE_INTRO, request.introTemplateUrl ?: brandKit.introTemplateUrl),
            outroTemplateUrl = scalarUrl(assets, TYPE_OUTRO, request.outroTemplateUrl ?: brandKit.outroTemplateUrl),
            watermarkUrl = scalarUrl(assets, TYPE_WATERMARK, request.watermarkUrl ?: brandKit.watermarkUrl),
            guidelines = request.guidelines ?: brandKit.guidelines,
            colorsJson = request.colors?.let(objectMapper::writeValueAsString) ?: brandKit.colorsJson,
            fontsJson = request.fonts?.let(objectMapper::writeValueAsString) ?: brandKit.fontsJson,
            assetsJson = assets?.let(objectMapper::writeValueAsString) ?: brandKit.assetsJson,
        )
        return brandKitRepository.update(updated).toResponse(assets)
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

    /**
     * 응답을 만든다. **`assetId` 가 있는 항목은 조회할 때마다 URL 을 새로 발급한다.**
     *
     * 저장된 문자열을 그대로 돌려주면 7 일 뒤 로고가 깨진다. 에셋 목록이 같은 이유로 이미
     * 재발급하고 있고([com.ongo.application.asset.AssetUseCase]), 브랜드킷만 빠져 있었다.
     */
    /**
     * @param resolved 쓰기 경로가 **이미 발급해 둔** 목록. 넘기면 다시 발급하지 않는다 —
     *        저장 직전과 응답 직후에 두 번 서명하면 항목 수만큼 헛일이 된다.
     */
    private fun BrandKit.toResponse(resolved: List<BrandKitAsset>? = null): BrandKitResponse {
        val assets = resolved ?: withFreshUrls(userId, parseList<BrandKitAsset>(assetsJson))
        return BrandKitResponse(
            id = id!!,
            name = name,
            primaryColor = primaryColor,
            secondaryColor = secondaryColor,
            accentColor = accentColor,
            fontFamily = fontFamily,
            logoUrl = scalarUrl(assets, TYPE_LOGO, logoUrl),
            introTemplateUrl = scalarUrl(assets, TYPE_INTRO, introTemplateUrl),
            outroTemplateUrl = scalarUrl(assets, TYPE_OUTRO, outroTemplateUrl),
            watermarkUrl = scalarUrl(assets, TYPE_WATERMARK, watermarkUrl),
            guidelines = guidelines,
            colors = parseList(colorsJson),
            fonts = parseList(fontsJson),
            assets = assets,
            isDefault = isDefault,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    /**
     * 쓰기 경로의 **엄격한 검증**. 잘못된 참조는 저장되기 전에 막는다.
     *
     * 여기서 통과시키면 지금은 되는 것처럼 보이다가 조용히 깨지거나, 더 나쁘게는 남의
     * 파일을 가리키는 브랜드킷이 저장된다. 사용자가 방금 고른 참조라 이 자리에서 알려
     * 주는 것이 맞다.
     */
    private fun validateAssetReferences(userId: Long, assets: List<BrandKitAsset>) {
        assets.forEach { asset ->
            val assetId = asset.assetId ?: return@forEach
            val stored = assetRepository.findById(assetId)
                ?: throw NotFoundException("에셋", assetId)
            if (stored.userId != userId) {
                throw ForbiddenException("해당 에셋에 대한 권한이 없습니다")
            }
            if (stored.storageObjectKey.isNullOrBlank()) {
                throw BrandKitAssetNotLinkableException(
                    "저장 위치를 확인할 수 없는 예전 에셋입니다. 파일을 다시 올린 뒤 사용해 주세요.",
                )
            }
        }
    }

    /**
     * `assetId` 가 가리키는 에셋의 **지금 유효한** URL 로 갈아 끼운다.
     *
     * ## 읽기에서는 던지지 않는다
     *
     * 참조하던 에셋이 나중에 지워졌다고 브랜드킷 조회가 500 이 되면 사용자는 자기 브랜드킷을
     * 영영 열지 못한다 — 색상·폰트·가이드라인까지 함께 막힌다. 그래서 확인하지 못한 항목은
     * **저장된 문자열을 그대로** 두고 로그만 남긴다.
     *
     * 다만 **확인하지 못한 참조로 URL 을 발급하지는 않는다.** 소유자가 아니거나 없는
     * 에셋의 키로 서명하면 남의 파일에 접근할 수 있는 링크를 우리가 만들어 주는 셈이다.
     */
    private fun withFreshUrls(userId: Long, assets: List<BrandKitAsset>): List<BrandKitAsset> =
        assets.map { asset ->
            val key = verifiedKey(userId, asset) ?: return@map asset
            val fresh = runCatching { fileStoragePort.downloadUrlByKey(key) }
                .onFailure {
                    log.warn(
                        "브랜드킷 에셋 URL 재발급 실패 — 저장된 URL 로 응답한다(만료됐을 수 있음). assetId={} key={}",
                        asset.assetId, key, it,
                    )
                }
                .getOrNull()
            if (fresh != null) asset.copy(url = fresh) else asset
        }

    /** 소유권까지 확인한 저장 키. 하나라도 어긋나면 `null` 이고, 그러면 발급하지 않는다. */
    private fun verifiedKey(userId: Long, asset: BrandKitAsset): String? {
        val assetId = asset.assetId ?: return null
        val stored: Asset? = runCatching { assetRepository.findById(assetId) }.getOrNull()
        if (stored == null) {
            log.warn("브랜드킷이 없는 에셋을 참조한다 — 저장된 URL 로 응답한다. assetId={}", assetId)
            return null
        }
        if (stored.userId != userId) {
            log.warn(
                "브랜드킷이 다른 사용자의 에셋을 참조한다 — URL 을 발급하지 않는다. assetId={} owner={} requester={}",
                assetId, stored.userId, userId,
            )
            return null
        }
        return stored.storageObjectKey?.takeIf { it.isNotBlank() }
    }

    /**
     * 타입별 대표 URL. 목록의 **같은 타입 항목이 있으면 그 신선한 URL**을 쓰고, 없으면
     * 넘어온 값을 그대로 둔다.
     *
     * 스칼라 컬럼과 `assets` 배열이 같은 파일을 가리키는데 한쪽만 갱신되면, 화면 위치에 따라
     * 로고가 보였다 안 보였다 한다.
     */
    private fun scalarUrl(assets: List<BrandKitAsset>?, type: String, fallback: String?): String? =
        assets?.firstOrNull { it.type.equals(type, ignoreCase = true) && it.assetId != null }?.url
            ?: fallback

    private companion object {
        const val TYPE_LOGO = "logo"
        const val TYPE_WATERMARK = "watermark"
        const val TYPE_INTRO = "intro"
        const val TYPE_OUTRO = "outro"
    }

    private inline fun <reified T> parseList(json: String?): List<T> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            objectMapper.readValue(json, object : TypeReference<List<T>>() {})
        }.getOrDefault(emptyList())
    }
}
