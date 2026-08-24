package com.ongo.infrastructure.storage

import com.ongo.application.video.StorageService
import com.ongo.infrastructure.external.storage.StorageClient
import com.ongo.infrastructure.external.storage.StorageProperties
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.InputStream
import java.net.URI

@Service
class VideoStorageService(
    private val storageClient: StorageClient,
    private val storageProperties: StorageProperties,
    @Value("\${storage.tus-endpoint:http://localhost:8070/api/v1/videos/upload/tus}")
    private val tusBaseEndpoint: String,
) : StorageService {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun generateUploadUrl(videoId: Long, filename: String, contentType: String, fileSize: Long): String {
        val objectName = "videos/$videoId/${safeFilename(filename)}"
        return storageClient.generatePresignedUploadUrl(objectName, contentType, fileSize, 60)
    }

    /**
     * 업로드된 오브젝트의 실제 크기. 신고치가 아니라 스토리지가 보고하는 값이다.
     *
     * 오브젝트가 없거나(업로드 미완료) 메타데이터를 못 읽으면 null 을 돌려주고, 판단은 호출부가 한다.
     */
    override fun getUploadedSize(videoId: Long): Long? {
        val key = getUploadedKey(videoId) ?: return null
        return storageClient.getObjectMetadata(key)?.contentLength
    }

    /** 서버가 할당한 prefix 아래 실제로 올라온 객체의 키. */
    override fun getUploadedKey(videoId: Long): String? =
        storageClient.listObjects("videos/$videoId/").firstOrNull()

    private fun safeFilename(filename: String): String =
        filename.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .takeLast(180)
            .ifBlank { "upload.bin" }

    override fun getTusEndpoint(videoId: Long): String {
        return "$tusBaseEndpoint/$videoId"
    }

    override fun getFileUrl(videoId: Long, storedFileUrl: String?): String {
        val prefix = "videos/$videoId/"
        val firstKey = storageClient.listObjects(prefix).firstOrNull()
            ?: storedFileUrl?.let(::resolveStoredObjectKey)
                ?.takeIf(storageClient::objectExists)
            ?: throw IllegalStateException("업로드된 파일을 찾을 수 없습니다: videoId=$videoId")

        return storageClient.generatePresignedDownloadUrl(firstKey, 60 * 24 * 7) // 7 days
    }

    override fun copyVideoFile(sourceVideoId: Long, targetVideoId: Long, storedFileUrl: String?): String {
        require(sourceVideoId > 0 && targetVideoId > 0) { "영상 ID가 올바르지 않습니다." }
        val sourcePrefix = "videos/$sourceVideoId/"
        val sourceKey = storageClient.listObjects(sourcePrefix).firstOrNull()
            ?: storedFileUrl?.let(::resolveStoredObjectKey)
                ?.takeIf(storageClient::objectExists)
            ?: throw IllegalStateException("복제할 영상 파일을 찾을 수 없습니다: videoId=$sourceVideoId")
        val filename = sourceKey.substringAfterLast('/').ifBlank { "video.bin" }
        val targetKey = "videos/$targetVideoId/$filename"
        storageClient.copyObject(sourceKey, targetKey)
        return storageClient.getFileUrl(targetKey)
    }

    /**
     * Old rows did not persist an object key. Their stored URL is still enough
     * to recover the key for our MinIO/S3 adapters, but an arbitrary external
     * URL must never be treated as durable media for a recurring job.
     */
    private fun resolveStoredObjectKey(fileUrl: String): String? {
        val path = runCatching { URI(fileUrl).path }.getOrNull()
            ?.trim('/')
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val bucketPrefix = "${storageProperties.bucket}/"
        return when {
            path.startsWith(bucketPrefix) -> path.removePrefix(bucketPrefix)
            path.startsWith("videos/") -> path
            else -> null
        }?.takeIf { it.isNotBlank() && !it.contains("..") }
    }

    override fun deleteFile(videoId: Long) {
        val prefix = "videos/$videoId/"
        val keys = storageClient.listObjects(prefix)

        keys.forEach { key ->
            storageClient.deleteFile(key)
        }
        log.info("영상 파일 삭제 완료: videoId={}", videoId)
    }

    override fun uploadFile(key: String, inputStream: InputStream, contentType: String, size: Long): String {
        storageClient.uploadFile(key, inputStream, contentType, size)
        return storageClient.getFileUrl(key)
    }
}
