package com.ongo.infrastructure.external.platform

import com.ongo.application.video.PublishConfirmation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import java.time.Duration

class PlatformUploadFailureTest {

    @Test
    fun `429 response preserves Retry-After for durable retry`() {
        val headers = HttpHeaders().apply { set("Retry-After", "9") }
        val error = HttpClientErrorException.create(
            HttpStatus.TOO_MANY_REQUESTS,
            "Too Many Requests",
            headers,
            ByteArray(0),
            Charsets.UTF_8,
        )

        val result = uploadFailureResult(error)

        assertThat(result.retryable).isTrue()
        assertThat(result.retryAfter).isEqualTo(Duration.ofSeconds(9))
        assertThat(result.httpStatus).isEqualTo(429)
        assertThat(result.confirmation).isEqualTo(PublishConfirmation.CONFIRMED)
    }

    @Test
    fun `network timeout is never converted into an automatic retry`() {
        val result = uploadFailureResult(java.net.SocketTimeoutException("read timed out"))

        assertThat(result.retryable).isFalse()
        assertThat(result.confirmation).isEqualTo(PublishConfirmation.UNKNOWN)
    }

    @Test
    fun `provider 5xx remains unconfirmed to prevent duplicate publishing`() {
        val error = HttpClientErrorException.create(
            HttpStatus.BAD_GATEWAY,
            "Bad Gateway",
            HttpHeaders.EMPTY,
            ByteArray(0),
            Charsets.UTF_8,
        )

        val result = uploadFailureResult(error)

        assertThat(result.retryable).isFalse()
        assertThat(result.confirmation).isEqualTo(PublishConfirmation.UNKNOWN)
    }
}
