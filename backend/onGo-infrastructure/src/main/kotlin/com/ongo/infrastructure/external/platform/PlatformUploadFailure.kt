package com.ongo.infrastructure.external.platform

import com.ongo.application.video.PlatformUploadResult
import com.ongo.application.video.indeterminateUploadFailure
import com.ongo.application.video.isIndeterminateUploadFailure

/** Preserve whether a writer failure is safe to retry without duplicating a post. */
internal fun uploadFailureResult(error: Exception): PlatformUploadResult =
    if (error.isIndeterminateUploadFailure()) {
        indeterminateUploadFailure(error.message)
    } else {
        PlatformUploadResult(success = false, errorMessage = error.message)
    }
