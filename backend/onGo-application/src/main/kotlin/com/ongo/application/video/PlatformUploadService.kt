package com.ongo.application.video

import com.ongo.common.enums.Platform
import java.time.Duration

interface PlatformUploadService {
    fun supports(platform: Platform): Boolean
    fun upload(config: PlatformUploadConfig, fileUrl: String, userId: Long): PlatformUploadResult
    /** 비동기 게시가 수락된 뒤 외부 플랫폼의 최종 상태를 확인한다. */
    fun poll(platform: Platform, pollToken: String, userId: Long): PlatformUploadResult =
        PlatformUploadResult(success = false, errorMessage = "${platform.name} 상태 조회를 지원하지 않습니다.")
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
    val published: Boolean = false,
    /** 비동기 플랫폼의 상태 조회 토큰. 없으면 platformVideoId를 토큰으로 사용한다. */
    val pollToken: String? = null,
)

/** 외부 게시 결과를 호출자가 놓치지 않도록 성공 의미를 sealed 타입으로 고정한다. */
sealed interface PublishOutcome {
    data class Published(
        val platformVideoId: String,
        val platformUrl: String,
    ) : PublishOutcome {
        init {
            require(platformVideoId.isNotBlank()) { "게시 완료 결과에는 platformVideoId가 필요합니다." }
            require(platformUrl.isNotBlank()) { "게시 완료 결과에는 사용 가능한 platformUrl이 필요합니다." }
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
    ) : PublishOutcome {
        init { require(message.isNotBlank()) { "실패 결과 메시지가 필요합니다." } }
    }
}

fun PlatformUploadResult.toPublishOutcome(): PublishOutcome = when {
    !success -> PublishOutcome.Failed(errorMessage ?: "플랫폼 게시에 실패했습니다.", retryable = true)
    published -> PublishOutcome.Published(platformVideoId.orEmpty(), platformUrl.orEmpty())
    else -> PublishOutcome.Accepted(
        platformVideoId = platformVideoId.orEmpty(),
        pollToken = pollToken ?: platformVideoId.orEmpty(),
        retryAfter = Duration.ofSeconds(30),
    )
}
