package com.ongo.infrastructure.external.storage

import com.ongo.application.common.StorageService
import java.io.InputStream

data class ObjectMetadata(val contentLength: Long, val contentType: String?, val eTag: String?)

interface StorageClient : StorageService {
    override fun uploadFile(key: String, inputStream: InputStream, contentType: String, size: Long): String
    fun getFileUrl(key: String): String
    fun copyObject(sourceKey: String, targetKey: String)
    fun deleteFile(key: String)
    /**
     * 업로드용 서명 URL.
     *
     * `contentLength` 는 클라이언트가 신고한 크기다. 서명에 포함할 수 있는 어댑터(S3/R2)는
     * 포함해서 다른 크기의 PUT 을 스토리지가 직접 거부하게 하고, 그럴 수 없는 어댑터(MinIO)는
     * 무시한다. 어느 쪽이든 confirm 단계에서 실제 메타데이터로 다시 검증한다.
     */
    fun generatePresignedUploadUrl(
        key: String,
        contentType: String,
        contentLength: Long,
        expirationMinutes: Int,
    ): String
    fun listObjects(prefix: String): List<String>
    fun generatePresignedDownloadUrl(key: String, expirationMinutes: Int): String
    fun objectExists(key: String): Boolean
    fun getObjectMetadata(key: String): ObjectMetadata?

    // ── 멀티파트 (S3/R2 만 구현. MinIO 어댑터는 기본값 그대로 "미지원") ─────────

    val supportsMultipart: Boolean get() = false

    fun createMultipartUpload(key: String, contentType: String): String = unsupportedMultipart()

    /** 조각 하나의 서명 URL. [contentLength] 가 서명에 들어가 다른 크기의 조각은 거부된다. */
    fun presignUploadPart(
        key: String,
        uploadId: String,
        partNumber: Int,
        contentLength: Long,
        expirationMinutes: Int,
    ): String = unsupportedMultipart()

    fun listUploadedParts(key: String, uploadId: String): List<UploadedPart> = unsupportedMultipart()

    fun completeMultipartUpload(key: String, uploadId: String, parts: List<UploadedPart>): Unit =
        unsupportedMultipart()

    fun abortMultipartUpload(key: String, uploadId: String): Unit = unsupportedMultipart()

    /**
     * [prefix] 아래 **완료되지 않은** 멀티파트 업로드를 모두 중단한다. 중단한 개수를 돌려준다.
     *
     * 미완료 조각은 오브젝트 목록(`listObjects`)에 나타나지 않는다. 그래서 오브젝트만 지우는
     * 기존 정리 경로로는 닿지 않고, 버려진 업로드의 조각이 용량을 계속 차지한다.
     */
    fun abortMultipartUploads(prefix: String): Int = 0
}

data class UploadedPart(val partNumber: Int, val eTag: String, val size: Long)

private fun unsupportedMultipart(): Nothing =
    throw UnsupportedOperationException("이 스토리지 어댑터는 멀티파트 업로드를 지원하지 않습니다.")
