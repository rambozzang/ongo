package com.ongo.domain.accountdeletion

/**
 * 삭제를 **한 건이라도 실행하기 전에** 끝내야 하는 정책 점검.
 *
 * 일부 지운 뒤 미분류를 발견하면 되돌릴 수 없다. 그래서 이 점검이 job 의 첫 단계다.
 *
 * 차단 범위를 두 층으로 나눈다. 하나로 합치면 안 된다.
 *
 * - **전역**: 분류되지 않은 외래키가 존재하면 모든 사용자의 삭제를 멈춘다.
 *   새 기능이 정책 검토를 우회하지 못하게 하는 장치다.
 * - **사용자별**: 분류된 `REVIEW_BLOCK` 외래키는 **그 사용자에게 실제로 행이 있을 때만** 막는다.
 *   이렇게 하지 않으면 `DELETE` 대상만 가진 사용자까지 영구히 탈퇴 불가가 된다.
 */
object AccountDeletionPreflight {

    sealed interface Result {
        /** 진행 가능. [deletable] 을 순서대로 지우면 된다(순서는 FK 그래프가 정한다). */
        data class Proceed(val deletable: List<UserFkPolicy>) : Result

        /** 분류되지 않은 외래키가 있다. 사람이 분류할 때까지 모든 삭제가 멈춘다. */
        data class BlockedGlobally(val unclassified: List<UserFkKey>) : Result

        /** 이 사용자가 판단 미완 데이터를 가지고 있다. */
        data class BlockedForUser(val blocking: List<UserFkPolicy>) : Result
    }

    /**
     * @param actualFks 실제 스키마에서 읽은 `users` 참조 외래키 전체
     * @param userRowCounter 해당 외래키로 이 사용자와 엮인 행이 있는지 세는 함수.
     *   실제 행 존재를 DB 에 물어야 하므로 주입받는다.
     */
    fun evaluate(
        actualFks: List<UserFkKey>,
        userRowCounter: (UserFkKey) -> Long,
    ): Result {
        // 1층 — 레지스트리 완전성. 전역 판정이다.
        val unclassified = actualFks.filter { UserFkPolicyRegistry.find(it) == null }
        if (unclassified.isNotEmpty()) {
            return Result.BlockedGlobally(unclassified.sortedBy { it.constraintName })
        }

        val policies = actualFks.mapNotNull { UserFkPolicyRegistry.find(it) }

        // 2층 — 이 사용자가 판단 미완 데이터를 실제로 가졌는가. 사용자별 판정이다.
        // 행이 없으면 막지 않는다. 정책이 미정이어도 데이터가 없으면 지울 게 없다.
        val blocking = policies
            .filter { it.policy != FkPolicy.DELETE }
            .filter { userRowCounter(it.key) > 0 }
        if (blocking.isNotEmpty()) {
            return Result.BlockedForUser(blocking.sortedBy { it.key.constraintName })
        }

        return Result.Proceed(policies.filter { it.policy == FkPolicy.DELETE })
    }
}
