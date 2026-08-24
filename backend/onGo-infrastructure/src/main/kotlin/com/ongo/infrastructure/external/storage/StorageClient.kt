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
}
