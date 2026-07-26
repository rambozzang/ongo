package com.ongo.application.video

import com.ongo.common.enums.Platform

interface PlatformUploadService {
    fun supports(platform: Platform): Boolean
    fun upload(config: PlatformUploadConfig, fileUrl: String, userId: Long): PlatformUploadResult
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
)
