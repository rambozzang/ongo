package com.ongo.infrastructure.external.storage

import io.mockk.mockk
import org.junit.jupiter.api.Test
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.Duration
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * presigned PUT 이 content-length 를 **서명 대상**으로 삼는지 SDK 에 직접 물어본다.
 *
 * S3StorageClient 는 신고 크기를 PutObjectRequest.contentLength 로 넣는데, 그 값이 서명에
 * 포함돼야만 다른 크기의 PUT 을 스토리지가 거부한다. 서명에 안 들어가면 그 방어는 존재하지
 * 않는 것이고 주석이 사실과 어긋나게 되므로, 네트워크 없이 SDK 의 presigner 출력만으로 확인한다.
 */
class S3PresignedUploadContractTest {

    private fun presigner(): S3Presigner = S3Presigner.builder()
        .region(Region.of("auto"))
        .endpointOverride(URI.create("https://example.r2.cloudflarestorage.com"))
        .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("ak", "sk")))
        .build()

    private fun presign(contentLength: Long?) = presigner().use { p ->
        p.presignPutObject(
            PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(60))
                .putObjectRequest(
                    PutObjectRequest.builder()
                        .bucket("ongo-videos")
                        .key("videos/1/clip.mp4")
                        .contentType("video/mp4")
                        .apply { contentLength?.let { contentLength(it) } }
                        .build(),
                )
                .build(),
        )
    }

    @Test
    fun `content length becomes a signed header so a different sized PUT is rejected`() {
        val presigned = presign(2_048L)

        val signedHeaderNames = presigned.signedHeaders().keys.map { it.lowercase() }
        assertTrue(
            signedHeaderNames.contains("content-length"),
            "content-length 가 서명 헤더에 없다. 서명 대상이 아니라면 크기 위조를 스토리지가 " +
                "막지 못하므로 S3StorageClient 의 주석을 정정하고 confirm 검증만 최종 방어로 " +
                "남겨야 한다. 실제 서명 헤더=$signedHeaderNames",
        )

        // 서명된 값이 우리가 요구한 크기여야 한다. 값이 다르면 어떤 크기든 통과한다.
        val signedLength = presigned.signedHeaders()
            .entries.first { it.key.equals("content-length", ignoreCase = true) }
            .value
        assertTrue(signedLength.contains("2048"), "서명된 content-length 가 요청 값과 다르다: $signedLength")
    }

    @Test
    fun `omitting content length leaves it out of the signature`() {
        // 대조군. 크기를 넣지 않으면 서명 대상에서 빠지는 것이 정상이고,
        // 위 테스트가 우연히 통과한 것이 아님을 보인다.
        val signedHeaderNames = presign(null).signedHeaders().keys.map { it.lowercase() }

        assertTrue(
            !signedHeaderNames.contains("content-length"),
            "크기를 넣지 않았는데 서명 헤더에 나타났다: $signedHeaderNames",
        )
    }

    /*
     * SDK 가 서명한다는 것만으로는 부족하다. 우리 어댑터가 실제로 그 값을 요청에 넣는지까지
     * 봐야 한다 — 넣지 않으면 서명 대상에서 빠져 크기 위조가 스토리지를 그대로 통과한다.
     */
    @Test
    fun `S3StorageClient puts the declared length into the signed request`() {
        val client = S3StorageClient(
            s3Client = mockk(relaxed = true),
            s3Presigner = presigner(),
            storageProperties = StorageProperties(bucket = "ongo-videos"),
        )

        val url = client.generatePresignedUploadUrl("videos/1/clip.mp4", "video/mp4", 2_048L, 60)

        val signedHeaders = URLDecoder.decode(url, StandardCharsets.UTF_8)
            .substringAfter("X-Amz-SignedHeaders=")
            .substringBefore("&")
        assertTrue(
            signedHeaders.contains("content-length"),
            "어댑터가 만든 URL 의 서명 헤더에 content-length 가 없다: $signedHeaders",
        )
    }

    @Test
    fun `S3StorageClient rejects a non positive declared length`() {
        val client = S3StorageClient(
            s3Client = mockk(relaxed = true),
            s3Presigner = presigner(),
            storageProperties = StorageProperties(bucket = "ongo-videos"),
        )

        assertFailsWith<IllegalArgumentException> {
            client.generatePresignedUploadUrl("videos/1/clip.mp4", "video/mp4", 0L, 60)
        }
    }
}
