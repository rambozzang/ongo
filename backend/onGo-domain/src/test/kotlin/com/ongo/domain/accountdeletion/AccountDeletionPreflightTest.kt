package com.ongo.domain.accountdeletion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * 차단 범위가 **전역**과 **사용자별**로 갈리는 의미를 고정한다.
 *
 * 이 구분이 무너지면 둘 중 하나가 된다.
 * - 전부 전역으로 막으면: `DELETE` 대상만 가진 사용자까지 영구히 탈퇴 불가가 된다
 * - 전부 사용자별로 막으면: 분류되지 않은 새 외래키가 정책 검토를 우회한다(fail-open)
 *
 * 실제 스키마 대조는 `AccountDeletionPolicyGuardIT` 가 한다. 여기서는 판정 규칙만 본다.
 */
class AccountDeletionPreflightTest {

    private fun key(constraint: String, table: String, column: String = "user_id") =
        UserFkKey(
            schema = "public",
            constraintName = constraint,
            table = table,
            localColumns = listOf(column),
            referencedColumns = listOf("id"),
        )

    /** 레지스트리에 실제로 있는 `DELETE` 항목. */
    private val deletableKey = key("goals_user_id_fkey", "goals")

    /** 레지스트리에 실제로 있는 `REVIEW_BLOCK` 항목. */
    private val blockingKey = key("comments_user_id_fkey", "comments")

    @Test
    @DisplayName("DELETE 대상만 가진 사용자는 진행한다 — 다른 FK 의 REVIEW_BLOCK 이 막지 않는다")
    fun userWithOnlyDeletableDataProceeds() {
        // 스키마에는 REVIEW_BLOCK 외래키가 있지만 이 사용자는 그 데이터를 가지고 있지 않다.
        val result = AccountDeletionPreflight.evaluate(
            actualFks = listOf(deletableKey, blockingKey),
            userRowCounter = { k -> if (k == deletableKey) 3 else 0 },
        )

        assertTrue(result is AccountDeletionPreflight.Result.Proceed) {
            "REVIEW_BLOCK 외래키가 스키마에 존재한다는 이유만으로 막으면 안 된다. 실제 결과: $result"
        }
        assertEquals(
            listOf("goals_user_id_fkey"),
            (result as AccountDeletionPreflight.Result.Proceed).deletable.map { it.key.constraintName },
        )
    }

    @Test
    @DisplayName("REVIEW_BLOCK 데이터를 실제로 가진 사용자만 사용자별로 막는다")
    fun userWithReviewBlockDataIsBlocked() {
        val result = AccountDeletionPreflight.evaluate(
            actualFks = listOf(deletableKey, blockingKey),
            userRowCounter = { 1 },
        )

        assertTrue(result is AccountDeletionPreflight.Result.BlockedForUser) { "실제 결과: $result" }
        assertEquals(
            listOf("comments_user_id_fkey"),
            (result as AccountDeletionPreflight.Result.BlockedForUser).blocking.map { it.key.constraintName },
        )
    }

    @Test
    @DisplayName("분류되지 않은 외래키가 있으면 전역으로 막는다 — 행이 없어도 막는다")
    fun unclassifiedFkBlocksGlobally() {
        val unknown = key("brand_new_feature_user_id_fkey", "brand_new_feature")

        // 이 사용자는 아무 행도 없지만 그래도 막아야 한다.
        // 새 기능이 정책 검토를 우회하고 삭제되는 것을 막는 장치이기 때문이다.
        val result = AccountDeletionPreflight.evaluate(
            actualFks = listOf(deletableKey, unknown),
            userRowCounter = { 0 },
        )

        assertTrue(result is AccountDeletionPreflight.Result.BlockedGlobally) { "실제 결과: $result" }
        assertEquals(
            listOf("brand_new_feature_user_id_fkey"),
            (result as AccountDeletionPreflight.Result.BlockedGlobally).unclassified.map { it.constraintName },
        )
    }

    @Test
    @DisplayName("전역 차단이 사용자별 차단보다 먼저다")
    fun globalBlockTakesPrecedence() {
        val unknown = key("brand_new_feature_user_id_fkey", "brand_new_feature")

        // 사용자가 REVIEW_BLOCK 데이터도 가지고 있지만, 미분류가 있으면 그게 먼저다.
        // 미분류는 사람이 분류해야 풀리고, 사용자별 차단은 데이터를 정리하면 풀린다.
        // 둘을 섞어 보고하면 무엇을 해야 풀리는지 알 수 없다.
        val result = AccountDeletionPreflight.evaluate(
            actualFks = listOf(deletableKey, blockingKey, unknown),
            userRowCounter = { 5 },
        )

        assertTrue(result is AccountDeletionPreflight.Result.BlockedGlobally) { "실제 결과: $result" }
    }

    @Test
    @DisplayName("행이 없으면 진행하되 지울 대상도 없다")
    fun userWithNoDataProceedsWithNothingToDelete() {
        val result = AccountDeletionPreflight.evaluate(
            actualFks = listOf(blockingKey),
            userRowCounter = { 0 },
        )

        assertTrue(result is AccountDeletionPreflight.Result.Proceed) { "실제 결과: $result" }
        assertTrue((result as AccountDeletionPreflight.Result.Proceed).deletable.isEmpty())
    }

    @Test
    @DisplayName("관계 참조는 DELETE 로 분류돼 있지 않다 — approvals 로 확인")
    fun relationshipFksAreNotDeletable() {
        // approvals 는 user_id(CASCADE), requester_id, reviewer_id 세 개가 users 를 참조한다.
        // 하나라도 DELETE 로 올라가면 다른 사용자의 승인 데이터가 사라진다.
        val approvalFks = listOf(
            key("approvals_user_id_fkey", "approvals", "user_id"),
            key("approvals_requester_id_fkey", "approvals", "requester_id"),
            key("approvals_reviewer_id_fkey", "approvals", "reviewer_id"),
        )

        approvalFks.forEach { k ->
            val policy = UserFkPolicyRegistry.find(k)
            assertTrue(policy != null) { "${k.constraintName} 이 레지스트리에 없다" }
            assertEquals(FkPolicy.REVIEW_BLOCK, policy!!.policy) {
                "${k.constraintName} 이 DELETE 로 올라가 있다. 남의 승인 데이터가 사라진다"
            }
            assertEquals(RowOperation.ROW_BLOCK, policy.rowOperation)
        }
    }
}
