package com.ongo.domain.accountdeletion

/**
 * 실제 스키마의 `users` 참조 외래키와, 특정 사용자가 그 외래키로 엮인 행 수를 읽는다.
 *
 * 정책 레지스트리는 "지워도 되는가"를 알지만 "지금 스키마에 무엇이 있는가"는 모른다.
 * 그 간극을 메우는 포트다. 구현은 `pg_constraint` 를 읽으므로 인프라에 둔다.
 */
interface UserFkScanner {

    /** `users` 를 참조하는 외래키 전체. */
    fun actualUserFks(): List<UserFkKey>

    /** 이 외래키로 해당 사용자와 엮인 행 수. */
    fun countRowsFor(key: UserFkKey, userId: Long): Long
}
