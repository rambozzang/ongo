package com.ongo.application.storage

import com.ongo.common.enums.PlanType
import com.ongo.common.enums.SubscriptionStatus
import com.ongo.common.exception.StorageQuotaExceededException
import com.ongo.domain.storage.StorageQuotaPort
import com.ongo.domain.subscription.Subscription
import com.ongo.domain.subscription.SubscriptionRepository
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 저장 한도 판정.
 *
 * 핵심은 "판정과 예약 사이가 직렬화되는가"다. 잠금이 없으면 동시에 들어온 두 요청이 같은
 * 사용량을 읽고 둘 다 통과해, 합계가 한도를 넘긴 채로 각각 예약을 저장한다.
 */
class StorageQuotaUseCaseTest {

    private val subscriptionRepository = mockk<SubscriptionRepository>()
    private val storageQuotaPort = mockk<StorageQuotaPort>(relaxed = true)

    private lateinit var useCase: StorageQuotaUseCase

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        useCase = StorageQuotaUseCase(subscriptionRepository, storageQuotaPort)
        every { subscriptionRepository.findByUserId(100L) } returns Subscription(
            id = 1L,
            userId = 100L,
            planType = PlanType.FREE,
            status = SubscriptionStatus.ACTIVE,
        )
    }

    /*
     * 사용량을 읽기 **전에** 사용자 행을 잠가야 한다. 순서가 뒤집히면 두 요청이 같은 값을
     * 읽은 뒤에야 줄을 서므로 잠금이 아무것도 막지 못한다.
     */
    @Test
    fun `locks the user row before reading usage`() {
        every { storageQuotaPort.calculateUserStorageBytes(100L, null) } returns 0L

        useCase.checkQuota(100L, 1_000L)

        verifyOrder {
            storageQuotaPort.lockUserForQuota(100L)
            storageQuotaPort.calculateUserStorageBytes(100L, null)
        }
    }

    @Test
    fun `locks the user row even when the quota is exceeded`() {
        // 거절 경로에서도 잠금이 걸려야 동시 요청이 같은 사용량을 보고 갈라지지 않는다.
        every { storageQuotaPort.calculateUserStorageBytes(100L, null) } returns PlanType.FREE.storageBytes

        assertFailsWith<StorageQuotaExceededException> { useCase.checkQuota(100L, 1L) }

        verify(exactly = 1) { storageQuotaPort.lockUserForQuota(100L) }
    }

    @Test
    fun `passes the exclusion through so a reservation is not counted twice`() {
        every { storageQuotaPort.calculateUserStorageBytes(100L, 7L) } returns 0L

        useCase.checkQuota(100L, 1_000L, excludeVideoId = 7L)

        verify(exactly = 1) { storageQuotaPort.calculateUserStorageBytes(100L, 7L) }
    }

    @Test
    fun `accepts an upload that fits within the plan limit`() {
        every { storageQuotaPort.calculateUserStorageBytes(100L, null) } returns 0L

        useCase.checkQuota(100L, PlanType.FREE.storageBytes)

        verify(exactly = 1) { storageQuotaPort.lockUserForQuota(100L) }
    }

    @Test
    fun `getCurrentUsage does not take the lock`() {
        // 단순 조회는 쓰기 경로가 아니므로 다른 사용자의 업로드를 막을 이유가 없다.
        every { storageQuotaPort.calculateUserStorageBytes(100L, null) } returns 512L

        assertEquals(512L, useCase.getCurrentUsage(100L))

        verify(exactly = 0) { storageQuotaPort.lockUserForQuota(any()) }
    }
}
