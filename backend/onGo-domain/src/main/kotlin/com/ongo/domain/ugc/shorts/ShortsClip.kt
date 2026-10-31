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
    /** 맥락 컷 AI 점수(0~100). AI 가 주지 않았으면 null. */
    val score: Int? = null,
    val scoreReason: String? = null,
    /** 플랫폼별 제목·설명·해시태그 JSON. 만들기 전이면 null. */
    val platformMetaJson: String? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)
