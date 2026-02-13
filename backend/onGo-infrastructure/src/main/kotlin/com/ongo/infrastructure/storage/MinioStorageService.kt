package com.ongo.infrastructure.storage

import com.ongo.application.video.StorageService
import com.ongo.common.enums.Platform
import io.minio.*
import io.minio.http.Method
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.InputStream
import java.util.concurrent.TimeUnit

@Service
class MinioStorageService(
    private val minioClient: MinioClient,
    @Value("\${storage.minio.bucket:ongo-videos}")
    private val bucket: String,
    @Value("\${storage.tus-endpoint:http://localhost:8777/api/v1/videos/upload/tus}")
    private val tusBaseEndpoint: String,
) : StorageService {

    private val log = LoggerFactory.getLogger(javaClass)

    init {
        ensureBucketExists()
    }

    override fun generateUploadUrl(videoId: Long, filename: String, contentType: String): String {
        val objectName = "videos/$videoId/$filename"
        return minioClient.getPresignedObjectUrl(
            GetPresignedObjectUrlArgs.builder()
                .method(Method.PUT)
                .bucket(bucket)
                .`object`(objectName)
                .expiry(1, TimeUnit.HOURS)
                .build()
        )
    }

    override fun getTusEndpoint(videoId: Long): String {
        return "$tusBaseEndpoint/$videoId"
    }

    override fun getFileUrl(videoId: Long): String {
        val prefix = "videos/$videoId/"
        val objects = minioClient.listObjects(
            ListObjectsArgs.builder()
                .bucket(bucket)
                .prefix(prefix)
                .maxKeys(1)
                .build()
        )
        val firstObject = objects.firstOrNull()?.get()
            ?: throw IllegalStateException("업로드된 파일을 찾을 수 없습니다: videoId=$videoId")

        return minioClient.getPresignedObjectUrl(
            GetPresignedObjectUrlArgs.builder()
                .method(Method.GET)
                .bucket(bucket)
                .`object`(firstObject.objectName())
                .expiry(7, TimeUnit.DAYS)
                .build()
        )
    }

    override fun deleteFile(videoId: Long) {
        val prefix = "videos/$videoId/"
        val objects = minioClient.listObjects(
            ListObjectsArgs.builder()
                .bucket(bucket)
                .prefix(prefix)
                .recursive(true)
                .build()
        )

        objects.forEach { result ->
            val obj = result.get()
            minioClient.removeObject(
                RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .`object`(obj.objectName())
                    .build()
            )
        }
        log.info("영상 파일 삭제 완료: videoId={}", videoId)
    }

    override fun downloadFile(videoId: Long): InputStream {
        val prefix = "videos/$videoId/"
        val objects = minioClient.listObjects(
            ListObjectsArgs.builder()
                .bucket(bucket)
                .prefix(prefix)
                .maxKeys(1)
                .build()
        )
        val firstObject = objects.firstOrNull()?.get()
            ?: throw IllegalStateException("업로드된 파일을 찾을 수 없습니다: videoId=$videoId")

        return minioClient.getObject(
            GetObjectArgs.builder()
                .bucket(bucket)
                .`object`(firstObject.objectName())
                .build()
        )
    }

    override fun uploadVariant(videoId: Long, platform: Platform, inputStream: InputStream, size: Long): String {
        val objectName = "videos/$videoId/variants/${platform.name.lowercase()}/video.mp4"
        minioClient.putObject(
            PutObjectArgs.builder()
                .bucket(bucket)
                .`object`(objectName)
                .stream(inputStream, size, -1)
                .contentType("video/mp4")
                .build()
        )

        val url = minioClient.getPresignedObjectUrl(
            GetPresignedObjectUrlArgs.builder()
                .method(Method.GET)
                .bucket(bucket)
                .`object`(objectName)
                .expiry(7, TimeUnit.DAYS)
                .build()
        )

        log.info("변환본 업로드 완료: videoId={}, platform={}", videoId, platform)
        return url
    }

    private fun ensureBucketExists() {
        val exists = minioClient.bucketExists(
            BucketExistsArgs.builder().bucket(bucket).build()
        )
        if (!exists) {
            minioClient.makeBucket(
                MakeBucketArgs.builder().bucket(bucket).build()
            )
            log.info("MinIO 버킷 생성: {}", bucket)
        }
    }
}
