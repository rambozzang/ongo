package com.ongo.application.video

import com.ongo.common.enums.Platform
import java.time.Duration
import java.net.ConnectException
import java.io.IOException
import java.io.FileNotFoundException
import java.net.SocketTimeoutException

interface PlatformUploadService {
    fun supports(platform: Platform): Boolean
    fun upload(config: PlatformUploadConfig, fileUrl: String, userId: Long): PlatformUploadResult
    /** 비동기 게시가 수락된 뒤 외부 플랫폼의 최종 상태를 확인한다. */
    fun poll(
        platform: Platform,
        pollToken: String,
        userId: Long,
        knownPlatformUrl: String? = null,
        channelId: Long? = null,
    ): PlatformUploadResult =
        PlatformUploadResult(success = false, published = false, errorMessage = "${platform.name} 상태 조회를 지원하지 않습니다.")
}

enum class PublishConfirmation {
    CONFIRMED,
    UNKNOWN,
}

data class PlatformUploadResult(
    val success: Boolean,
    val platformVideoId: String? = null,
    val platformUrl: String? = null,
    val errorMessage: String? = null,
    /**
     * 전송 성공이 곧 '게시 완료'를 뜻하는지.
     *
     * true 면 플랫폼이 이미 시청 가능한 콘텐츠를 만들었다는 뜻이라 PUBLISHED 로 확정한다.
     * false 면 플랫폼이 비동기로 처리·심사 중이므로 PROCESSING 에 머문다.
     *
     * 기본값을 false 로 둔 것은 의도적이다. 새 플랫폼을 추가하면서 이 값을 잊으면
     * "게시 안 됐는데 완료로 표시"되는 쪽이 아니라 "완료됐는데 처리중으로 표시"되는
     * 쪽으로 틀리게 해서, 사용자를 속이지 않게 한다.
     */
    val published: Boolean,
    /** 비동기 플랫폼의 상태 조회 토큰. 없으면 platformVideoId를 토큰으로 사용한다. */
    val pollToken: String? = null,
    /** 외부 호출이 끝났지만 응답을 잃었을 때 중복 재전송을 막기 위한 구분. */
    val confirmation: PublishConfirmation = PublishConfirmation.CONFIRMED,
    /** writer가 예외를 결과로 변환한 경우에도 토큰 갱신 판단을 잃지 않도록 보존한다. */
    val httpStatus: Int? = null,
    /** 외부 일시 오류를 다음 durable 시도로 넘길 수 있는지. */
    val retryable: Boolean = false,
    /** Retry-After 또는 지수 백오프를 반영한 다음 시도까지의 대기 시간. */
    val retryAfter: Duration? = null,
)

/** 외부 게시 결과를 호출자가 놓치지 않도록 성공 의미를 sealed 타입으로 고정한다. */
sealed interface PublishOutcome {
    data class Published(
        val platformVideoId: String,
        val platformUrl: String,
    ) : PublishOutcome {
        init {
            require(platformVideoId.isNotBlank()) { "게시 완료 결과에는 platformVideoId가 필요합니다." }
            require(platformUrl.isUsablePlatformUrl()) { "게시 완료 결과에는 사용 가능한 platformUrl이 필요합니다." }
        }
    }

    data class Accepted(
        val platformVideoId: String,
        val pollToken: String,
        val retryAfter: Duration,
    ) : PublishOutcome {
        init {
            require(platformVideoId.isNotBlank()) { "처리 중 결과에는 platformVideoId가 필요합니다." }
            require(pollToken.isNotBlank()) { "처리 중 결과에는 pollToken이 필요합니다." }
            require(!retryAfter.isNegative && !retryAfter.isZero) { "retryAfter는 양수여야 합니다." }
        }
    }

    data class Failed(
        val message: String,
        val retryable: Boolean,
        val retryAfter: Duration? = null,
    ) : PublishOutcome {
        init { require(message.isNotBlank()) { "실패 결과 메시지가 필요합니다." } }
    }

    /** 외부 호출 결과를 확인하지 못했으므로 자동 재전송하면 안 된다. */
    data class Unconfirmed(
        val message: String,
        val platformVideoId: String? = null,
        val pollToken: String? = null,
    ) : PublishOutcome {
        init { require(message.isNotBlank()) { "확인 불가 결과 메시지가 필요합니다." } }
    }
}

fun PlatformUploadResult.toPublishOutcome(): PublishOutcome = when {
    confirmation == PublishConfirmation.UNKNOWN -> PublishOutcome.Unconfirmed(
        message = errorMessage ?: "게시 결과를 확인하지 못했습니다. 중복 게시를 막기 위해 자동 재전송하지 않습니다.",
        platformVideoId = platformVideoId,
        pollToken = pollToken,
    )
    !success -> PublishOutcome.Failed(
        errorMessage ?: "플랫폼 게시에 실패했습니다.",
        retryable = retryable,
        retryAfter = retryAfter,
    )
    published && platformVideoId.isNullOrBlank() -> PublishOutcome.Unconfirmed(
        message = "플랫폼이 게시 성공 응답을 반환했지만 게시 ID가 없습니다. 중복 게시를 막기 위해 자동 재전송하지 않습니다.",
        platformVideoId = platformVideoId,
        pollToken = pollToken,
    )
    published && platformUrl?.isUsablePlatformUrl() != true -> PublishOutcome.Unconfirmed(
        message = "플랫폼이 게시 성공 응답을 반환했지만 확인 가능한 게시 URL이 없습니다. 중복 게시를 막기 위해 자동 재전송하지 않습니다.",
        platformVideoId = platformVideoId,
        pollToken = pollToken,
    )
    published -> PublishOutcome.Published(platformVideoId!!, platformUrl!!)
    platformVideoId.isNullOrBlank() -> PublishOutcome.Unconfirmed(
        message = "플랫폼이 업로드를 접수했지만 게시 ID가 없습니다. 중복 게시를 막기 위해 자동 재전송하지 않습니다.",
        platformVideoId = platformVideoId,
        pollToken = pollToken,
    )
    (pollToken ?: platformVideoId).isNullOrBlank() -> PublishOutcome.Unconfirmed(
        message = "플랫폼이 업로드를 접수했지만 상태 조회 토큰이 없습니다. 중복 게시를 막기 위해 자동 재전송하지 않습니다.",
        platformVideoId = platformVideoId,
        pollToken = pollToken,
    )
    else -> PublishOutcome.Accepted(
        platformVideoId = platformVideoId!!,
        pollToken = pollToken ?: platformVideoId!!,
        retryAfter = Duration.ofSeconds(30),
    )
}

/**
 * A writer may be called after bytes have already reached the provider. Network
 * failures therefore cannot be treated as ordinary, retryable 4xx failures.
 */
fun indeterminateUploadFailure(message: String?): PlatformUploadResult =
    PlatformUploadResult(
        success = false,
        published = false,
        errorMessage = message ?: "게시 결과를 확인하지 못했습니다.",
        confirmation = PublishConfirmation.UNKNOWN,
    )

fun Throwable.isIndeterminateUploadFailure(): Boolean {
    val chain = generateSequence(this) { it.cause }.toList()
    if (chain.any { it is FileNotFoundException }) return false
    if (chain.any { it is SocketTimeoutException || it is ConnectException || it is IOException }) return true
    if (chain.any { it.message?.contains("timeout", ignoreCase = true) == true ||
            it.message?.contains("timed out", ignoreCase = true) == true ||
            it.message?.contains("connection reset", ignoreCase = true) == true }) return true
    return chain.any {
        it is org.springframework.web.client.HttpStatusCodeException &&
            it.statusCode.value() >= 500
    }
}

private fun String.isUsablePlatformUrl(): Boolean =
    startsWith("https://") || startsWith("http://")
