package com.ongo.domain.ugc.shorts

import java.time.Instant

/**
 * 쇼츠 템플릿. 폰트/색/배경/세이프에어리어와 레퍼런스 이미지를 담는다.
 */
data class ShortsTemplate(
    val id: Long = 0,
    val workspaceId: Long,
    val name: String,
    val description: String? = null,
    val aspectRatio: String = "9:16",
    val width: Int = 1080,
    val height: Int = 1920,
    val backgroundStyle: String = "BLACK_BARS",
    val hookFontFamily: String? = null,
    val hookFontSize: Int? = null,
    val hookFontColor: String? = null,
    val hookStrokeColor: String? = null,
    val hookPosition: String = "TOP",
    val captionFontFamily: String? = null,
    val captionFontSize: Int? = null,
    val captionFontColor: String? = null,
    val captionStrokeColor: String? = null,
    val captionPosition: String = "BOTTOM",
    val safeAreaTop: Int = 0,
    val safeAreaBottom: Int = 0,
    val referenceImageUrl: String? = null,
    val extraSpec: String? = null,   // JSONB 는 JSON 문자열로 다룬다
    val isDefault: Boolean = false,
    val createdBy: Long,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val version: Long = 0,
)
