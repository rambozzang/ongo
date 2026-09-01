package com.ongo.domain.video

import java.time.LocalDateTime

data class ContentImage(
    val id: Long? = null,
    val videoId: Long,
    val imageUrl: String,
    val displayOrder: Int = 0,
    val width: Int? = null,
    val height: Int? = null,
    val fileSizeBytes: Long? = null,
    val originalFilename: String? = null,
    val contentType: String? = null,
    /**
     * 서버가 할당한 **실제 저장 키**. 탈퇴 정리가 추측 없이 지울 수 있는 유일한 근거다.
     *
     * V112 이전 행은 `null` 이다. 그 행은 자동 삭제하지 않고 수기 확인 대상으로 센다 —
     * `image_url` 에서 키를 되짚으면 빗나갔을 때 남의 파일을 지운다.
     */
    val storageObjectKey: String? = null,
    val createdAt: LocalDateTime? = null,
)
