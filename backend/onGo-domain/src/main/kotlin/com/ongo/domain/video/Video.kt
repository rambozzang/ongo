package com.ongo.domain.video

import com.fasterxml.jackson.databind.JsonNode
import com.ongo.common.enums.MediaType
import com.ongo.common.enums.UploadStatus
import com.ongo.domain.contentsource.VideoSource
import java.time.LocalDateTime

data class Video(
    val id: Long? = null,
    val userId: Long,
    val title: String,
    val description: String? = null,
    val tags: List<String> = emptyList(),
    val category: String? = null,
    val fileUrl: String? = null,
    /**
     * 우리 버킷의 정확한 객체 키.
     *
     * fileUrl 은 서명이 붙은 presigned URL 이라 키를 되짚기 어렵고 경로 형식도 어댑터마다
     * 다르다. 탈퇴 시 무엇을 지워야 하는지는 추측이 아니라 기록으로 알아야 해서 따로 둔다.
     * 외부 URL 로 만든 행은 null 이다 — 우리 객체가 아니므로 지울 대상도 아니다.
     */
    val storageObjectKey: String? = null,
    val fileSizeBytes: Long? = null,
    val originalFilename: String? = null,
    val thumbnailUrls: List<String> = emptyList(),
    val mediaType: MediaType = MediaType.VIDEO,
    val status: UploadStatus = UploadStatus.DRAFT,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
    val source: VideoSource = VideoSource.UPLOAD_PC,
    val sourceReference: JsonNode? = null,
)
