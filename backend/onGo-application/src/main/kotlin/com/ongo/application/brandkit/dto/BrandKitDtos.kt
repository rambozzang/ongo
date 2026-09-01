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
    val colors: List<BrandKitColor> = emptyList(),
    val fonts: List<BrandKitFont> = emptyList(),
    val assets: List<BrandKitAsset> = emptyList(),
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
    val colors: List<BrandKitColor> = emptyList(),
    val fonts: List<BrandKitFont> = emptyList(),
    val assets: List<BrandKitAsset> = emptyList(),
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
    val colors: List<BrandKitColor>? = null,
    val fonts: List<BrandKitFont>? = null,
    val assets: List<BrandKitAsset>? = null,
)

data class BrandKitColor(
    val id: Long,
    val name: String,
    val hex: String,
    val usage: String,
)

data class BrandKitFont(
    val id: Long,
    val name: String,
    val family: String,
    val weight: String,
    val usage: String,
    val sampleText: String,
)

/**
 * 브랜드킷이 참조하는 파일 하나.
 *
 * ## `url` 이 아니라 `assetId` 가 근거다
 *
 * 예전에는 업로드 응답의 `fileUrl` 문자열만 복사해 저장했다. 운영(S3/R2)에서 그 값은
 * **7 일짜리 서명 URL** 이라 8 일째부터 로고·워터마크가 통째로 깨졌다. 게다가 원본 에셋과
 * 아무 관계가 없어, 에셋을 지워도 이쪽은 알지 못하고 브랜드킷 항목을 지워도 에셋은
 * 쿼터를 계속 먹었다.
 *
 * `assetId` 가 있으면 서버가 조회할 때마다 **소유권을 확인하고 저장 키로 URL 을 새로
 * 발급**한다. 만료가 사라지고 두 레코드의 관계도 생긴다.
 *
 * @param assetId `null` 이면 이 필드가 생기기 전에 저장된 항목이다. 그 행의 [url] 은
 *        **이미 만료됐을 수 있다.** 문자열에서 키를 되짚어 되살리지 않는다 — 경로·서명
 *        형식이 어댑터마다 달라 추측이 빗나가고, 빗나간 키는 남의 파일을 가리킨다.
 *        사용자가 파일을 다시 올리면 `assetId` 가 붙는다.
 */
data class BrandKitAsset(
    val id: Long,
    val name: String,
    val type: String,
    val url: String,
    val format: String,
    val size: String,
    val uploadedAt: String,
    val assetId: Long? = null,
)
