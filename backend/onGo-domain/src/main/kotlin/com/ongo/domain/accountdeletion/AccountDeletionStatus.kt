package com.ongo.domain.accountdeletion

/**
 * 계정 삭제 작업의 durable 상태.
 *
 * 상태를 영속화하는 이유는 **중단돼도 재개할 수 있어야** 하기 때문이다. DB 삭제까지 마쳤는데
 * 외부 정리 중 프로세스가 죽으면, 기록이 없으면 어디까지 됐는지 알 수 없다.
 *
 * ```
 * REQUESTED → IN_PROGRESS → DB_COMMITTED → EXTERNAL_CLEANUP_PENDING → COMPLETED
 *                  ↘ BLOCKED_POLICY       ↘ FAILED
 * ```
 *
 * [DB_COMMITTED] 기록은 **사용자 데이터 삭제와 같은 트랜잭션에서 커밋**해야 한다.
 * 별도 트랜잭션으로 미루면 "삭제는 커밋됐는데 상태 기록 전에 죽은" 구간이 생기고,
 * 재개 시 DB 단계를 다시 돌게 된다.
 */
enum class AccountDeletionStatus {
    /** 요청 접수. 이 시점에 계정 쓰기를 동결한다. */
    REQUESTED,

    /** 사전 점검·리소스 수집 진행 중. 아직 아무것도 지우지 않았다. */
    IN_PROGRESS,

    /** DB 삭제 트랜잭션이 커밋됐다. 이 이후로 DB 단계를 다시 하지 않는다. */
    DB_COMMITTED,

    /** 외부 리소스 정리가 남았다. 재시도 대상이다. */
    EXTERNAL_CLEANUP_PENDING,

    COMPLETED,

    /**
     * 정책 때문에 진행할 수 없다. **아무것도 지우지 않은 상태**여야 한다.
     *
     * 두 경우가 있다.
     * - 전역: 분류되지 않은 외래키가 존재한다. 사람이 분류할 때까지 모든 삭제가 멈춘다
     * - 사용자별: 이 사용자가 `REVIEW_BLOCK` 외래키로 엮인 행을 가지고 있다
     */
    BLOCKED_POLICY,

    FAILED,
}
