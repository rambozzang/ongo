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
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest
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

    // ── 멀티파트 ────────────────────────────────────────────────────────────

    override val supportsMultipart: Boolean get() = true

    override fun createMultipartUpload(key: String, contentType: String): String {
        validateStorageKey(key)
        val response = s3Client.createMultipartUpload(
            CreateMultipartUploadRequest.builder()
                .bucket(storageProperties.bucket)
                .key(key)
                .contentType(contentType)
                .build(),
        )
        return response.uploadId()
    }

    override fun presignUploadPart(
        key: String,
        uploadId: String,
        partNumber: Int,
        contentLength: Long,
        expirationMinutes: Int,
    ): String {
        validateStorageKey(key)
        require(contentLength > 0) { "조각 크기가 올바르지 않습니다." }
        // 단일 PUT 과 같은 방어다. contentLength 가 서명 대상 헤더가 되어, 계획과 다른 크기의
        // 조각은 스토리지가 서명 불일치로 거부한다(S3MultipartPresignContractTest 가 고정).
        val uploadPartRequest = UploadPartRequest.builder()
            .bucket(storageProperties.bucket)
            .key(key)
            .uploadId(uploadId)
            .partNumber(partNumber)
            .contentLength(contentLength)
            .build()

        return s3Presigner.presignUploadPart(
            UploadPartPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(expirationMinutes.toLong()))
                .uploadPartRequest(uploadPartRequest)
                .build(),
        ).url().toExternalForm()
    }

    /**
     * 올라온 조각 전체. ListParts 는 한 번에 최대 1,000 개라 페이지를 끝까지 넘긴다 —
     * 2 GB 는 128 조각이라 한 번이면 되지만, 조각 수가 늘면 앞 1,000 개만 보고 "빠졌다" 고
     * 판정하게 된다.
     */
    override fun listUploadedParts(key: String, uploadId: String): List<UploadedPart> {
        validateStorageKey(key)
        val parts = mutableListOf<UploadedPart>()
        var marker: Int? = null
        do {
            val response = s3Client.listParts(
                ListPartsRequest.builder()
                    .bucket(storageProperties.bucket)
                    .key(key)
                    .uploadId(uploadId)
                    .apply { marker?.let { partNumberMarker(it) } }
                    .build(),
            )
            response.parts().forEach { parts += UploadedPart(it.partNumber(), it.eTag(), it.size()) }
            marker = response.nextPartNumberMarker()
        } while (response.isTruncated == true)
        return parts
    }

    override fun completeMultipartUpload(key: String, uploadId: String, parts: List<UploadedPart>) {
        validateStorageKey(key)
        s3Client.completeMultipartUpload(
            CompleteMultipartUploadRequest.builder()
                .bucket(storageProperties.bucket)
                .key(key)
                .uploadId(uploadId)
                .multipartUpload(
                    CompletedMultipartUpload.builder()
                        .parts(
                            parts.sortedBy { it.partNumber }.map {
                                CompletedPart.builder().partNumber(it.partNumber).eTag(it.eTag).build()
                            },
                        )
                        .build(),
                )
                .build(),
        )
    }

    override fun abortMultipartUpload(key: String, uploadId: String) {
        validateStorageKey(key)
        try {
            s3Client.abortMultipartUpload(
                AbortMultipartUploadRequest.builder()
                    .bucket(storageProperties.bucket)
                    .key(key)
                    .uploadId(uploadId)
                    .build(),
            )
        } catch (_: NoSuchUploadException) {
            // 이미 완료됐거나 중단됐다. 목표 상태(세션 없음)에 이미 도달했으므로 성공으로 본다.
        }
    }

    override fun abortMultipartUploads(prefix: String): Int {
        validateStorageKey(prefix)
        var aborted = 0
        var keyMarker: String? = null
        var uploadIdMarker: String? = null
        do {
            val response = s3Client.listMultipartUploads(
                ListMultipartUploadsRequest.builder()
                    .bucket(storageProperties.bucket)
                    .prefix(prefix)
                    .apply { keyMarker?.let { keyMarker(it) } }
                    .apply { uploadIdMarker?.let { uploadIdMarker(it) } }
                    .build(),
            )
            response.uploads().forEach { upload ->
                abortMultipartUpload(upload.key(), upload.uploadId())
                aborted++
            }
            keyMarker = response.nextKeyMarker()
            uploadIdMarker = response.nextUploadIdMarker()
        } while (response.isTruncated == true)
        if (aborted > 0) log.info("미완료 멀티파트 업로드 {}건 중단: prefix={}", aborted, prefix)
        return aborted
    }

    private fun validateStorageKey(key: String) {
        if (key.contains("..") || key.startsWith("/") || !key.matches(Regex("^[a-zA-Z0-9\\-_./]+$"))) {
            throw IllegalArgumentException("Invalid storage key")
        }
    }
}
