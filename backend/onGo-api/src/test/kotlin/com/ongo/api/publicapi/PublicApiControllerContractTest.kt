package com.ongo.api.publicapi

import com.ongo.application.channel.ChannelUseCase
import com.ongo.application.notification.NotificationUseCase
import com.ongo.application.publicapi.ChangePublicPostStatusRequest
import com.ongo.application.publicapi.CreatePublicPostRequest
import com.ongo.application.publicapi.GeneratedVideoUseCase
import com.ongo.application.publicapi.PublicApiAnalyticsUseCase
import com.ongo.application.publicapi.PublicApiMediaUseCase
import com.ongo.application.publicapi.PublicApiUseCase
import com.ongo.application.publicapi.PublicConnectionResponse
import com.ongo.application.publicapi.PublicFieldLimit
import com.ongo.application.publicapi.PublicIntegrationSettingsOutput
import com.ongo.application.publicapi.PublicIntegrationSettingsResponse
import com.ongo.application.publicapi.PublicPostResponse
import com.ongo.application.publicapi.PublicPostTargetResponse
import com.ongo.application.publicapi.PublicOAuthUseCase
import com.ongo.application.publicapi.PublicPostSettingsResponse
import com.ongo.application.publicapi.UpdatePublicPostSettingsRequest
import com.ongo.application.publicapi.VideoFunctionUseCase
import com.ongo.application.workspace.WorkspaceUseCase
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.TestingAuthenticationToken

class PublicApiControllerContractTest {
    private val useCase = mockk<PublicApiUseCase>(relaxed = true)
    private val analyticsUseCase = mockk<PublicApiAnalyticsUseCase>(relaxed = true)
    private val controller = PublicApiController(
        useCase = useCase,
        mediaUseCase = mockk<PublicApiMediaUseCase>(relaxed = true),
        analyticsUseCase = analyticsUseCase,
        channelUseCase = mockk<ChannelUseCase>(relaxed = true),
        notificationUseCase = mockk<NotificationUseCase>(relaxed = true),
        workspaceUseCase = mockk<WorkspaceUseCase>(relaxed = true),
        generatedVideoUseCase = mockk<GeneratedVideoUseCase>(relaxed = true),
        videoFunctionUseCase = mockk<VideoFunctionUseCase>(relaxed = true),
        publicOAuthUseCase = mockk<PublicOAuthUseCase>(relaxed = true),
    )
    private val apiKeyAuthentication = TestingAuthenticationToken(
        1L,
        "api-key",
        "AUTH_API_KEY",
    )

    @Test
    fun `is-connected returns raw Postiz object instead of ResData envelope`() {
        val response = controller.isConnected(1L, apiKeyAuthentication)

        assertEquals(200, response.statusCode.value())
        assertEquals(PublicConnectionResponse(true), response.body)
    }

    @Test
    fun `integration settings returns only the documented output envelope`() {
        val settings = PublicIntegrationSettingsResponse(
            id = "7",
            provider = "youtube",
            title = PublicFieldLimit(maxLength = 100),
            description = PublicFieldLimit(maxLength = 5_000),
            tags = PublicFieldLimit(maxCount = 500),
            scheduling = true,
            directVideoUpload = true,
            cloudVideoUpload = true,
            maxFileSizeBytes = 100,
            acceptedExtensions = setOf("mp4"),
            unavailableReason = null,
            output = PublicIntegrationSettingsOutput("rules", 100, mockk(relaxed = true), emptyList()),
        )
        every { useCase.integrationSettings(1L, "7") } returns settings

        val body = controller.integrationSettings(1L, apiKeyAuthentication, "7").body
        val map = assertIs<Map<*, *>>(body)

        assertEquals(settings.output, map["output"])
        assertFalse(map.containsKey("provider"))
    }

    @Test
    fun `list posts returns documented posts wrapper and state fields`() {
        every { useCase.list(1L, 50, null, null, null) } returns listOf(
            PublicPostResponse(
                id = "41",
                type = "schedule",
                status = "scheduled",
                state = "QUEUE",
                date = "2026-08-20T10:00:00",
                videoId = 11,
                error = null,
                posts = listOf(PublicPostTargetResponse("7", "SCHEDULED", null, null)),
                content = "hello",
            ),
        )

        val body = controller.list(1L, apiKeyAuthentication, 50, null, null, null).body
        val map = assertIs<Map<*, *>>(body)
        val posts = assertIs<List<*>>(map["posts"])
        val post = assertIs<com.ongo.application.publicapi.PublicPostListItem>(posts.single())

        assertEquals("41", post.id)
        assertEquals("hello", post.content)
        assertEquals("QUEUE", post.state)
        assertEquals(mapOf("id" to "7"), post.integration)
    }

    @Test
    fun `list maps uncertain or partial publishing to error instead of queue`() {
        every { useCase.list(1L, 50, null, null, null) } returns listOf(
            PublicPostResponse(
                id = "42",
                type = "now",
                status = "partially_published",
                state = "PARTIALLY_PUBLISHED",
                date = null,
                videoId = 12,
                error = "one channel failed",
                posts = emptyList(),
            ),
        )

        val body = controller.list(1L, apiKeyAuthentication, 50, null, null, null).body
        val post = assertIs<com.ongo.application.publicapi.PublicPostListItem>(
            assertIs<List<*>>(assertIs<Map<*, *>>(body)["posts"]).single(),
        )

        assertEquals("ERROR", post.state)
    }

    @Test
    fun `change status returns documented id and state object`() {
        every { useCase.changeStatus(1L, 41L, ChangePublicPostStatusRequest("schedule", null)) } returns
            PublicPostResponse("41", "schedule", "scheduled", "QUEUE", null, 11, null, emptyList())

        val response = controller.changeStatus(
            1L,
            apiKeyAuthentication,
            41L,
            ChangePublicPostStatusRequest("schedule", null),
        )

        assertEquals(mapOf("id" to "41", "state" to "QUEUE"), response.body)
    }

    @Test
    fun `create forwards Idempotency-Key to the durable public API use case`() {
        val request = CreatePublicPostRequest(type = "draft")
        every { useCase.create(1L, request, "retry-key") } returns
            PublicPostResponse("41", "draft", "draft", "DRAFT", null, 11, null, emptyList())

        val response = controller.create(1L, apiKeyAuthentication, "retry-key", request)

        assertEquals(200, response.statusCode.value())
        verify { useCase.create(1L, request, "retry-key") }
    }

    @Test
    fun `post analytics accepts the official date query alias`() {
        every { analyticsUseCase.post(1L, 41L, 90) } returns emptyList()

        val response = controller.postAnalytics(1L, apiKeyAuthentication, 41L, 30, 90)

        assertEquals(200, response.statusCode.value())
        assertEquals(emptyList<Any>(), response.body)
    }

    @Test
    fun `settings update returns Postiz postId and publishDate`() {
        val request = UpdatePublicPostSettingsRequest(mockk(relaxed = true))
        every { useCase.updateSettings(1L, 41L, request) } returns
            PublicPostSettingsResponse("41", "2026-08-20T01:00:00Z")

        val response = controller.updateSettings(1L, apiKeyAuthentication, 41L, request)

        assertEquals(200, response.statusCode.value())
        assertEquals(PublicPostSettingsResponse("41", "2026-08-20T01:00:00Z"), response.body)
        verify { useCase.updateSettings(1L, 41L, request) }
    }
}
