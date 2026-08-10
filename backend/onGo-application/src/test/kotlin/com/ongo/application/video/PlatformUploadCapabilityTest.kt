package com.ongo.application.video

import com.ongo.common.enums.Platform
import com.ongo.common.enums.MediaType
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
                Platform.THREADS,
                Platform.TWITTER,
                Platform.PINTEREST,
                Platform.LINKEDIN,
                Platform.WORDPRESS,
                Platform.DAILYMOTION,
                Platform.VIMEO,
                Platform.TUMBLR,
            ),
            capabilities.keys,
        )
        capabilities.values.forEach { capability ->
            assertTrue(capability.maxFileSizeBytes > 0)
            assertTrue(capability.maxTitleLength > 0)
            assertTrue(capability.maxTagCount >= 0)
            assertTrue(capability.acceptedExtensions.isNotEmpty())
            assertTrue(
                capability.directVideoUpload || capability.cloudVideoUpload ||
                    capability.unavailableReason != null,
            )
        }
    }

    @Test
    fun `unsupported scheduling is explicit for direct only channels`() {
        val capabilities = PlatformUploadCapabilities.all().associateBy { it.platform }

        assertTrue(capabilities.getValue(Platform.YOUTUBE).scheduling)
        assertEquals(2_000, capabilities.getValue(Platform.TIKTOK).maxTitleLength)
        assertEquals(2_000, capabilities.getValue(Platform.TIKTOK).maxCaptionLength)
        assertFalse(capabilities.getValue(Platform.TIKTOK).scheduling)
        assertFalse(capabilities.getValue(Platform.TWITTER).scheduling)
        assertFalse(capabilities.getValue(Platform.TWITTER).directVideoUpload)
        assertFalse(capabilities.getValue(Platform.TWITTER).cloudVideoUpload)
        assertNotNull(capabilities.getValue(Platform.TWITTER).unavailableReason)
        assertFalse(capabilities.getValue(Platform.INSTAGRAM).scheduling)
        assertFalse(capabilities.getValue(Platform.THREADS).scheduling)
        assertFalse(capabilities.getValue(Platform.FACEBOOK).directVideoUpload)
        assertNotNull(capabilities.getValue(Platform.FACEBOOK).unavailableReason)
        assertEquals(
            setOf(MediaType.VIDEO, MediaType.IMAGE),
            capabilities.getValue(Platform.INSTAGRAM).acceptedMediaTypes,
        )
        assertEquals(
            setOf(MediaType.VIDEO, MediaType.IMAGE),
            capabilities.getValue(Platform.THREADS).acceptedMediaTypes,
        )
        assertEquals(
            setOf(MediaType.VIDEO),
            capabilities.getValue(Platform.YOUTUBE).acceptedMediaTypes,
        )
    }
}
