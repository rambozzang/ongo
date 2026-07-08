package com.ongo.common.exception

class PlatformApiException(
    val platform: String,
    override val message: String,
    override val cause: Throwable? = null,
) : BusinessException("PLATFORM_API_FAILED", "[$platform] API 호출 실패: $message")
