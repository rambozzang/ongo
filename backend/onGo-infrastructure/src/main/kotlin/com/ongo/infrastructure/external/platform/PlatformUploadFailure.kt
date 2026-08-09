package com.ongo.infrastructure.external.platform

import com.ongo.application.video.PlatformUploadResult
import com.ongo.application.video.indeterminateUploadFailure
import com.ongo.application.video.isIndeterminateUploadFailure
import org.springframework.web.client.HttpStatusCodeException

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
        PlatformUploadResult(
            success = false,
            published = false,
            errorMessage = error.message,
            httpStatus = httpStatus,
        )
    }
}
