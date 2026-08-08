package com.ongo.application.webhook

import com.fasterxml.jackson.databind.ObjectMapper
import com.ongo.application.video.UploadCompletedEvent
import com.ongo.common.enums.Platform
import com.ongo.domain.webhook.Webhook
import com.ongo.domain.webhook.WebhookDelivery
import com.ongo.domain.webhook.WebhookDeliveryRepository
import com.ongo.domain.webhook.WebhookRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebhookDispatcherTest {
    private val webhooks = mockk<WebhookRepository>(relaxed = true)
    private val deliveries = mockk<WebhookDeliveryRepository>(relaxed = true)
    private lateinit var server: MockWebServer
    private lateinit var dispatcher: WebhookDispatcher

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        dispatcher = WebhookDispatcher(webhooks, deliveries, ObjectMapper(), allowUnsafeTargets = true)
    }

    @AfterEach
    fun tearDown() = server.shutdown()

    @Test
    fun `published event is delivered with persisted payload and signature`() {
        server.enqueue(MockResponse().setResponseCode(202).setBody("accepted"))
        val webhook = Webhook(
            id = 9L,
            userId = 7L,
            name = "receiver",
            url = server.url("/events").toString(),
            events = listOf("video.published"),
            secret = "test-secret",
        )
        every { webhooks.findByUserId(7L) } returns listOf(webhook)
        every { deliveries.saveIfAbsent(any()) } returns true

        dispatcher.enqueueUploadEvent(
            UploadCompletedEvent(
                videoId = 42L,
                userId = 7L,
                platform = Platform.YOUTUBE,
                success = true,
                platformUrl = "https://youtube.example/42",
            )
        )

        val saved = slot<WebhookDelivery>()
        verify { deliveries.saveIfAbsent(capture(saved)) }
        assertEquals("video.published", saved.captured.eventType)
        assertTrue(saved.captured.payload.contains("https://youtube.example/42"))

        val claimed = saved.captured.copy(id = 1L, leaseOwner = "worker-1", attemptCount = 1)
        every { deliveries.findDue(any(), any()) } returns listOf(saved.captured.copy(id = 1L))
        every { deliveries.claim(eq(1L), any(), any(), any()) } returns claimed
        every { deliveries.updateOwned(any(), any()) } returns true
        every { webhooks.findById(9L) } returns webhook

        dispatcher.dispatchDueDeliveries()

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals(saved.captured.payload, request.body.readUtf8())
        assertEquals("video.published", request.getHeader("X-Ongo-Webhook-Event"))
        assertTrue(request.getHeader("X-Ongo-Webhook-Signature")!!.startsWith("sha256="))
        val updated = slot<WebhookDelivery>()
        verify { deliveries.updateOwned(capture(updated), "worker-1") }
        assertEquals("DELIVERED", updated.captured.status)
        assertEquals(202, updated.captured.statusCode)
    }
}
