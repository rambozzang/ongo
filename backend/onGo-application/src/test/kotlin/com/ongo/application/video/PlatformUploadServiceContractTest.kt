package com.ongo.application.video

import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertIs
import kotlin.test.assertEquals

class PlatformUploadServiceContractTest {
    @Test
    fun `published response without a URL is unconfirmed instead of throwing`() {
        val outcome = PlatformUploadResult(
            success = true,
            platformVideoId = "video-1",
            platformUrl = null,
            published = true,
        ).toPublishOutcome()

        assertIs<PublishOutcome.Unconfirmed>(outcome)
    }

    @Test
    fun `accepted response without an identifier is unconfirmed instead of retryable`() {
        val outcome = PlatformUploadResult(
            success = true,
            published = false,
        ).toPublishOutcome()

        assertIs<PublishOutcome.Unconfirmed>(outcome)
    }

    @Test
    fun `retryable provider failure preserves durable retry timing`() {
        val outcome = PlatformUploadResult(
            success = false,
            errorMessage = "429 Too Many Requests",
            published = false,
            retryable = true,
            retryAfter = Duration.ofSeconds(12),
        ).toPublishOutcome()

        val failed = assertIs<PublishOutcome.Failed>(outcome)
        assertEquals(true, failed.retryable)
        assertEquals(Duration.ofSeconds(12), failed.retryAfter)
    }

    @Test
    fun `accepted response preserves provider retry timing`() {
        val outcome = PlatformUploadResult(
            success = true,
            platformVideoId = "processing-1",
            pollToken = "poll-1",
            published = false,
            retryAfter = Duration.ofSeconds(47),
        ).toPublishOutcome()

        val accepted = assertIs<PublishOutcome.Accepted>(outcome)
        assertEquals(Duration.ofSeconds(47), accepted.retryAfter)
    }
}
