package com.ongo.domain.accountdeletion

import java.time.LocalDateTime

/**
 * 계정 삭제 작업의 durable 기록.
 *
 * **삭제가 끝난 뒤에도 남는다.** 그래서 `users` 를 외래키로 참조하지 않는다.
 * 외래키를 걸면 CASCADE 는 삭제 기록 자체를 지우고, NO ACTION 은 사용자 삭제를 영원히 막는다.
 */
data class AccountDeletionJob(
    val id: Long? = null,
    val userId: Long,
    val status: AccountDeletionStatus = AccountDeletionStatus.REQUESTED,
    /** 같은 요청이 두 번 들어와도 job 이 하나만 생기게 한다. */
    val idempotencyKey: String,
    /** 진단·지원용 내부 식별자. **개인정보 본문을 담지 않는다.** */
    val supportReference: String? = null,
    val attemptCount: Int = 0,
    val lastErrorCode: String? = null,
    val requestedAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
    /** DB 삭제 트랜잭션이 커밋된 시각. 값이 있으면 DB 단계를 다시 하지 않는다. */
    val dbCommittedAt: LocalDateTime? = null,
    val completedAt: LocalDateTime? = null,
)

/**
 * 계정의 쓰기 동결 게이트.
 *
 * `account_deletion_jobs` 가 durable 단계를 갖는 것과 별개로, **인증 직후 한 번의 조회로
 * 쓰기를 막을 수 있어야** 한다. 그래서 `users` 에 단일 권위 상태를 둔다.
 * jobs 테이블만 두면 모든 쓰기 경로가 매번 조인해야 하고 누락되는 경로가 생긴다.
 */
enum class AccountDeletionState {
    /** 정상. 쓰기 허용. */
    ACTIVE,

    /** 삭제 요청됨. 쓰기를 막는다. 예외는 삭제 요청·토큰 갱신·로그아웃·상태 조회뿐이다. */
    DELETION_REQUESTED,

    /** 삭제 완료. */
    DELETED,
    ;

    /** 이 상태에서 사용자 데이터 쓰기가 허용되는가. */
    fun allowsWrites(): Boolean = this == ACTIVE
}
