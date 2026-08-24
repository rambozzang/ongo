package com.ongo.infrastructure.external.storage

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.*
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import java.io.InputStream
import java.time.Duration

@Component
@Profile("prod")
class S3StorageClient(
    private val s3Client: S3Client,
    private val s3Presigner: S3Presigner,
    private val storageProperties: StorageProperties,
) : StorageClient {

    private val log = LoggerFactory.getLogger(S3StorageClient::class.java)

    override fun uploadFile(key: String, inputStream: InputStream, contentType: String, size: Long): String {
        log.info("S3 파일 업로드: key={}, contentType={}, size={}", key, contentType, size)

        val putRequest = PutObjectRequest.builder()
            .bucket(storageProperties.bucket)
            .key(key)
            .contentType(contentType)
            .contentLength(size)
            .build()

        s3Client.putObject(putRequest, RequestBody.fromInputStream(inputStream, size))

        val url = getFileUrl(key)
        log.info("S3 파일 업로드 완료: url={}", url)
        return url
    }

    override fun getFileUrl(key: String): String {
        return generatePresignedDownloadUrl(key, 60 * 24 * 7) // 7 days
    }

    override fun copyObject(sourceKey: String, targetKey: String) {
        s3Client.copyObject(
            CopyObjectRequest.builder()
                .copySource("${storageProperties.bucket}/$sourceKey")
                .destinationBucket(storageProperties.bucket)
                .destinationKey(targetKey)
                .build(),
        )
    }

    override fun deleteFile(key: String) {
        log.info("S3 파일 삭제: key={}", key)

        val deleteRequest = DeleteObjectRequest.builder()
            .bucket(storageProperties.bucket)
            .key(key)
            .build()

        s3Client.deleteObject(deleteRequest)
    }

    override fun generatePresignedUploadUrl(
        key: String,
        contentType: String,
        contentLength: Long,
        expirationMinutes: Int,
    ): String {
        validateStorageKey(key)
        require(contentLength > 0) { "업로드 크기가 올바르지 않습니다." }
        log.debug(
            "S3 presigned URL 생성: key={}, length={}, expiry={}분",
            key, contentLength, expirationMinutes,
        )

        // content-length 를 요청에 넣으면 presigner 가 서명 대상 헤더에 포함시킨다.
        // 신고한 크기와 다른 PUT 은 스토리지가 서명 불일치로 거부하므로, 용량 우회가
        // 서버까지 오지 않고 그 자리에서 막힌다.
        val putObjectRequest = PutObjectRequest.builder()
            .bucket(storageProperties.bucket)
            .key(key)
            .contentType(contentType)
            .contentLength(contentLength)
            .build()

        val presignRequest = PutObjectPresignRequest.builder()
            .signatureDuration(Duration.ofMinutes(expirationMinutes.toLong()))
            .putObjectRequest(putObjectRequest)
            .build()

        return s3Presigner.presignPutObject(presignRequest).url().toExternalForm()
    }

    override fun listObjects(prefix: String): List<String> {
        val request = ListObjectsV2Request.builder()
            .bucket(storageProperties.bucket)
            .prefix(prefix)
            .build()

        return s3Client.listObjectsV2(request).contents().map { it.key() }
    }

    override fun generatePresignedDownloadUrl(key: String, expirationMinutes: Int): String {
        validateStorageKey(key)
        log.debug("S3 presigned GET URL 생성: key={}, expiry={}분", key, expirationMinutes)

        val getObjectRequest = GetObjectRequest.builder()
            .bucket(storageProperties.bucket)
            .key(key)
            .build()

        val presignRequest = GetObjectPresignRequest.builder()
            .signatureDuration(Duration.ofMinutes(expirationMinutes.toLong()))
            .getObjectRequest(getObjectRequest)
            .build()

        return s3Presigner.presignGetObject(presignRequest).url().toExternalForm()
    }

    override fun objectExists(key: String): Boolean = try {
        s3Client.headObject(
            HeadObjectRequest.builder()
                .bucket(storageProperties.bucket)
                .key(key)
                .build(),
        )
        true
    } catch (_: NoSuchKeyException) {
        false
    }

    override fun getObjectMetadata(key: String): ObjectMetadata? = try {
        val response = s3Client.headObject(
            HeadObjectRequest.builder()
                .bucket(storageProperties.bucket)
                .key(key)
                .build(),
        )
        ObjectMetadata(
            contentLength = response.contentLength(),
            contentType = response.contentType(),
            eTag = response.eTag(),
        )
    } catch (_: NoSuchKeyException) {
        null
    }

    private fun validateStorageKey(key: String) {
        if (key.contains("..") || key.startsWith("/") || !key.matches(Regex("^[a-zA-Z0-9\\-_./]+$"))) {
            throw IllegalArgumentException("Invalid storage key")
        }
    }
}
