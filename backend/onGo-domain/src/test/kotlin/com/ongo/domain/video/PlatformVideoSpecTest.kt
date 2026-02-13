package com.ongo.domain.video

import com.ongo.common.enums.Platform
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PlatformVideoSpecTest {

    @Test
    fun `YouTube spec should be 1920x1080 at 8000kbps`() {
        val spec = PlatformVideoSpec.forPlatform(Platform.YOUTUBE)

        assertEquals(1920, spec.width)
        assertEquals(1080, spec.height)
        assertEquals(8000, spec.bitrateKbps)
        assertEquals("libx264", spec.codec)
        assertEquals("aac", spec.audioCodec)
        assertEquals("128k", spec.audioBitrate)
    }

    @Test
    fun `TikTok spec should be 1080x1920 at 4000kbps`() {
        val spec = PlatformVideoSpec.forPlatform(Platform.TIKTOK)

        assertEquals(1080, spec.width)
        assertEquals(1920, spec.height)
        assertEquals(4000, spec.bitrateKbps)
    }

    @Test
    fun `Instagram spec should be 1080x1920 at 4000kbps`() {
        val spec = PlatformVideoSpec.forPlatform(Platform.INSTAGRAM)

        assertEquals(1080, spec.width)
        assertEquals(1920, spec.height)
        assertEquals(4000, spec.bitrateKbps)
    }

    @Test
    fun `Naver Clip spec should be 1080x1920 at 4000kbps`() {
        val spec = PlatformVideoSpec.forPlatform(Platform.NAVER_CLIP)

        assertEquals(1080, spec.width)
        assertEquals(1920, spec.height)
        assertEquals(4000, spec.bitrateKbps)
    }

    @ParameterizedTest
    @EnumSource(Platform::class)
    fun `all platforms should return non-null specs with valid codec`(platform: Platform) {
        val spec = PlatformVideoSpec.forPlatform(platform)

        assertNotNull(spec)
        assert(spec.width > 0)
        assert(spec.height > 0)
        assert(spec.bitrateKbps > 0)
        assertEquals("libx264", spec.codec)
        assertEquals("aac", spec.audioCodec)
    }

    @Test
    fun `vertical platforms should all have 9-16 aspect ratio`() {
        val verticalPlatforms = listOf(Platform.TIKTOK, Platform.INSTAGRAM, Platform.NAVER_CLIP)

        verticalPlatforms.forEach { platform ->
            val spec = PlatformVideoSpec.forPlatform(platform)
            assertEquals(1080, spec.width, "$platform width should be 1080")
            assertEquals(1920, spec.height, "$platform height should be 1920")
        }
    }

    @Test
    fun `YouTube should be the only horizontal platform`() {
        val ytSpec = PlatformVideoSpec.forPlatform(Platform.YOUTUBE)

        assert(ytSpec.width > ytSpec.height) { "YouTube should be landscape (width > height)" }

        listOf(Platform.TIKTOK, Platform.INSTAGRAM, Platform.NAVER_CLIP).forEach { platform ->
            val spec = PlatformVideoSpec.forPlatform(platform)
            assert(spec.height > spec.width) { "$platform should be portrait (height > width)" }
        }
    }
}
