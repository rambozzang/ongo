package com.ongo.application.config

import com.ongo.common.enums.Platform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExecutorConfigTest {

    @Test
    fun `external platform limits are explicit and conservative`() {
        assertEquals(20, ExecutorConfig.platformUploadLimit(Platform.YOUTUBE))
        assertEquals(20, ExecutorConfig.platformUploadLimit(Platform.TIKTOK))
        assertEquals(10, ExecutorConfig.platformUploadLimit(Platform.INSTAGRAM))
        assertEquals(5, ExecutorConfig.platformUploadLimit(Platform.NAVER_CLIP))
        assertEquals(10, ExecutorConfig.platformUploadLimit(Platform.THREADS))
        assertEquals(5, ExecutorConfig.platformUploadLimit(Platform.TWITTER))
        assertEquals(5, ExecutorConfig.platformUploadLimit(Platform.FACEBOOK))
        assertTrue(ExecutorConfig.platformUploadSemaphore(Platform.INSTAGRAM).availablePermits() <= 10)
    }

    @Test
    fun `클러스터 advisory lock 슬롯은 플랫폼별 로컬 한도와 일치하고 서로 겹치지 않는다`() {
        val lockIds = Platform.entries.flatMap { platform ->
            ExecutorConfig.platformUploadLockIds(platform)
        }

        assertEquals(lockIds.size, lockIds.toSet().size)
        Platform.entries.forEach { platform ->
            assertEquals(
                ExecutorConfig.platformUploadLimit(platform),
                ExecutorConfig.platformUploadLockIds(platform).size,
            )
        }
        assertTrue(lockIds.all { it > 0 })
    }
}
