package com.ongo.application.video

import com.ongo.common.enums.Platform
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class PlatformUploadCapabilityTest {
    @Test
    fun `publishing channels expose an explicit capability`() {
        val capabilities = PlatformUploadCapabilities.all().associateBy { it.platform }

        assertEquals(
            setOf(
                Platform.YOUTUBE,
                Platform.TIKTOK,
                Platform.INSTAGRAM,
                Platform.FACEBOOK,
                Platform.NAVER_CLIP,
                Platform.THREADS,
                Platform.TWITTER,
                Platform.PINTEREST,
            ),
            capabilities.keys,
        )
        capabilities.values.forEach { capability ->
            assertTrue(capability.maxFileSizeBytes > 0)
            assertTrue(capability.maxTitleLength > 0)
            assertTrue(capability.maxTagCount >= 0)
            assertTrue(capability.acceptedExtensions.isNotEmpty())
            assertTrue(capability.directVideoUpload || capability.cloudVideoUpload)
        }
    }

    @Test
    fun `unsupported scheduling is explicit for direct only channels`() {
        val capabilities = PlatformUploadCapabilities.all().associateBy { it.platform }

        assertTrue(capabilities.getValue(Platform.YOUTUBE).scheduling)
        assertTrue(capabilities.getValue(Platform.NAVER_CLIP).scheduling)
        assertFalse(capabilities.getValue(Platform.TIKTOK).scheduling)
        assertFalse(capabilities.getValue(Platform.TWITTER).scheduling)
        assertFalse(capabilities.getValue(Platform.INSTAGRAM).scheduling)
        assertFalse(capabilities.getValue(Platform.THREADS).scheduling)
        assertFalse(capabilities.getValue(Platform.FACEBOOK).directVideoUpload)
        assertNotNull(capabilities.getValue(Platform.FACEBOOK).unavailableReason)
    }
}
