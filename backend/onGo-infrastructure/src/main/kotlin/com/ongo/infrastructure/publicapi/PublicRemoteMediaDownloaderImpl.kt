package com.ongo.infrastructure.publicapi

import com.ongo.application.publicapi.PublicDownloadedMedia
import com.ongo.application.publicapi.PublicRemoteMediaDownloader
import org.springframework.stereotype.Service
import java.net.InetAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.time.Duration

/**
 * 공개 API URL 업로드용 bounded downloader.
 *
 * 공개 API는 서버가 임의 URL을 읽게 만들기 때문에 내부망/loopback 주소, 인증 정보가
 * 포함된 URL, 리다이렉트를 허용하지 않는다. 응답도 2GB + 1바이트에서 중단한다.
 */
@Service
class PublicRemoteMediaDownloaderImpl : PublicRemoteMediaDownloader {
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    override fun download(url: String): PublicDownloadedMedia {
        val uri = validateUri(url)
        val request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(60))
            .header("Accept", "video/*,image/*,audio/*")
            .GET()
            .build()
        val response = runCatching {
            client.send(request, HttpResponse.BodyHandlers.ofInputStream())
        }.getOrElse { throw IllegalArgumentException("URL 미디어에 연결할 수 없습니다") }

        if (response.statusCode() !in 200..299) {
            response.body().use { it.close() }
            throw IllegalArgumentException("URL 미디어 다운로드가 HTTP ${response.statusCode()}로 실패했습니다")
        }
        val contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L)
        if (contentLength > MAX_FILE_SIZE) {
            response.body().use { it.close() }
            throw IllegalArgumentException("URL 미디어가 2GB 제한을 초과합니다")
        }

        val contentType = response.headers().firstValue("Content-Type").orElse(null)
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.isNotBlank() && it != "application/octet-stream" }
            ?: mimeTypeFromPath(uri.path)
            ?: run {
                response.body().use { it.close() }
                throw IllegalArgumentException("URL 응답의 미디어 MIME 타입을 확인할 수 없습니다")
            }
        val path = Files.createTempFile("ongo-public-url-", ".media")
        try {
            var size = 0L
            response.body().use { input ->
                Files.newOutputStream(path).buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        size += read
                        if (size > MAX_FILE_SIZE) {
                            throw IllegalArgumentException("URL 미디어가 2GB 제한을 초과합니다")
                        }
                        output.write(buffer, 0, read)
                    }
                }
            }
            return PublicDownloadedMedia(
                path = path,
                filename = safeFilename(uri.path, contentType),
                contentType = contentType,
                size = size,
            )
        } catch (error: Exception) {
            Files.deleteIfExists(path)
            throw error
        }
    }

    private fun validateUri(value: String): URI {
        val uri = runCatching { URI(value.trim()) }
            .getOrElse { throw IllegalArgumentException("URL 미디어 주소가 올바르지 않습니다") }
        require(uri.scheme == "https" || uri.scheme == "http") { "URL 미디어는 http 또는 https만 지원합니다" }
        require(uri.userInfo == null) { "URL 미디어 주소에 인증 정보는 사용할 수 없습니다" }
        require(uri.host != null && uri.host.isNotBlank()) { "URL 미디어 호스트가 필요합니다" }
        require(uri.port == -1 || uri.port == 80 || uri.port == 443) { "URL 미디어 포트는 80 또는 443만 지원합니다" }
        val addresses = runCatching { InetAddress.getAllByName(uri.host) }
            .getOrElse { throw IllegalArgumentException("URL 미디어 호스트를 확인할 수 없습니다") }
        require(addresses.isNotEmpty() && addresses.none(::isPrivateAddress)) {
            "내부망 또는 로컬 주소의 URL 미디어는 사용할 수 없습니다"
        }
        return uri
    }

    private fun isPrivateAddress(address: InetAddress): Boolean =
        address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress

    private fun safeFilename(path: String, contentType: String): String {
        val candidate = path.substringAfterLast('/').substringBefore('?')
            .takeIf { it.isNotBlank() && it.length <= 255 && !it.contains("..") }
        return candidate ?: "media.${extensionFor(contentType)}"
    }

    private fun mimeTypeFromPath(path: String): String? = when (path.substringAfterLast('.', "").lowercase()) {
        "mp4" -> "video/mp4"
        "mov" -> "video/quicktime"
        "avi" -> "video/x-msvideo"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "ogg" -> "audio/ogg"
        else -> null
    }

    private fun extensionFor(contentType: String): String = when (contentType) {
        "video/mp4" -> "mp4"
        "video/quicktime" -> "mov"
        "video/webm" -> "webm"
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        "audio/mpeg" -> "mp3"
        "audio/wav" -> "wav"
        else -> "media"
    }

    companion object {
        private const val MAX_FILE_SIZE = 2L * 1024 * 1024 * 1024
        private const val DEFAULT_BUFFER_SIZE = 64 * 1024
    }
}
