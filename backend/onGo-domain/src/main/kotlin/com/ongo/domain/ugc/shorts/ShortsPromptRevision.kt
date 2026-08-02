package com.ongo.domain.ugc.shorts

import java.time.Instant

/**
 * 쇼츠 프롬프트 개정 이력.
 *
 * 프롬프트가 변경되기 직전의 내용을 보관하며, 롤백은 과거 개정을 지우지 않고
 * 지정 개정의 내용으로 새 개정을 만드는 방식으로 동작한다.
 */
data class ShortsPromptRevision(
    val id: Long = 0,
    val promptId: Long,
    val revision: Int,
    val systemPrompt: String? = null,
    val userPrompt: String,
    val changeNote: String? = null,
    val changedBy: Long,
    val createdAt: Instant = Instant.now(),
)
