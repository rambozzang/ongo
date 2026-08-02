package com.ongo.domain.ugc.shorts

import java.time.Instant

/**
 * 클립별 후킹 문구 후보. A/B는 AI가 만들고 CUSTOM은 사용자가 직접 입력한다.
 */
data class ClipHook(
    val id: Long = 0,
    val clipId: Long,
    val variant: HookVariant,
    val text: String,
    val selected: Boolean = false,
    val createdAt: Instant = Instant.now(),
)
