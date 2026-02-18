package com.ongo.infrastructure.storage

import com.ongo.application.video.StorageService
import com.ongo.infrastructure.external.storage.StorageClient
import com.ongo.infrastructure.external.storage.StorageProperties
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.InputStream

@Service
class VideoStorageService(
    private val storageClient: StorageClient,
    private val storageProperties: StorageProperties,
    @Value("\${storage.tus-endpoint:http://localhost:8777/api/v1/videos/upload/tus}")
    private val tusBaseEndpoint: String,
) : StorageService {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun generateUploadUrl(videoId: Long, filename: String, contentType: String): String {
        val objectName = "videos/$videoId/$filename"
        return storageClient.generatePresignedUploadUrl(objectName, contentType, 60)
    }

    override fun getTusEndpoint(videoId: Long): String {
        return "$tusBaseEndpoint/$videoId"
    }

    override fun getFileUrl(videoId: Long): String {
        val prefix = "videos/$videoId/"
        val keys = storageClient.listObjects(prefix)
        val firstKey = keys.firstOrNull()
            ?: throw IllegalStateException("업로드된 파일을 찾을 수 없습니다: videoId=$videoId")

        return storageClient.generatePresignedDownloadUrl(firstKey, 60 * 24 * 7) // 7 days
    }

    override fun deleteFile(videoId: Long) {
        val prefix = "videos/$videoId/"
        val keys = storageClient.listObjects(prefix)

        keys.forEach { key ->
            storageClient.deleteFile(key)
        }
        log.info("영상 파일 삭제 완료: videoId={}", videoId)
    }

    override fun downloadFile(videoId: Long): InputStream {
        val prefix = "videos/$videoId/"
        val keys = storageClient.listObjects(prefix)
        val firstKey = keys.firstOrNull()
            ?: throw IllegalStateException("업로드된 파일을 찾을 수 없습니다: videoId=$videoId")

        return storageClient.downloadFile(firstKey)
    }

    override fun uploadFile(key: String, inputStream: InputStream, contentType: String, size: Long): String {
        storageClient.uploadFile(key, inputStream, contentType, size)
        return storageClient.getFileUrl(key)
    }
}
