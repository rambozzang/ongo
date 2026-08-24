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

    /**
     * 한도를 검사한다. **호출한 트랜잭션 안에서 예약/기록까지 마쳐야 한다.**
     *
     * 사용자 행을 먼저 잠근다. 잠금이 없으면 동시에 들어온 두 요청이 같은 사용량을 읽고 둘 다
     * 통과한 뒤 각각 예약을 저장해, 합계가 한도를 넘긴다(TOCTOU). 애플리케이션 락은 인스턴스가
     * 늘면 무너지므로 DB 행 잠금으로 직렬화한다. 잠금은 호출한 트랜잭션과 함께 풀리므로,
     * 검사 후 예약 저장까지 같은 트랜잭션에 있어야 의미가 있다.
     *
     * @param excludeVideoId 이미 예약으로 잡혀 있는 자기 자신의 영상. 확정 단계에서 실제 크기로
     *        다시 볼 때 예약분과 실제분을 겹쳐 세지 않도록 제외한다.
     */
    fun checkQuota(userId: Long, additionalBytes: Long, excludeVideoId: Long? = null) {
        storageQuotaPort.lockUserForQuota(userId)
        val limit = getEffectiveLimit(userId)
        val used = storageQuotaPort.calculateUserStorageBytes(userId, excludeVideoId)
        if (used + additionalBytes > limit) {
            throw StorageQuotaExceededException(
                limitBytes = limit,
                usedBytes = used,
                requiredBytes = additionalBytes,
            )
        }
    }
}
