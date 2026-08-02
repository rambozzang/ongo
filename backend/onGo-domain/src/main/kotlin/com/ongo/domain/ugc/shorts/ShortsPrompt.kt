package com.ongo.domain.ugc.shorts

import java.time.Instant

/**
 * 단계별 쇼츠 프롬프트.
 *
 * [workspaceId]가 null이면 시스템 기본값, 값이 있으면 해당 워크스페이스의 오버라이드다.
 */
data class ShortsPrompt(
    val id: Long = 0,
    val workspaceId: Long?,          // null = 시스템 기본값
    val stage: PipelineStage,
    val name: String,
    val description: String? = null,
    val systemPrompt: String? = null,
    val userPrompt: String,
    val executable: Boolean = true,
    val revision: Int = 1,
    val createdBy: Long? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val version: Long = 0,
)
