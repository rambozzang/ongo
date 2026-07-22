package com.ongo.domain.ugc.campaign

import java.time.LocalDateTime

/**
 * 캠페인 플레이북. MVP에서는 캠페인당 활성 플레이북 1개.
 * `contentType`: UGC_VIDEO, SLIDESHOW, TESTIMONIAL, HOOK_DEMO 등.
 */
data class Playbook(
    val id: Long? = null,
    val campaignId: Long,
    val title: String,
    val summary: String? = null,
    val contentType: String = "UGC_VIDEO",
    val revision: Int = 1,
    val steps: List<PlaybookStep> = emptyList(),
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
) {
    init {
        require(title.isNotBlank()) { "플레이북 제목은 비어 있을 수 없습니다" }
    }
}

data class PlaybookStep(
    val id: Long? = null,
    val playbookId: Long? = null,
    val sortOrder: Int,
    val stepType: String = "INSTRUCTION",
    val title: String,
    val instruction: String? = null,
    val exampleUrl: String? = null,
    val required: Boolean = true,
) {
    init {
        require(title.isNotBlank()) { "플레이북 단계 제목은 비어 있을 수 없습니다" }
    }
}
