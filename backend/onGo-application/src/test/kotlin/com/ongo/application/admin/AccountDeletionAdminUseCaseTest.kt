package com.ongo.application.admin

import com.ongo.domain.accountdeletion.AccountDeletionJob
import com.ongo.domain.accountdeletion.AccountDeletionJobRepository
import com.ongo.domain.accountdeletion.AccountDeletionStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AccountDeletionAdminUseCaseTest {

    private val repository = mockk<AccountDeletionJobRepository>()
    private val useCase = AccountDeletionAdminUseCase(repository)

    @Test
    fun `lists recent deletion jobs without exposing idempotency keys`() {
        every { repository.findRecent(25) } returns listOf(
            AccountDeletionJob(
                id = 9L,
                userId = 4L,
                status = AccountDeletionStatus.FAILED,
                idempotencyKey = "must-not-be-returned",
                lastErrorCode = "DB_RETRY",
            ),
        )

        val result = useCase.listJobs(25)

        assertEquals(1, result.size)
        assertEquals(9L, result.single().jobId)
        assertEquals("FAILED", result.single().status)
        verify(exactly = 1) { repository.findRecent(25) }
    }

    @Test
    fun `retries a failed job through the repository`() {
        every { repository.retry(9L) } returns AccountDeletionJob(
            id = 9L,
            userId = 4L,
            status = AccountDeletionStatus.REQUESTED,
            idempotencyKey = "private",
        )

        val result = useCase.retry(9L)

        assertEquals("REQUESTED", result.status)
        verify(exactly = 1) { repository.retry(9L) }
    }
}
