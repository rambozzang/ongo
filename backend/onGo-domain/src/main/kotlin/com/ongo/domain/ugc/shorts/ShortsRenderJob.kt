package com.ongo.domain.ugc.shorts

import java.time.Instant

/** 클립 단위 서버 렌더 작업의 영속 상태. */
enum class ShortsRenderJobStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
}

/**
 * 렌더 job의 상태와 재시도 메타데이터.
 *
 * job은 파이프라인 실행과 별도로 보존되어 서버 재시작 뒤에도 상태를 조회할 수 있다.
 * 사용자 FK를 두지 않는 이유는 실행 감사 이력이 사용자 삭제를 영구적으로 막거나
 * CASCADE로 함께 사라지지 않게 하기 위해서다. run/clip FK가 소유권과 생명주기를 보장한다.
 */
data class ShortsRenderJob(
    val id: String,
    val runId: Long,
    val clipId: Long,
    val status: ShortsRenderJobStatus = ShortsRenderJobStatus.QUEUED,
    val progress: Int? = null,
    val videoId: Long? = null,
    val failureReason: String? = null,
    val attemptCount: Int = 0,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val startedAt: Instant? = null,
    val completedAt: Instant? = null,
)

interface ShortsRenderJobRepository {
    fun findById(id: String): ShortsRenderJob?
    fun findByRunAndClip(runId: Long, clipId: Long): ShortsRenderJob?
    /** 동시 요청에서도 (runId, clipId)당 하나만 생성하고 기존 job을 반환한다. */
    fun saveIfAbsent(job: ShortsRenderJob): ShortsRenderJob
    /** QUEUED 상태를 원자적으로 RUNNING으로 선점한다. 이미 선점됐으면 null을 반환한다. */
    fun claimQueued(id: String, startedAt: Instant): ShortsRenderJob?
    fun update(job: ShortsRenderJob): ShortsRenderJob
}
