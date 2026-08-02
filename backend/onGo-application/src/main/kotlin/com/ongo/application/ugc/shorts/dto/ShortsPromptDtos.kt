package com.ongo.application.ugc.shorts.dto

import java.time.Instant

/**
 * 쇼츠 프롬프트 응답.
 * 프론트엔드 `ShortsPromptResponse` 인터페이스와 필드명이 일치해야 한다.
 */
data class ShortsPromptResponse(
    val id: Long,
    val stage: String,
    val name: String,
    val description: String?,
    val systemPrompt: String?,
    val userPrompt: String,
    val executable: Boolean,
    val revision: Int,
    /** true = 워크스페이스 오버라이드, false = 시스템 기본값 */
    val customized: Boolean,
    /** 복원 미리보기용 시스템 기본값 */
    val defaultSystemPrompt: String?,
    val defaultUserPrompt: String,
    val updatedAt: Instant?,
)

/**
 * 쇼츠 프롬프트 개정 이력 응답.
 * 프론트엔드 `ShortsPromptRevisionResponse` 인터페이스와 필드명이 일치해야 한다.
 */
data class ShortsPromptRevisionResponse(
    val revision: Int,
    val systemPrompt: String?,
    val userPrompt: String,
    val changeNote: String?,
    val changedBy: Long,
    val createdAt: Instant,
)

/**
 * 프롬프트 편집 요청. 워크스페이스 오버라이드를 생성/갱신한다.
 */
data class UpdateShortsPromptRequest(
    val systemPrompt: String? = null,
    val userPrompt: String,
    val changeNote: String? = null,
)
