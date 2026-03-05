package com.ongo.infrastructure.external.platform

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.core.io.FileSystemResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration

/**
 * S3 등 외부 URL에서 파일을 다운로드하고, 플랫폼 업로드 URL로 전송하는 헬퍼.
 */
@Component
class PlatformFileTransferHelper(
    private val objectMapper: ObjectMapper,
) {

    private val log = LoggerFactory.getLogger(PlatformFileTransferHelper::class.java)

    private val transferClient: RestClient = RestClient.builder()
        .requestFactory(createUploadRequestFactory())
        .build()

    /**
     * URL에서 파일을 다운로드하여 임시 파일로 저장.
     */
    fun downloadToTempFile(url: String): Path {
        log.debug("파일 다운로드 시작: {}", url.take(80))
        val tempFile = Files.createTempFile("ongo-upload-", ".tmp")
        try {
            URI.create(url).toURL().openStream().use { input ->
                Files.copy(input, tempFile, StandardCopyOption.REPLACE_EXISTING)
            }
            val size = Files.size(tempFile)
            log.debug("파일 다운로드 완료: {} bytes", size)
            return tempFile
        } catch (e: Exception) {
            Files.deleteIfExists(tempFile)
            throw e
        }
    }

    /**
     * YouTube Resumable Upload: 세션 URI 획득 (POST metadata → Location 헤더).
     */
    fun initiateYouTubeResumableUpload(
        uploadBaseUrl: String,
        metadata: Any,
        accessToken: String,
        fileSize: Long,
    ): String {
        log.debug("YouTube resumable upload 세션 요청")

        val metadataJson = objectMapper.writeValueAsString(metadata)

        val response = transferClient.post()
            .uri("$uploadBaseUrl/upload/youtube/v3/videos?uploadType=resumable&part=snippet,status")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .header("X-Upload-Content-Type", "video/*")
            .header("X-Upload-Content-Length", fileSize.toString())
            .body(metadataJson)
            .retrieve()
            .toBodilessEntity()

        val sessionUri = response.headers.location?.toString()
            ?: throw IllegalStateException("YouTube resumable upload: Location 헤더가 없습니다")

        log.debug("YouTube 세션 URI 획득 완료")
        return sessionUri
    }

    /**
     * YouTube: 세션 URI에 전체 파일을 단일 PUT으로 업로드.
     * 응답 body에 video resource JSON이 담겨 있어 videoId를 추출.
     */
    fun uploadToYouTubeSession(sessionUri: String, filePath: Path): String {
        val fileSize = Files.size(filePath)
        log.info("YouTube 파일 업로드: {} bytes → session", fileSize)

        val responseBody = transferClient.put()
            .uri(sessionUri)
            .header(HttpHeaders.CONTENT_TYPE, "video/*")
            .header(HttpHeaders.CONTENT_LENGTH, fileSize.toString())
            .body(FileSystemResource(filePath))
            .retrieve()
            .body(String::class.java)

        val videoId = try {
            val tree = objectMapper.readTree(responseBody)
            tree.get("id")?.asText()
        } catch (_: Exception) {
            null
        }

        log.info("YouTube 업로드 완료: videoId={}", videoId)
        return videoId ?: throw IllegalStateException("YouTube 응답에서 videoId를 추출할 수 없습니다")
    }

    /**
     * TikTok: uploadUrl에 파일을 청크 단위로 PUT 업로드.
     * Content-Range: bytes {start}-{end}/{total} 형식.
     */
    fun uploadChunkedToTikTok(uploadUrl: String, filePath: Path, chunkSize: Long) {
        val fileSize = Files.size(filePath)
        var offset = 0L

        log.info("TikTok 청크 업로드 시작: {} bytes, chunkSize={}", fileSize, chunkSize)

        Files.newInputStream(filePath).use { input ->
            while (offset < fileSize) {
                val end = minOf(offset + chunkSize, fileSize) - 1
                val currentChunkSize = (end - offset + 1).toInt()
                val chunk = input.readNBytes(currentChunkSize)

                val contentRange = "bytes $offset-$end/$fileSize"
                log.debug("TikTok 청크 업로드: {}", contentRange)

                transferClient.put()
                    .uri(uploadUrl)
                    .header(HttpHeaders.CONTENT_TYPE, "video/mp4")
                    .header(HttpHeaders.CONTENT_LENGTH, currentChunkSize.toString())
                    .header("Content-Range", contentRange)
                    .body(chunk)
                    .retrieve()
                    .toBodilessEntity()

                offset = end + 1
            }
        }

        log.info("TikTok 청크 업로드 완료")
    }

    /**
     * Naver Clip: uploadUrl에 전체 파일을 단일 PUT 업로드.
     */
    fun uploadToNaverClip(uploadUrl: String, filePath: Path, authHeader: String) {
        val fileSize = Files.size(filePath)
        log.info("Naver Clip 파일 업로드: {} bytes", fileSize)

        transferClient.put()
            .uri(uploadUrl)
            .header(HttpHeaders.CONTENT_TYPE, "video/mp4")
            .header(HttpHeaders.CONTENT_LENGTH, fileSize.toString())
            .header(HttpHeaders.AUTHORIZATION, authHeader)
            .body(FileSystemResource(filePath))
            .retrieve()
            .toBodilessEntity()

        log.info("Naver Clip 파일 업로드 완료")
    }

    /**
     * 임시 파일 정리.
     */
    fun cleanupTempFile(path: Path?) {
        if (path != null) {
            try {
                Files.deleteIfExists(path)
            } catch (e: Exception) {
                log.warn("임시 파일 삭제 실패: {}", e.message)
            }
        }
    }

    companion object {
        private fun createUploadRequestFactory(): SimpleClientHttpRequestFactory {
            return SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(Duration.ofSeconds(10))
                setReadTimeout(Duration.ofMinutes(30))
            }
        }
    }
}
