package com.ongo.common.exception

class PlatformUploadException(
    val platform: String,
    override val message: String,
    override val cause: Throwable? = null,
) : BusinessException("PLATFORM_UPLOAD_FAILED", "[$platform] 업로드 실패: $message")
