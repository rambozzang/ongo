package com.ongo.domain.accountdeletion

import java.time.LocalDateTime

/**
 * 탈퇴 시 지워야 할 외부 스토리지 객체 한 건.
 *
 * DB 삭제 트랜잭션 안에서 기록되고 **함께 커밋된다.** 원장이 커밋됐다는 사실이 곧
 * "DB 삭제가 확정됐다"는 증거이므로, 그 뒤에야 실제 객체를 지운다. 순서를 뒤집어
 * 먼저 지우면 트랜잭션이 롤백됐을 때 살아있는 계정의 파일을 잃는다 — 되돌릴 수 없다.
 */
data class AccountDeletionObjectTask(
    val id: Long? = null,
    val jobId: Long,
    /** 버킷 상대 키. 외부 URL 은 애초에 여기 들어오지 않는다. */
    val objectKey: String,
    val status: ObjectCleanupStatus = ObjectCleanupStatus.PENDING,
    val attemptCount: Int = 0,
    val lastErrorCode: String? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
    val completedAt: LocalDateTime? = null,
)

enum class ObjectCleanupStatus {
    /** 아직 안 지웠다. 실패해도 여기로 남아 다음 tick 이 다시 집는다. */
    PENDING,

    /** 스토리지에서 실제로 지워졌다. */
    DONE,

    /**
     * 사람이 봐야 한다. 자동으로는 진행하지 않는다.
     *
     * 이 상태가 하나라도 남으면 job 을 COMPLETED 로 올리지 않는다. "지웠다고 표시했는데
     * 실제로는 남아 있는" 거짓 완료가 개인정보 관점에서 가장 나쁜 결과다.
     */
    BLOCKED,
}
