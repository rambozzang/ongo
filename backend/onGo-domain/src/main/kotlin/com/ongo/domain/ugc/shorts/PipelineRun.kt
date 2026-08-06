package com.ongo.domain.ugc.shorts

import java.time.Instant

/**
 * UGC 쇼츠 파이프라인 실행. 롱폼 영상 하나에 대응한다.
 */
data class PipelineRun(
    val id: Long = 0,
    val workspaceId: Long,
    val userId: Long,
    val sourceVideoId: Long,
    val templateId: Long? = null,
    val status: PipelineRunStatus = PipelineRunStatus.PENDING,
    val currentStage: PipelineStage? = null,
    val transcriptText: String? = null,
    /** REFRAME 산출 크롭 좌표. 게이트 재개 후 TEMPLATE 이 클립에 복사한다. */
    val cropJson: String? = null,
    val clipCount: Int = 0,
    val errorMessage: String? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val version: Long = 0,
)
