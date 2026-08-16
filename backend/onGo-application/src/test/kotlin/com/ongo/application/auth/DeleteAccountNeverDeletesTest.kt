package com.ongo.application.auth

import com.ongo.common.exception.AccountDeletionBlockedException
import com.ongo.domain.accountdeletion.AccountDeletionJobRepository
import com.ongo.domain.accountdeletion.AccountDeletionJob
import com.ongo.domain.accountdeletion.AccountDeletionStatus
import com.ongo.domain.accountdeletion.AccountDeletionState
import com.ongo.domain.accountdeletion.UserFkKey
import com.ongo.domain.accountdeletion.UserFkScanner
import com.ongo.domain.auth.AuthTokenPort
import com.ongo.domain.auth.OAuth2Port
import com.ongo.domain.auth.RefreshTokenPort
import com.ongo.domain.auth.TokenBlacklistPort
import com.ongo.domain.credit.CreditRepository
import com.ongo.domain.settings.UserSettingsRepository
import com.ongo.domain.subscription.SubscriptionRepository
import com.ongo.domain.subscription.Subscription
import com.ongo.domain.user.User
import com.ongo.domain.user.UserRepository
import com.ongo.common.enums.PlanType
import com.ongo.common.enums.SubscriptionStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * 계정 삭제 경로가 **아무것도 지우지 않는다**는 것을 고정한다.
 *
 * 예전 구현은 refresh token 을 지우고 `userRepository.delete` 를 바로 불렀다.
 * `users` 를 `ON DELETE CASCADE` 로 참조하는 17개가 함께 사라졌고 거기에
 * `payments`, `subscriptions`, `ai_credit_transactions` 가 들어 있었다.
 *
 * 정책으로 승인된 계정은 삭제 job 으로 넘기되, 이 경로에서 직접 삭제하지 않는 계약을 고정한다.
 * 유료·공유·외부 데이터가 있는 계정은 여전히 차단되어야 한다.
 */
class DeleteAccountNeverDeletesTest {

    private val userRepository = mockk<UserRepository>()
    private val refreshTokenPort = mockk<RefreshTokenPort>(relaxed = true)
    private val scanner = mockk<UserFkScanner>()
    private val deletionJobs = mockk<AccountDeletionJobRepository>(relaxed = true)
    private val subscriptions = mockk<SubscriptionRepository>(relaxed = true)

    private fun useCase(withDeletionJobs: Boolean = false): AuthUseCase {
        every { subscriptions.findByUserId(any()) } returns null
        return AuthUseCase(
            userRepository = userRepository,
            creditRepository = mockk<CreditRepository>(relaxed = true),
            userSettingsRepository = mockk<UserSettingsRepository>(relaxed = true),
            subscriptionRepository = subscriptions,
            authTokenPort = mockk<AuthTokenPort>(relaxed = true),
            oAuth2Port = mockk<OAuth2Port>(relaxed = true),
            refreshTokenPort = refreshTokenPort,
            tokenBlacklistPort = mockk<TokenBlacklistPort>(relaxed = true),
            userFkScanner = scanner,
            accountDeletionJobRepository = deletionJobs.takeIf { withDeletionJobs },
        )
    }

    private fun key(constraint: String, table: String, column: String = "user_id") =
        UserFkKey("public", constraint, table, listOf(column), listOf("id"))

    private fun existingUser() {
        every { userRepository.findById(1L) } returns User(
            id = 1L,
            email = "u@t.io",
            name = "u",
            provider = com.ongo.common.enums.AuthProvider.GOOGLE,
            providerId = "p",
        )
    }

    private fun assertNothingDeleted() {
        verify(exactly = 0) { userRepository.delete(any()) }
        verify(exactly = 0) { refreshTokenPort.deleteByUserId(any()) }
    }

    @Test
    @DisplayName("삭제 상태 조회는 게이트와 durable job 상태를 함께 반환한다")
    fun deletionStatusReportsJobState() {
        every { deletionJobs.findDeletionState(1L) } returns AccountDeletionState.DELETION_REQUESTED
        every { deletionJobs.findLatestByUserId(1L) } returns AccountDeletionJob(
            id = 19L,
            userId = 1L,
            status = AccountDeletionStatus.IN_PROGRESS,
            idempotencyKey = "private-test-key",
        )

        val result = useCase(withDeletionJobs = true).getAccountDeletionStatus(1L)

        assertEquals("DELETION_REQUESTED", result.state)
        assertEquals("IN_PROGRESS", result.status)
        assertEquals(19L, result.jobId)
        assertEquals(false, result.retryable)
    }

    @Test
    @DisplayName("판단 미완 데이터를 가진 사용자 — 차단하고 아무것도 지우지 않는다")
    fun blockedUserLosesNothing() {
        existingUser()
        every { scanner.actualUserFks() } returns listOf(key("comments_user_id_fkey", "comments"))
        every { scanner.countRowsFor(any(), 1L) } returns 3

        val thrown = assertThrows<AccountDeletionBlockedException> { useCase().deleteAccount(1L) }

        assertEquals(AccountDeletionBlockedException.CODE_POLICY_REVIEW, thrown.code)
        assertNothingDeleted()
    }

    @Test
    @DisplayName("삭제 가능한 사용자여도 처리 절차가 없으므로 지우지 않는다")
    fun deletableUserStillLosesNothing() {
        existingUser()
        every { scanner.actualUserFks() } returns listOf(key("goals_user_id_fkey", "goals"))
        every { scanner.countRowsFor(any(), 1L) } returns 0

        val thrown = assertThrows<AccountDeletionBlockedException> { useCase().deleteAccount(1L) }

        // 202 REQUESTED 를 돌려주지 않는다. 처리할 job 이 없는데 접수됐다고 하면 거짓말이다.
        assertEquals(AccountDeletionBlockedException.CODE_NOT_READY, thrown.code)
        assertNothingDeleted()
    }

    @Test
    @DisplayName("분류되지 않은 외래키가 있으면 전역 차단한다")
    fun unclassifiedFkBlocksGlobally() {
        existingUser()
        every { scanner.actualUserFks() } returns listOf(key("brand_new_user_id_fkey", "brand_new"))
        every { scanner.countRowsFor(any(), 1L) } returns 0

        val thrown = assertThrows<AccountDeletionBlockedException> { useCase().deleteAccount(1L) }

        assertEquals(AccountDeletionBlockedException.CODE_UNCLASSIFIED, thrown.code)
        assertNothingDeleted()
    }

    @Test
    @DisplayName("사전 점검이 실패하면 fail-closed — 삭제로 넘어가지 않는다")
    fun preflightFailureIsFailClosed() {
        existingUser()
        every { scanner.actualUserFks() } throws IllegalStateException("DB 조회 실패")

        val thrown = assertThrows<AccountDeletionBlockedException> { useCase().deleteAccount(1L) }

        // 판정을 못 했으면 지우지 않는다. 여기서 예외를 삼키고 진행하면 최악이다.
        assertEquals(AccountDeletionBlockedException.CODE_PREFLIGHT_FAILED, thrown.code)
        assertNothingDeleted()
    }

    @Test
    @DisplayName("행 수 조회가 실패해도 fail-closed")
    fun rowCountFailureIsFailClosed() {
        existingUser()
        every { scanner.actualUserFks() } returns listOf(key("comments_user_id_fkey", "comments"))
        every { scanner.countRowsFor(any(), 1L) } throws IllegalStateException("count 실패")

        val thrown = assertThrows<AccountDeletionBlockedException> { useCase().deleteAccount(1L) }

        assertEquals(AccountDeletionBlockedException.CODE_PREFLIGHT_FAILED, thrown.code)
        assertNothingDeleted()
    }

    @Test
    @DisplayName("응답 문구에 테이블·컬럼 이름이 들어가면 안 된다")
    fun userFacingMessageHidesSchemaNames() {
        existingUser()
        every { scanner.actualUserFks() } returns listOf(key("comments_user_id_fkey", "comments"))
        every { scanner.countRowsFor(any(), 1L) } returns 1

        val thrown = assertThrows<AccountDeletionBlockedException> { useCase().deleteAccount(1L) }

        // 스키마 구조 노출이고, 테이블 이름이 바뀌면 클라이언트가 깨진다.
        // 진단 정보는 supportReference 와 로그에만 둔다.
        val message = thrown.message ?: ""
        listOf("comments", "user_id", "fkey").forEach {
            assert(!message.contains(it)) { "사용자 문구에 '$it' 가 들어 있다: $message" }
        }
        assert(thrown.supportReference?.contains("comments_user_id_fkey") == true) {
            "진단용 참조에는 제약 이름이 있어야 한다"
        }
    }

    @Test
    @DisplayName("차단 결과는 사용자 동결 없이 내구성 있는 작업 기록으로 남긴다")
    fun blockedResultIsRecordedWithoutFreezing() {
        existingUser()
        every { scanner.actualUserFks() } returns listOf(key("comments_user_id_fkey", "comments"))
        every { scanner.countRowsFor(any(), 1L) } returns 1

        assertThrows<AccountDeletionBlockedException> { useCase(withDeletionJobs = true).deleteAccount(1L) }

        verify(exactly = 1) {
            deletionJobs.recordBlocked(
                userId = 1L,
                idempotencyKey = match { it.startsWith("account-deletion:1:") },
                errorCode = AccountDeletionBlockedException.CODE_POLICY_REVIEW,
                supportReference = "review-block:1",
            )
        }
        verify(exactly = 0) { deletionJobs.requestDeletion(any(), any()) }
        assertNothingDeleted()
    }

    @Test
    @DisplayName("정책 승인된 무료 계정은 삭제 job 으로 넘기고 이 요청에서 직접 지우지 않는다")
    fun eligibleFreeUserIsQueuedForDeletion() {
        existingUser()
        every { scanner.actualUserFks() } returns listOf(key("goals_user_id_fkey", "goals"))
        every { scanner.countRowsFor(any(), 1L) } returns 0
        every { subscriptions.findByUserId(1L) } returns Subscription(
            userId = 1L,
            planType = PlanType.FREE,
            status = SubscriptionStatus.FREE,
        )
        every { deletionJobs.requestDeletion(1L, any()) } returns AccountDeletionJob(
            id = 42L,
            userId = 1L,
            idempotencyKey = "queued",
        )

        val target = useCase(withDeletionJobs = true)
        every { subscriptions.findByUserId(1L) } returns Subscription(
            userId = 1L,
            planType = PlanType.FREE,
            status = SubscriptionStatus.FREE,
        )
        target.deleteAccount(1L)

        verify(exactly = 1) {
            deletionJobs.requestDeletion(
                userId = 1L,
                idempotencyKey = match { it.startsWith("account-deletion-request:1:") },
            )
        }
        assertNothingDeleted()
    }
}
