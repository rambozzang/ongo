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
                // 업로드 API 가 없어도 등재한다. 빠져 있으면 게시 게이트가 이유 없는 예외를
                // 내고, 과거 업로드 행은 클라이언트의 미구현 분기로 흘러간다.
                Platform.NAVER_CLIP,
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

    /**
     * Naver Clip 은 업로드 공개 API 가 없다. 그 사실을 **문장으로** 갖고 있어야 게시
     * 게이트와 과거 업로드 행 정리가 같은 이유를 사용자에게 보여줄 수 있다.
     */
    @Test
    fun `naver clip is registered as explicitly unpublishable with a user facing reason`() {
        val capability = PlatformUploadCapabilities.get(Platform.NAVER_CLIP)

        assertNotNull(capability)
        assertFalse(capability.directVideoUpload)
        assertFalse(capability.cloudVideoUpload)

        val reason = capability.unavailableReason
        assertNotNull(reason)
        // 내부 마이그레이션 안내가 사용자 문구로 새지 않아야 한다.
        assertFalse(reason.contains("StreamPublishUseCase"))
        assertFalse(reason.contains("uploadVideo"))
        assertTrue(reason.contains("Naver Clip"))
    }

    /**
     * 게시 가능 판정의 단일 출처. 업로드 경로가 하나도 없는 플랫폼은 false 여야
     * 스케줄러·리스너가 외부 API 를 부르지 않는다.
     */
    @Test
    fun `canPublish is false for every channel without an upload path`() {
        PlatformUploadCapabilities.all().forEach { capability ->
            assertEquals(
                capability.directVideoUpload || capability.cloudVideoUpload,
                PlatformUploadCapabilities.canPublish(capability.platform),
                "${capability.platform} canPublish 판정이 capability 와 다르다",
            )
        }
        assertFalse(PlatformUploadCapabilities.canPublish(Platform.NAVER_CLIP))
        assertFalse(PlatformUploadCapabilities.canPublish(Platform.TWITTER))
        assertTrue(PlatformUploadCapabilities.canPublish(Platform.YOUTUBE))
        assertTrue(PlatformUploadCapabilities.canPublish(Platform.TIKTOK))
    }

    /** 등재된 플랫폼은 자기 문구를, 등재되지 않은 플랫폼도 사람이 읽을 문장을 준다. */
    @Test
    fun `unsupportedReason prefers the registered explanation`() {
        assertEquals(
            PlatformUploadCapabilities.get(Platform.NAVER_CLIP)?.unavailableReason,
            PlatformUploadCapabilities.unsupportedReason(Platform.NAVER_CLIP),
        )
        assertEquals(
            PlatformUploadCapabilities.get(Platform.TWITTER)?.unavailableReason,
            PlatformUploadCapabilities.unsupportedReason(Platform.TWITTER),
        )
    }
}
