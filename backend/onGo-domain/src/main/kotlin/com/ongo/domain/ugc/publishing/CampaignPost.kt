package com.ongo.domain.ugc.publishing

import java.time.LocalDateTime

enum class PostType { DIRECT, EXTERNAL }

enum class PostStatus { PENDING, PUBLISHING, PUBLISHED, FAILED, EXTERNAL }

/**
 * 캠페인 게시물. 승인된 제출물을 플랫폼에 게시(DIRECT)하거나 외부 게시물 URL을 등록(EXTERNAL)한 결과.
 * `idempotencyKey`로 재시도 시 중복 게시를 방지한다.
 */
data class CampaignPost(
    val id: Long? = null,
    val campaignId: Long,
    val submissionId: Long,
    val creatorId: Long,
    val platform: String,
    val postType: PostType,
    val videoUploadId: Long? = null,
    val externalPostUrl: String? = null,
    val platformPostId: String? = null,
    val status: PostStatus,
    val idempotencyKey: String,
    val errorMessage: String? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)
