package com.ongo.application.storage

import com.ongo.common.enums.PlanType
import com.ongo.common.exception.StorageQuotaExceededException
import com.ongo.domain.storage.StorageQuotaPort
import com.ongo.domain.subscription.SubscriptionRepository
import org.springframework.stereotype.Service

@Service
class StorageQuotaUseCase(
    private val subscriptionRepository: SubscriptionRepository,
    private val storageQuotaPort: StorageQuotaPort,
) {

    fun getEffectiveLimit(userId: Long): Long {
        val subscription = subscriptionRepository.findByUserId(userId)
        return subscription?.storageQuotaLimitBytes
            ?: (subscription?.planType ?: PlanType.FREE).storageBytes
    }

    fun getCurrentUsage(userId: Long): Long {
        return storageQuotaPort.calculateUserStorageBytes(userId)
    }

    fun checkQuota(userId: Long, additionalBytes: Long) {
        val limit = getEffectiveLimit(userId)
        val used = getCurrentUsage(userId)
        if (used + additionalBytes > limit) {
            throw StorageQuotaExceededException(
                limitBytes = limit,
                usedBytes = used,
                requiredBytes = additionalBytes,
            )
        }
    }
}
