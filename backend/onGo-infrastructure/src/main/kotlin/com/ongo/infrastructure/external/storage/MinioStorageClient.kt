package com.ongo.infrastructure.external.storage

import io.minio.*
import io.minio.http.Method
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.io.InputStream
import java.util.concurrent.TimeUnit

@Component
@Profile("local", "default", "dev")
class MinioStorageClient(
    private val minioClient: MinioClient,
    private val storageProperties: StorageProperties,
) : StorageClient {

    private val log = LoggerFactory.getLogger(MinioStorageClient::class.java)

    override fun uploadFile(key: String, inputStream: InputStream, contentType: String, size: Long): String {
        log.info("MinIO 파일 업로드: key={}, contentType={}, size={}", key, contentType, size)

        ensureBucketExists()

        minioClient.putObject(
            PutObjectArgs.builder()
                .bucket(storageProperties.bucket)
                .`object`(key)
                .stream(inputStream, size, -1)
                .contentType(contentType)
                .build(),
        )

        val url = getFileUrl(key)
        log.info("MinIO 파일 업로드 완료: url={}", url)
        return url
    }

    override fun getFileUrl(key: String): String {
        return "${storageProperties.minio.endpoint}/${storageProperties.bucket}/$key"
    }

    override fun deleteFile(key: String) {
        log.info("MinIO 파일 삭제: key={}", key)

        minioClient.removeObject(
            RemoveObjectArgs.builder()
                .bucket(storageProperties.bucket)
                .`object`(key)
                .build(),
        )
    }

    override fun generatePresignedUploadUrl(key: String, contentType: String, expirationMinutes: Int): String {
        log.debug("MinIO presigned URL 생성: key={}, expiry={}분", key, expirationMinutes)

        ensureBucketExists()

        return minioClient.getPresignedObjectUrl(
            GetPresignedObjectUrlArgs.builder()
                .bucket(storageProperties.bucket)
                .`object`(key)
                .method(Method.PUT)
                .expiry(expirationMinutes, TimeUnit.MINUTES)
                .build(),
        )
    }

    override fun listObjects(prefix: String): List<String> {
        return minioClient.listObjects(
            ListObjectsArgs.builder()
                .bucket(storageProperties.bucket)
                .prefix(prefix)
                .recursive(true)
                .build(),
        ).map { it.get().objectName() }
    }

    override fun downloadFile(key: String): InputStream {
        return minioClient.getObject(
            GetObjectArgs.builder()
                .bucket(storageProperties.bucket)
                .`object`(key)
                .build(),
        )
    }

    override fun generatePresignedDownloadUrl(key: String, expirationMinutes: Int): String {
        log.debug("MinIO presigned GET URL 생성: key={}, expiry={}분", key, expirationMinutes)

        return minioClient.getPresignedObjectUrl(
            GetPresignedObjectUrlArgs.builder()
                .bucket(storageProperties.bucket)
                .`object`(key)
                .method(Method.GET)
                .expiry(expirationMinutes, TimeUnit.MINUTES)
                .build(),
        )
    }

    private fun ensureBucketExists() {
        val exists = minioClient.bucketExists(
            BucketExistsArgs.builder()
                .bucket(storageProperties.bucket)
                .build(),
        )
        if (!exists) {
            minioClient.makeBucket(
                MakeBucketArgs.builder()
                    .bucket(storageProperties.bucket)
                    .build(),
            )
            log.info("MinIO 버킷 생성 완료: {}", storageProperties.bucket)
        }
    }
}
