package com.ongo.infrastructure.external.storage

import io.minio.*
import io.minio.errors.ErrorResponseException
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

    override fun copyObject(sourceKey: String, targetKey: String) {
        ensureBucketExists()
        minioClient.copyObject(
            CopyObjectArgs.builder()
                .bucket(storageProperties.bucket)
                .`object`(targetKey)
                .source(
                    CopySource.builder()
                        .bucket(storageProperties.bucket)
                        .`object`(sourceKey)
                        .build(),
                )
                .build(),
        )
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

    /**
     * MinIO 는 presigned URL 에 content-length 를 묶을 수 없다.
     *
     * `getPresignedObjectUrl` 에는 길이를 서명에 포함시키는 인자가 없어서, S3/R2 처럼 스토리지가
     * 크기 불일치를 직접 거부하게 만들 수단이 없다. 그래서 로컬/개발 환경에서는 신고치와 다른
     * 크기의 업로드가 스토리지 단계에서는 통과한다 — 이 차이를 숨기지 않고 남겨두고,
     * 실제 차단은 두 환경 모두 confirm 의 메타데이터 대조가 담당한다.
     */
    override fun generatePresignedUploadUrl(
        key: String,
        contentType: String,
        contentLength: Long,
        expirationMinutes: Int,
    ): String {
        validateStorageKey(key)
        require(contentLength > 0) { "업로드 크기가 올바르지 않습니다." }
        log.debug(
            "MinIO presigned URL 생성(길이 서명 미지원): key={}, length={}, expiry={}분",
            key, contentLength, expirationMinutes,
        )

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

    override fun generatePresignedDownloadUrl(key: String, expirationMinutes: Int): String {
        validateStorageKey(key)
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

    override fun objectExists(key: String): Boolean = try {
        minioClient.statObject(
            StatObjectArgs.builder()
                .bucket(storageProperties.bucket)
                .`object`(key)
                .build(),
        )
        true
    } catch (_: ErrorResponseException) {
        false
    }

    override fun getObjectMetadata(key: String): ObjectMetadata? = try {
        val stat = minioClient.statObject(
            StatObjectArgs.builder()
                .bucket(storageProperties.bucket)
                .`object`(key)
                .build(),
        )
        ObjectMetadata(
            contentLength = stat.size(),
            contentType = stat.contentType(),
            eTag = stat.etag(),
        )
    } catch (_: ErrorResponseException) {
        null
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

    private fun validateStorageKey(key: String) {
        if (key.contains("..") || key.startsWith("/") || !key.matches(Regex("^[a-zA-Z0-9\\-_./]+$"))) {
            throw IllegalArgumentException("Invalid storage key")
        }
    }
}
