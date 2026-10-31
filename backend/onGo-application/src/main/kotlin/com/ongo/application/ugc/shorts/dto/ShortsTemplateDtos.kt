package com.ongo.application.ugc.shorts.dto

import java.time.Instant

/**
 * 쇼츠 템플릿 응답.
 * 프론트엔드 `ShortsTemplateResponse` 인터페이스와 필드명이 일치해야 한다.
 */
data class ShortsTemplateResponse(
    val id: Long,
    val name: String,
    val description: String?,
    val aspectRatio: String,
    val width: Int,
    val height: Int,
    val backgroundStyle: String,
    val hookFontFamily: String?,
    val hookFontSize: Int?,
    val hookFontColor: String?,
    val hookStrokeColor: String?,
    val hookPosition: String,
    val captionFontFamily: String?,
    val captionFontSize: Int?,
    val captionFontColor: String?,
    val captionStrokeColor: String?,
    val captionPosition: String,
    val safeAreaTop: Int,
    val safeAreaBottom: Int,
    val referenceImageUrl: String?,
    val isDefault: Boolean,
    /** 자막 스타일 프리셋(CLEAN·POP·BOX). 저장값이 없으면 POP. */
    val captionStyle: String = "POP",
    /** 말하는 단어 강조색(#RRGGBB). 없으면 기본 노랑. */
    val highlightColor: String? = null,
    val createdAt: Instant?,
    val updatedAt: Instant?,
)

/**
 * 템플릿 생성/수정 요청. 응답에서 id, referenceImageUrl, createdAt, updatedAt을 뺀 구성이다.
 */
data class ShortsTemplateRequest(
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
    val isDefault: Boolean = false,
    /** null 이면 기존 값을 유지한다(예전 화면이 보내지 않아도 지워지지 않게). */
    val captionStyle: String? = null,
    val highlightColor: String? = null,
)
