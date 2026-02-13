package com.ongo.infrastructure.external.storage

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetUrlRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import java.io.InputStream
import java.time.Duration

@Component
@Profile("production")
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
        val urlRequest = GetUrlRequest.builder()
            .bucket(storageProperties.bucket)
            .key(key)
            .build()

        return s3Client.utilities().getUrl(urlRequest).toExternalForm()
    }

    override fun deleteFile(key: String) {
        log.info("S3 파일 삭제: key={}", key)

        val deleteRequest = DeleteObjectRequest.builder()
            .bucket(storageProperties.bucket)
            .key(key)
            .build()

        s3Client.deleteObject(deleteRequest)
    }

    override fun generatePresignedUploadUrl(key: String, contentType: String, expirationMinutes: Int): String {
        log.debug("S3 presigned URL 생성: key={}, expiry={}분", key, expirationMinutes)

        val putObjectRequest = PutObjectRequest.builder()
            .bucket(storageProperties.bucket)
            .key(key)
            .contentType(contentType)
            .build()

        val presignRequest = PutObjectPresignRequest.builder()
            .signatureDuration(Duration.ofMinutes(expirationMinutes.toLong()))
            .putObjectRequest(putObjectRequest)
            .build()

        return s3Presigner.presignPutObject(presignRequest).url().toExternalForm()
    }
}
