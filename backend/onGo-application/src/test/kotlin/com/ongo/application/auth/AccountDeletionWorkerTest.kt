package com.ongo.application.auth

import com.ongo.common.enums.PlanType
import com.ongo.common.enums.SubscriptionStatus
import com.ongo.domain.accountdeletion.AccountDeletionDataPort
import com.ongo.domain.accountdeletion.AccountDeletionJob
import com.ongo.domain.accountdeletion.AccountDeletionJobRepository
import com.ongo.domain.accountdeletion.UserFkKey
import com.ongo.domain.accountdeletion.UserFkPolicyRegistry
import com.ongo.domain.accountdeletion.UserFkScanner
import com.ongo.domain.subscription.Subscription
import com.ongo.domain.subscription.SubscriptionRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class AccountDeletionWorkerTest {
    private val objectTasks = mockk<com.ongo.domain.accountdeletion.AccountDeletionObjectTaskRepository>(relaxed = true)
    private val fileStoragePort = mockk<com.ongo.application.common.FileStoragePort>(relaxed = true)

    private val jobs = mockk<AccountDeletionJobRepository>(relaxed = true)
    private val scanner = mockk<UserFkScanner>()
    private val deletionData = mockk<AccountDeletionDataPort>(relaxed = true)
    private val subscriptions = mockk<SubscriptionRepository>(relaxed = true)

    private val processor = AccountDeletionJobProcessor(jobs, scanner, deletionData, subscriptions, objectTasks, fileStoragePort)
    private val job = AccountDeletionJob(id = 9L, userId = 1L, idempotencyKey = "worker-test")

    private fun key(constraint: String, table: String) =
        UserFkKey("public", constraint, table, listOf("user_id"), listOf("id"))

    @Test
    @DisplayName("정책 승인된 무료 계정은 사용자 소유 row 삭제 단계로 진행한다")
    fun eligibleFreeUserIsProcessed() {
        val goalKey = key("goals_user_id_fkey", "goals")
        every { scanner.actualUserFks() } returns listOf(goalKey)
        every { scanner.countRowsFor(goalKey, 1L) } returns 0
        every { subscriptions.findByUserId(1L) } returns Subscription(
            userId = 1L,
            planType = PlanType.FREE,
            status = SubscriptionStatus.FREE,
        )

        processor.process(job)

        verify(exactly = 1) {
            deletionData.snapshotObjectsAndDeleteUserData(
                jobId = 9L,
                userId = 1L,
                policies = match { policies ->
                    policies == listOf(UserFkPolicyRegistry.find(goalKey))
                },
            )
        }
        verify(exactly = 0) { jobs.markBlocked(any(), any(), any()) }
    }

    @Test
    @DisplayName("사용자별 REVIEW_BLOCK 데이터가 있으면 삭제하지 않고 job 을 종료한다")
    fun reviewBlockDataIsBlocked() {
        val commentsKey = key("comments_user_id_fkey", "comments")
        every { scanner.actualUserFks() } returns listOf(commentsKey)
        every { scanner.countRowsFor(commentsKey, 1L) } returns 1

        processor.process(job)

        verify(exactly = 1) {
            jobs.markBlocked(
                jobId = 9L,
                errorCode = "ACCOUNT_DELETION_BLOCKED_POLICY_REVIEW",
                supportReference = "review-block:1",
            )
        }
        verify(exactly = 0) { deletionData.snapshotObjectsAndDeleteUserData(any<Long>(), any<Long>(), any()) }
    }

    @Test
    @DisplayName("유료 또는 결제 식별자가 있는 계정은 삭제하지 않는다")
    fun paidSubscriptionIsBlocked() {
        val goalKey = key("goals_user_id_fkey", "goals")
        every { scanner.actualUserFks() } returns listOf(goalKey)
        every { scanner.countRowsFor(goalKey, 1L) } returns 0
        every { subscriptions.findByUserId(1L) } returns Subscription(
            userId = 1L,
            planType = PlanType.PRO,
            status = SubscriptionStatus.ACTIVE,
            paddleCustomerId = "customer-1",
        )

        processor.process(job)

        verify(exactly = 1) {
            jobs.markBlocked(
                jobId = 9L,
                errorCode = "ACCOUNT_DELETION_BLOCKED_SUBSCRIPTION",
                supportReference = "subscription:active-or-billing",
            )
        }
        verify(exactly = 0) { deletionData.snapshotObjectsAndDeleteUserData(any<Long>(), any<Long>(), any()) }
    }
}
