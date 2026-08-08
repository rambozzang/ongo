package com.ongo.application.config

import com.ongo.common.enums.Platform
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

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
}
