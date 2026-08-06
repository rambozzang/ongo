package com.ongo.domain.accountdeletion

/**
 * `users` 참조 외래키 하나에 대한 계정 삭제 정책.
 *
 * 키를 테이블+컬럼이 아니라 **제약 identity** 로 잡는다. 같은 컬럼에 외래키가 여러 개
 * 걸리거나 복합 외래키가 생기면 테이블+컬럼만으로는 누락되거나 충돌하기 때문이다.
 * `oid` 는 DB 를 다시 만들 때마다 바뀌므로 저장 키로 쓰지 않는다.
 */
data class UserFkKey(
    val schema: String,
    val constraintName: String,
    val table: String,
    /** 순서가 의미를 가진다. 복합 외래키에서 컬럼 순서가 다르면 다른 제약이다. */
    val localColumns: List<String>,
    val referencedColumns: List<String>,
)

/**
 * 이 외래키로 엮인 데이터를 어떻게 할 것인가.
 *
 * 정책이 정해지지 않은 것을 [PRESERVE_ANONYMIZE] 로 두지 않는다. 그건 결정된 척하는 것이다.
 * 미정이면 [REVIEW_BLOCK] 이고, 그러면 그 데이터를 가진 사용자의 삭제가 막힌다.
 */
enum class FkPolicy {
    /** 사용자 단독 소유이고 DB 안에서 완결된다. */
    DELETE,

    /** 보존하되 개인 식별자를 끊는다. **보존 정책이 이미 결정된 것만** 여기 온다. */
    PRESERVE_ANONYMIZE,

    /** 판단 미완. 이 외래키로 엮인 행을 가진 사용자는 삭제하지 않는다. */
    REVIEW_BLOCK,
}

/**
 * 행 단위 연산.
 *
 * [FkPolicy] 가 제약 단위여도 **한 행이 여러 사용자를 참조하면 행 전체의 운명을 따로
 * 정해야 한다.** `approvals` 가 그렇다 — `user_id`, `requester_id`, `reviewer_id` 가
 * 서로 다른 사용자를 가리킬 수 있어서, 한 외래키가 삭제 대상이라고 행을 지우면 다른
 * 사용자의 승인 데이터가 사라진다.
 */
enum class RowOperation {
    /** 행 전체 삭제. 그 행이 탈퇴자 단독 소유일 때만. */
    ROW_DELETE,

    /** 해당 컬럼만 끊고 행은 남긴다. 다른 사용자가 걸려 있을 때. */
    ROW_DETACH,

    /** 판단 미완. 사용자별 차단. */
    ROW_BLOCK,
}

data class UserFkPolicy(
    val key: UserFkKey,
    val policy: FkPolicy,
    val rowOperation: RowOperation,
    /** 왜 이 정책인지. 정책을 올릴 때 근거 없이 올리지 못하게 강제한다. */
    val rationale: String,
)
