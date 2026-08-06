package com.ongo.infrastructure.accountdeletion

import com.ongo.common.exception.AccountFrozenException
import com.ongo.domain.accountdeletion.AccountDeletionJobRepository
import com.ongo.domain.accountdeletion.AccountDeletionState
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

/**
 * 게이트 판정 계약을 고정한다. **아직 어디에도 적용하지 않은 상태**의 계약이다.
 *
 * 핵심은 fail-closed 다. 상태를 읽지 못했을 때 통과시키면, 하필 삭제가 진행 중인
 * 계정에 데이터가 들어오는 창이 열린다. 정상 사용자가 일시적 오류로 쓰기를 거부당하는
 * 쪽이 낫다.
 */
class UserWriteGuardImplTest {

    private val repository = mockk<AccountDeletionJobRepository>()
    private val guard = UserWriteGuardImpl(repository)

    @Test
    @DisplayName("ACTIVE 면 통과한다")
    fun activeAllowsWrites() {
        every { repository.findDeletionState(1L) } returns AccountDeletionState.ACTIVE
        assertDoesNotThrow { guard.requireWritable(1L) }
    }

    @Test
    @DisplayName("삭제 요청된 계정은 막는다")
    fun requestedBlocksWrites() {
        every { repository.findDeletionState(1L) } returns AccountDeletionState.DELETION_REQUESTED
        assertThrows<AccountFrozenException> { guard.requireWritable(1L) }
    }

    @Test
    @DisplayName("삭제된 계정도 막는다")
    fun deletedBlocksWrites() {
        every { repository.findDeletionState(1L) } returns AccountDeletionState.DELETED
        assertThrows<AccountFrozenException> { guard.requireWritable(1L) }
    }

    @Test
    @DisplayName("상태 조회가 실패하면 막는다 — fail-closed")
    fun lookupFailureBlocksWrites() {
        every { repository.findDeletionState(1L) } throws IllegalStateException("DB 오류")
        assertThrows<AccountFrozenException> { guard.requireWritable(1L) }
    }

    @Test
    @DisplayName("상태를 찾을 수 없어도 막는다 — 판정 근거가 없으면 통과시키지 않는다")
    fun missingStateBlocksWrites() {
        every { repository.findDeletionState(1L) } returns null
        assertThrows<AccountFrozenException> { guard.requireWritable(1L) }
    }

    @Test
    @DisplayName("ACTIVE 외에는 전부 막힌다 — 새 상태가 생겨도 기본이 차단이다")
    fun onlyActiveAllowsWrites() {
        AccountDeletionState.entries.forEach { state ->
            every { repository.findDeletionState(9L) } returns state
            if (state == AccountDeletionState.ACTIVE) {
                assertDoesNotThrow { guard.requireWritable(9L) }
            } else {
                assertThrows<AccountFrozenException>("$state 가 쓰기를 통과시켰다") {
                    guard.requireWritable(9L)
                }
            }
        }
    }
}
