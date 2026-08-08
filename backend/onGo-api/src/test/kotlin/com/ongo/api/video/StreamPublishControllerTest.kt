package com.ongo.api.video

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ongo.application.video.PlatformUploadCapability
import com.ongo.application.video.StreamPublishResponse
import com.ongo.application.video.StreamPublishUseCase
import com.ongo.common.enums.Platform
import com.ongo.common.enums.Visibility
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockMultipartFile

class StreamPublishControllerTest {
    private val useCase = mockk<StreamPublishUseCase>()
    private val controller = StreamPublishController(useCase, jacksonObjectMapper())

    @Test
    fun `maps multipart metadata to the application request and returns accepted`() {
        val file = MockMultipartFile("file", "clip.mp4", "video/mp4", byteArrayOf(1, 2, 3))
        val metadata = """
            {
              "title":"원본 제목",
              "description":"공통 설명",
              "tags":["#onGo"],
              "category":"creator",
              "platforms":[
                {"platform":"YOUTUBE","title":"채널 제목","visibility":"PUBLIC"}
              ]
            }
        """.trimIndent()
        every { useCase.initiate(42L, any(), any()) } returns StreamPublishResponse(99L)

        val response = controller.streamPublish(42L, metadata, file)

        assertEquals(202, response.statusCode.value())
        assertEquals(99L, response.body?.data?.videoId)
        verify {
            useCase.initiate(42L, any(), match {
                it.title == "원본 제목" &&
                    it.description == "공통 설명" &&
                    it.tags == listOf("#onGo") &&
                    it.platforms.single().platform == Platform.YOUTUBE &&
                    it.platforms.single().title == "채널 제목" &&
                    it.platforms.single().visibility == Visibility.PUBLIC
            })
        }
    }

    @Test
    fun `invalid metadata never reaches the publishing use case`() {
        val file = MockMultipartFile("file", "clip.mp4", "video/mp4", byteArrayOf(1))

        assertFailsWith<IllegalArgumentException> {
            controller.streamPublish(42L, "{invalid", file)
        }

        verify(exactly = 0) { useCase.initiate(any(), any(), any()) }
    }

    @Test
    fun `capabilities endpoint exposes the current platform contract`() {
        val capability = PlatformUploadCapability(
            platform = Platform.YOUTUBE,
            directVideoUpload = true,
            cloudVideoUpload = true,
            scheduling = true,
            maxFileSizeBytes = 1024,
            maxTitleLength = 100,
            maxDescriptionLength = 500,
            maxTagCount = 30,
            acceptedExtensions = setOf("mp4"),
        )
        every { useCase.getCapabilities() } returns listOf(capability)

        val response = controller.capabilities()

        assertEquals(true, response.success)
        assertEquals(Platform.YOUTUBE, response.data?.single()?.platform)
        assertEquals(true, response.data?.single()?.scheduling)
    }
}
