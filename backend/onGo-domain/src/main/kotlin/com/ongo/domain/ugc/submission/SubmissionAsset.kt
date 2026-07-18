package com.ongo.domain.ugc.submission

/**
 * 제출 첨부물. 기존 asset/video를 resourceType+resourceId로 참조하거나,
 * 외부 게시물 URL(externalUrl)을 등록한다. 외부 URL은 허용된 스킴만 신뢰한다.
 */
data class SubmissionAsset(
    val id: Long? = null,
    val submissionId: Long? = null,
    val assetType: String,
    val resourceType: String? = null,
    val resourceId: Long? = null,
    val externalUrl: String? = null,
    val sortOrder: Int = 0,
)
