package com.ongo.api.config

import com.ongo.common.exception.AccountDeletionBlockedException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * 계정 삭제 차단이 클라이언트에게 어떻게 보이는지 고정한다.
 *
 * 세 가지가 계약이다.
 * 1. HTTP 409 — 요청 자체는 유효하지만 현재 상태에서 처리할 수 없다는 의미다
 * 2. `error` 는 **안정적인 코드**. 클라이언트가 이걸로 분기한다
 * 3. 응답에 테이블·컬럼 이름이 없다. 스키마 노출이고 이름이 바뀌면 클라이언트가 깨진다
 */
class AccountDeletionBlockedResponseTest {

    private val handler = GlobalExceptionHandler()

    @Test
    @DisplayName("409 와 안정적인 error 코드를 돌려준다")
    fun returnsConflictWithStableCode() {
        val response = handler.handleAccountDeletionBlocked(
            AccountDeletionBlockedException(
                code = AccountDeletionBlockedException.CODE_POLICY_REVIEW,
                message = "보관 정책 확인이 필요한 데이터가 있어 계정 삭제를 바로 진행할 수 없습니다.",
                supportReference = "review-block:comments_user_id_fkey",
            )
        )

        assertEquals(409, response.statusCode.value())
        assertEquals(false, response.body?.success)
        assertEquals(AccountDeletionBlockedException.CODE_POLICY_REVIEW, response.body?.error)
    }

    @Test
    @DisplayName("응답 어디에도 스키마 이름이 새지 않는다")
    fun responseNeverLeaksSchemaNames() {
        val response = handler.handleAccountDeletionBlocked(
            AccountDeletionBlockedException(
                code = AccountDeletionBlockedException.CODE_POLICY_REVIEW,
                message = "보관 정책 확인이 필요한 데이터가 있어 계정 삭제를 바로 진행할 수 없습니다.",
                supportReference = "review-block:comments_user_id_fkey,competitors_user_id_fkey",
            )
        )

        // supportReference 는 진단용이라 제약 이름을 담지만, 응답에는 실리지 않아야 한다.
        val serialized = "${response.body?.message} ${response.body?.error}"
        listOf("comments", "competitors", "user_id", "fkey").forEach {
            assertFalse(serialized.contains(it)) { "응답에 '$it' 가 새어나갔다: $serialized" }
        }
    }

    @Test
    @DisplayName("코드마다 서로 다른 값이어야 클라이언트가 분기할 수 있다")
    fun codesAreDistinct() {
        val codes = listOf(
            AccountDeletionBlockedException.CODE_POLICY_REVIEW,
            AccountDeletionBlockedException.CODE_NOT_READY,
            AccountDeletionBlockedException.CODE_UNCLASSIFIED,
            AccountDeletionBlockedException.CODE_PREFLIGHT_FAILED,
        )
        assertEquals(codes.size, codes.toSet().size) { "중복된 error 코드가 있다: $codes" }
    }
}
