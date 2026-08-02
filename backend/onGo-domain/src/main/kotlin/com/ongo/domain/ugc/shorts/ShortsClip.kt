package com.ongo.domain.ugc.shorts

import java.time.Instant

/**
 * 파이프라인 실행으로 만들어진 쇼츠 클립 하나.
 */
data class ShortsClip(
    val id: Long = 0,
    val runId: Long,
    val seq: Int,
    val startMs: Long,
    val endMs: Long,
    val title: String? = null,
    val caption: String? = null,
    val subtitleJson: String? = null,
    val cropJson: String? = null,
    val renderSpec: String? = null,
    val status: ClipStatus = ClipStatus.DRAFT,
    val dedupKey: String? = null,
    val renderedVideoId: Long? = null,
    val scheduledAt: Instant? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)
