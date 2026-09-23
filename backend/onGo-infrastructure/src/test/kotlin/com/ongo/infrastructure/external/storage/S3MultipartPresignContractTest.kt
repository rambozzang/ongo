package com.ongo.infrastructure.external.storage

import org.junit.jupiter.api.Test
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.model.UploadPartRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.Duration
import kotlin.test.assertTrue

/**
 * 조각 presigned URL 이 content-length 를 **서명 대상**으로 삼는지 SDK 에 직접 물어본다.
 *
 * `S3StorageClient.presignUploadPart` 는 서버가 계산한 조각 크기를 UploadPartRequest 에 넣는다.
 * 그 값이 서명에 포함돼야만 계획과 다른 크기의 조각을 스토리지가 거부한다 — 단일 PUT 의
 * 용량 우회 방어(`S3PresignedUploadContractTest`)를 멀티파트에서도 유지하는 근거다.
 * 네트워크 없이 presigner 출력만으로 확인한다.
 */
class S3MultipartPresignContractTest {

    private fun presign(contentLength: Long?): String =
        S3Presigner.builder()
            .region(Region.of("auto"))
            .endpointOverride(URI.create("https://example.r2.cloudflarestorage.com"))
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("ak", "sk")))
            .build()
            .use { p ->
                p.presignUploadPart(
                    UploadPartPresignRequest.builder()
                        .signatureDuration(Duration.ofMinutes(60))
                        .uploadPartRequest(
                            UploadPartRequest.builder()
                                .bucket("ongo-videos")
                                .key("videos/1/clip.mp4")
                                .uploadId("upload-1")
                                .partNumber(3)
                                .apply { contentLength?.let { contentLength(it) } }
                                .build(),
                        )
                        .build(),
                ).url().toExternalForm()
            }

    private fun signedHeaders(url: String): String =
        URLDecoder.decode(url, StandardCharsets.UTF_8)
            .substringAfter("X-Amz-SignedHeaders=").substringBefore('&')

    @Test
    fun `조각 크기가 서명 대상 헤더가 된다`() {
        val headers = signedHeaders(presign(16L * 1024 * 1024))
        assertTrue("content-length" in headers.split(';'), "content-length 가 서명에 없다: $headers")
    }

    /** 대조군 — 크기를 안 넣으면 서명에도 없다. 위 단언이 우연히 통과하는 것이 아님을 보인다. */
    @Test
    fun `크기를 넣지 않으면 서명에 없다`() {
        val headers = signedHeaders(presign(null))
        assertTrue("content-length" !in headers.split(';'), "대조군인데 content-length 가 있다: $headers")
    }

    @Test
    fun `조각 번호와 업로드 ID 가 URL 에 묶인다`() {
        val url = presign(16L * 1024 * 1024)
        assertTrue("partNumber=3" in url, url)
        assertTrue("uploadId=upload-1" in url, url)
    }
}
