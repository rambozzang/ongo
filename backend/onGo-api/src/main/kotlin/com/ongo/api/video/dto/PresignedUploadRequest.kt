package com.ongo.api.video.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

data class PresignedUploadRequest(
    @field:NotBlank val filename: String,
    @field:Positive val fileSize: Long,
    @field:NotBlank val contentType: String,
)

data class PresignedUploadResponse(
    val videoId: Long,
    val uploadUrl: String,
)

/**
 * 업로드 시작 응답. `multipart=false` 면 `uploadUrl` 로 단일 PUT(MinIO 로컬 개발),
 * `true` 면 조각 URL 을 몇 개씩 받아 올린다.
 */
data class UploadInitiationResponse(
    val videoId: Long,
    val multipart: Boolean,
    val uploadUrl: String? = null,
    val uploadId: String? = null,
    val objectKey: String? = null,
    val partSize: Long? = null,
    val partCount: Int? = null,
)

data class MultipartPartUrlsRequest(
    @field:NotBlank val uploadId: String,
    @field:NotBlank val objectKey: String,
    @field:NotEmpty @field:Size(max = 20) val partNumbers: List<@Positive Int>,
)

data class MultipartPartUrlsResponse(
    /** 조각 번호 → 서명 URL. JSON 키는 문자열이 된다. */
    val urls: Map<Int, String>,
)

data class MultipartSessionRequest(
    @field:NotBlank val uploadId: String,
    @field:NotBlank val objectKey: String,
)
