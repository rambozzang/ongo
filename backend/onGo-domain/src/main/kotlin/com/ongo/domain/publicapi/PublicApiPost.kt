package com.ongo.domain.publicapi

import java.time.LocalDateTime

/** Postiz 호환 공개 API가 내부 게시 실행과 연결되는 영속 작업. */
data class PublicApiPost(
    val id: Long = 0,
    val userId: Long,
    /** Postiz customer/group scope. Null is retained for legacy rows. */
    val workspaceId: Long? = null,
    val videoId: Long,
    val type: PublicApiPostType,
    val status: PublicApiPostStatus,
    val scheduledAt: LocalDateTime? = null,
    val errorMessage: String? = null,
    /** 재예약/상태 변경 때 동일한 플랫폼별 설정을 복원하기 위한 요청 스냅샷. */
    val payloadJson: String,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)

enum class PublicApiPostType {
    NOW,
    SCHEDULE,
    DRAFT,
}

enum class PublicApiPostStatus {
    DRAFT,
    PROCESSING,
    SCHEDULED,
    PUBLISHED,
    PARTIALLY_PUBLISHED,
    UNCONFIRMED,
    FAILED,
    CANCELLED,
}

interface PublicApiPostRepository {
    fun save(post: PublicApiPost): PublicApiPost
    fun update(post: PublicApiPost): PublicApiPost
    fun findById(id: Long): PublicApiPost?
    fun findByIdAndUserId(id: Long, userId: Long): PublicApiPost?
    fun findByUserId(userId: Long, limit: Int): List<PublicApiPost>

    fun findByUserIdAndWorkspaceId(userId: Long, workspaceId: Long, limit: Int): List<PublicApiPost> =
        findByUserId(userId, limit).filter { it.workspaceId == workspaceId }

    /** 외부 자동화 클라이언트의 calendar 범위 조회. */
    fun findByUserIdAndDateRange(
        userId: Long,
        start: LocalDateTime,
        end: LocalDateTime,
        limit: Int,
    ): List<PublicApiPost> = findByUserId(userId, limit)

    fun findByUserIdAndWorkspaceIdAndDateRange(
        userId: Long,
        workspaceId: Long,
        start: LocalDateTime,
        end: LocalDateTime,
        limit: Int,
    ): List<PublicApiPost> = findByUserIdAndDateRange(userId, start, end, limit)

    fun deleteDraft(id: Long, userId: Long): Boolean
}
