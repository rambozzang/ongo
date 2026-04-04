package com.ongo.infrastructure.external.platform

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.Duration

/**
 * 플랫폼 업로드 URL로 파일 데이터를 전송하는 헬퍼.
 * ByteArray 기반으로 동작 — S3 다운로드 및 임시 파일 불필요.
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
     * YouTube: 세션 URI에 ByteArray 전체를 단일 PUT으로 업로드.
     * 응답 body에 video resource JSON이 담겨 있어 videoId를 추출.
     */
    fun uploadToYouTubeSession(sessionUri: String, data: ByteArray): String {
        log.info("YouTube 파일 업로드: {} bytes → session", data.size)

        val responseBody = transferClient.put()
            .uri(sessionUri)
            .header(HttpHeaders.CONTENT_TYPE, "video/*")
            .header(HttpHeaders.CONTENT_LENGTH, data.size.toString())
            .body(data)
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
     * TikTok: uploadUrl에 ByteArray를 청크 단위로 PUT 업로드.
     * Content-Range: bytes {start}-{end}/{total} 형식.
     */
    fun uploadChunkedToTikTok(uploadUrl: String, data: ByteArray, chunkSize: Long) {
        val fileSize = data.size.toLong()
        var offset = 0L

        log.info("TikTok 청크 업로드 시작: {} bytes, chunkSize={}", fileSize, chunkSize)

        while (offset < fileSize) {
            val end = minOf(offset + chunkSize, fileSize) - 1
            val chunk = data.copyOfRange(offset.toInt(), (end + 1).toInt())
            val contentRange = "bytes $offset-$end/$fileSize"

            log.debug("TikTok 청크 업로드: {}", contentRange)

            transferClient.put()
                .uri(uploadUrl)
                .header(HttpHeaders.CONTENT_TYPE, "video/mp4")
                .header(HttpHeaders.CONTENT_LENGTH, chunk.size.toString())
                .header("Content-Range", contentRange)
                .body(chunk)
                .retrieve()
                .toBodilessEntity()

            offset = end + 1
        }

        log.info("TikTok 청크 업로드 완료")
    }

    /**
     * Naver Clip: uploadUrl에 ByteArray 전체를 단일 PUT 업로드.
     */
    fun uploadToNaverClip(uploadUrl: String, data: ByteArray, authHeader: String) {
        log.info("Naver Clip 파일 업로드: {} bytes", data.size)

        transferClient.put()
            .uri(uploadUrl)
            .header(HttpHeaders.CONTENT_TYPE, "video/mp4")
            .header(HttpHeaders.CONTENT_LENGTH, data.size.toString())
            .header(HttpHeaders.AUTHORIZATION, authHeader)
            .body(data)
            .retrieve()
            .toBodilessEntity()

        log.info("Naver Clip 파일 업로드 완료")
    }

    /**
     * Twitter Media Upload APPEND: 미디어 데이터를 청크 단위로 전송.
     * multipart/form-data 형태로 command, media_id, segment_index, media_data(binary) 전송.
     */
    fun appendToTwitterMedia(
        uploadUrl: String,
        accessToken: String,
        mediaId: String,
        segmentIndex: Int,
        chunkData: ByteArray,
    ) {
        log.debug("Twitter APPEND: mediaId={}, segment={}, bytes={}", mediaId, segmentIndex, chunkData.size)

        val boundary = "----OnGoTwitterUpload${System.nanoTime()}"
        val body = buildTwitterAppendMultipart(boundary, mediaId, segmentIndex, chunkData)

        transferClient.post()
            .uri(uploadUrl)
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            .header(HttpHeaders.CONTENT_TYPE, "multipart/form-data; boundary=$boundary")
            .body(body)
            .retrieve()
            .toBodilessEntity()
    }

    private fun buildTwitterAppendMultipart(
        boundary: String,
        mediaId: String,
        segmentIndex: Int,
        chunkData: ByteArray,
    ): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val crlf = "\r\n"

        fun writePart(name: String, value: String) {
            output.write("--$boundary$crlf".toByteArray())
            output.write("Content-Disposition: form-data; name=\"$name\"$crlf".toByteArray())
            output.write(crlf.toByteArray())
            output.write(value.toByteArray())
            output.write(crlf.toByteArray())
        }

        writePart("command", "APPEND")
        writePart("media_id", mediaId)
        writePart("segment_index", segmentIndex.toString())

        // Binary part
        output.write("--$boundary$crlf".toByteArray())
        output.write("Content-Disposition: form-data; name=\"media\"; filename=\"chunk.mp4\"$crlf".toByteArray())
        output.write("Content-Type: application/octet-stream$crlf".toByteArray())
        output.write(crlf.toByteArray())
        output.write(chunkData)
        output.write(crlf.toByteArray())
        output.write("--$boundary--$crlf".toByteArray())

        return output.toByteArray()
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
