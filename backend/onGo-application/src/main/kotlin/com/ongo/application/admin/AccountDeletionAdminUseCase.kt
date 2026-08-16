package com.ongo.application.admin

import com.ongo.application.admin.dto.AccountDeletionAdminJobResponse
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.accountdeletion.AccountDeletionJob
import com.ongo.domain.accountdeletion.AccountDeletionJobRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 계정 삭제 durable job을 운영자가 확인하고 재처리하는 경계. */
@Service
class AccountDeletionAdminUseCase(
    private val repository: AccountDeletionJobRepository,
) {

    @Transactional(readOnly = true)
    fun listJobs(limit: Int): List<AccountDeletionAdminJobResponse> =
        repository.findRecent(limit).map(::toResponse)

    @Transactional
    fun retry(jobId: Long): AccountDeletionAdminJobResponse {
        val job = repository.retry(jobId)
            ?: throw NotFoundException("재처리 가능한 계정 삭제 작업", jobId)
        return toResponse(job)
    }

    private fun toResponse(job: AccountDeletionJob) = AccountDeletionAdminJobResponse(
        jobId = job.id!!,
        userId = job.userId,
        status = job.status.name,
        attemptCount = job.attemptCount,
        requestedAt = job.requestedAt,
        updatedAt = job.updatedAt,
        completedAt = job.completedAt,
        lastErrorCode = job.lastErrorCode,
        supportReference = job.supportReference,
    )
}
