package com.ongo.infrastructure.storage

import com.ongo.application.video.IncompleteMultipartUploadException
import com.ongo.application.video.MultipartUploadSession
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
        // 완료되지 않은 멀티파트 조각은 위 목록에 나오지 않는다. 버려진 업로드
        // (확정 실패·사용자 취소·StaleUploadCleanupUseCase 회수)가 남긴 조각을 여기서 함께 지운다.
        // 이게 없으면 조각이 보이지 않는 채로 버킷 용량을 계속 차지한다.
        storageClient.abortMultipartUploads(prefix)
        log.info("영상 파일 삭제 완료: videoId={}", videoId)
    }

    // ── 멀티파트 ────────────────────────────────────────────────────────────

    override fun supportsMultipartUpload(): Boolean = storageClient.supportsMultipart

    override fun startMultipartUpload(videoId: Long, filename: String, contentType: String): MultipartUploadSession {
        // 키는 단일 PUT 과 같은 규칙으로 서버가 정한다. 확정 로직(getUploadedKey·getUploadedSize)이
        // `videos/{id}/` 아래 첫 오브젝트를 보므로 두 경로가 같은 결과를 낸다.
        val objectKey = "videos/$videoId/${safeFilename(filename)}"
        val uploadId = storageClient.createMultipartUpload(objectKey, contentType)
        return MultipartUploadSession(objectKey, uploadId)
    }

    override fun presignUploadParts(
        videoId: Long,
        objectKey: String,
        uploadId: String,
        partSizes: Map<Int, Long>,
    ): Map<Int, String> {
        requireOwnKey(videoId, objectKey)
        return partSizes.mapValues { (partNumber, size) ->
            storageClient.presignUploadPart(objectKey, uploadId, partNumber, size, PART_URL_EXPIRATION_MINUTES)
        }
    }

    override fun completeMultipartUpload(
        videoId: Long,
        objectKey: String,
        uploadId: String,
        expectedSizes: Map<Int, Long>,
    ) {
        requireOwnKey(videoId, objectKey)
        val uploaded = storageClient.listUploadedParts(objectKey, uploadId).associateBy { it.partNumber }

        // 번호가 빠졌거나 크기가 계획과 다른 조각. 서명이 크기를 묶으므로 크기가 틀릴 일은
        // 드물지만, MinIO 호환 스토리지처럼 서명 강제가 없는 곳을 대비해 여기서 다시 본다.
        val missing = expectedSizes.filter { (partNumber, size) -> uploaded[partNumber]?.size != size }
            .keys.sorted()
        if (missing.isNotEmpty()) throw IncompleteMultipartUploadException(missing)

        // 계획에 없는 조각 번호는 완료 목록에 넣지 않는다 — 넣으면 계획보다 큰 파일이 된다.
        storageClient.completeMultipartUpload(
            objectKey,
            uploadId,
            expectedSizes.keys.sorted().map { uploaded.getValue(it) },
        )
    }

    override fun abortMultipartUpload(videoId: Long, objectKey: String, uploadId: String) {
        requireOwnKey(videoId, objectKey)
        storageClient.abortMultipartUpload(objectKey, uploadId)
    }

    /**
     * [objectKey] 는 클라이언트가 되돌려 보낸 값이다. 이 영상의 경로 밖을 가리키면 거부한다 —
     * 그렇지 않으면 남의 영상 경로에 조각을 올리거나 남의 업로드를 중단할 수 있다.
     * (uploadId 는 키에 묶여 있어 다른 키와 섞어 쓸 수 없다. 키만 막으면 된다.)
     */
    private fun requireOwnKey(videoId: Long, objectKey: String) {
        val prefix = "videos/$videoId/"
        require(
            objectKey.startsWith(prefix) &&
                objectKey.length > prefix.length &&
                !objectKey.substring(prefix.length).contains('/') &&
                !objectKey.contains(".."),
        ) { "업로드 경로가 이 영상의 것이 아닙니다." }
    }

    /**
     * 게시 이미지 객체 정리. 접두사가 videoId 로만 만들어져 남의 객체가 섞일 수 없다.
     *
     * 실패를 삼키지 않는다. 호출부가 "정리하지 못했다" 를 사용자에게 그대로 알려야 한다.
     */
    override fun deleteContentImages(videoId: Long) {
        val prefix = "content/$videoId/"
        storageClient.listObjects(prefix).forEach { key -> storageClient.deleteFile(key) }
        log.info("게시 이미지 파일 삭제 완료: videoId={}", videoId)
    }

    override fun deleteFileByKey(key: String) {
        storageClient.deleteFile(key)
    }

    override fun uploadFile(key: String, inputStream: InputStream, contentType: String, size: Long): String {
        storageClient.uploadFile(key, inputStream, contentType, size)
        return storageClient.getFileUrl(key)
    }

    private companion object {
        /**
         * 조각 URL 수명. 클라이언트는 URL 을 한꺼번에 받지 않고 몇 개씩 요청하므로, 이 시간은
         * "파일 전체" 가 아니라 "조각 몇 개" 를 올리는 데 충분하면 된다. 느린 회선에서 16 MiB
         * 조각 하나가 수 분이라 60분이면 여유가 크다.
         */
        const val PART_URL_EXPIRATION_MINUTES = 60
    }
}
