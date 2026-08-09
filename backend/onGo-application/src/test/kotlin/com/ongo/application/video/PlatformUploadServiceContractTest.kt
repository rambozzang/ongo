package com.ongo.application.video

import org.junit.jupiter.api.Test
import kotlin.test.assertIs

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
}
