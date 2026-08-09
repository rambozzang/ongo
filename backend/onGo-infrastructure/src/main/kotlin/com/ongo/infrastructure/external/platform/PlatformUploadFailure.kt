package com.ongo.infrastructure.external.platform

import com.ongo.application.video.PlatformUploadResult
import com.ongo.application.video.indeterminateUploadFailure
import com.ongo.application.video.isIndeterminateUploadFailure
import org.springframework.web.client.HttpStatusCodeException
import java.time.Duration

/** Preserve whether a writer failure is safe to retry without duplicating a post. */
internal fun uploadFailureResult(
    error: Exception,
    safeToRetryUnauthorized: Boolean = false,
): PlatformUploadResult {
    val httpStatus = generateSequence(error as Throwable?) { it.cause }
        .filterIsInstance<HttpStatusCodeException>()
        .map { it.statusCode.value() }
        .firstOrNull()
    val indeterminate = error.isIndeterminateUploadFailure() ||
        (httpStatus == 401 && !safeToRetryUnauthorized)
    return if (indeterminate) {
        indeterminateUploadFailure(error.message).copy(httpStatus = httpStatus)
    } else {
        // 429 is an explicit refusal and can safely be delayed. A 5xx may have
        // been returned after the provider accepted bytes, so it stays UNKNOWN.
        val retryable = httpStatus == 429
        PlatformUploadResult(
            success = false,
            published = false,
            errorMessage = error.message,
            httpStatus = httpStatus,
            retryable = retryable,
            retryAfter = if (retryable) {
                Duration.ofSeconds(
                    generateSequence(error as Throwable?) { it.cause }
                        .filterIsInstance<HttpStatusCodeException>()
                        .mapNotNull { it.responseHeaders?.getFirst("Retry-After")?.toLongOrNull() }
                        .firstOrNull()
                        ?.coerceIn(1L, 60L)
                        ?: 30L,
                )
            } else {
                null
            },
        )
    }
}
