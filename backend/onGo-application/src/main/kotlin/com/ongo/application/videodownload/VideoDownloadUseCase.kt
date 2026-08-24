package com.ongo.application.videodownload

import com.fasterxml.jackson.databind.ObjectMapper
import com.ongo.common.exception.BusinessException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.util.UUID

@Service
class VideoDownloadUseCase(
    private val sourceDownloader: VideoSourceDownloader,
    private val importedVideoPersister: ImportedVideoPersister,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 영상 URL 임포트를 지금 쓸 수 있는지 알려준다.
     *
     * 추출기 바이너리는 배포 전제라 JVM 밖에 있다. 화면이 진입점을 감추거나 비활성화할 수
     * 있게 미리 알려주려는 것이다. 그래야 "버튼은 있는데 누르면 실패"가 안 생긴다.
     */
    fun checkAvailability(): DownloaderAvailability = sourceDownloader.checkAvailability()

    fun importVideo(userId: Long, request: VideoDownloadRequest): VideoDownloadResult {
        val source = VideoDownloadUrl.parse(request.url)
        val downloaded = try {
            sourceDownloader.download(source.canonical, source.provider)
        } catch (e: BusinessException) {
            throw e
        } catch (e: Exception) {
            log.warn("외부 영상 다운로드 실패: provider={}", source.provider, e)
            throw BusinessException("VIDEO_DOWNLOAD_FAILED", "소스 영상을 가져오지 못했습니다.")
        }

        try {
            validateDownloadedVideo(downloaded)

            val extension = extensionOf(downloaded.originalFilename)
            val key = "videos/$userId/imports/${UUID.randomUUID()}.$extension"

            val title = request.title?.trim()?.takeIf { it.isNotBlank() }
                ?: downloaded.title.trim().takeIf { it.isNotBlank() }
                ?: "가져온 영상"
            val sourceReference = objectMapper.createObjectNode().apply {
                put("provider", source.provider.name)
                put("url", source.original)
            }

            /*
             * 여기부터가 트랜잭션이다. 다운로드는 이미 끝났고, 한도 판정·업로드·행 저장만
             * 한 트랜잭션에 묶여 사용자 행 잠금이 확정 시점까지 유지된다. 다운로드까지 감싸면
             * 2GB 를 받는 몇 분 동안 잠금과 커넥션을 붙들게 되므로 경계를 여기에 둔다.
             */
            val video = importedVideoPersister.persist(
                userId = userId,
                downloaded = downloaded,
                objectKey = key,
                title = title.take(MAX_TITLE_LENGTH),
                originalFilename = downloaded.originalFilename.take(MAX_FILENAME_LENGTH),
                sourceReference = sourceReference,
                openStream = { Files.newInputStream(downloaded.path) },
            )

            return VideoDownloadResult(
                videoId = requireNotNull(video.id) { "가져온 영상 레코드 생성에 실패했습니다." },
                title = video.title,
                provider = source.provider,
                fileUrl = video.fileUrl,
            )
        } finally {
            runCatching { Files.deleteIfExists(downloaded.path) }
            // The yt-dlp adapter uses a private, prefixed temporary directory.
            // Only remove that known directory; never touch a caller-owned path.
            runCatching {
                downloaded.path.parent
                    ?.takeIf { it.fileName?.toString()?.startsWith("ongo-video-download-") == true }
                    ?.let { Files.deleteIfExists(it) }
            }
        }
    }

    private fun validateDownloadedVideo(downloaded: DownloadedVideo) {
        if (downloaded.size <= 0 || downloaded.size > MAX_FILE_SIZE_BYTES) {
            throw BusinessException("VIDEO_DOWNLOAD_SIZE_INVALID", "영상 크기는 1바이트 이상 2GB 이하여야 합니다.")
        }
        if (!Files.isRegularFile(downloaded.path)) {
            throw BusinessException("VIDEO_DOWNLOAD_FAILED", "다운로드된 영상 파일을 찾을 수 없습니다.")
        }
        if (Files.size(downloaded.path) != downloaded.size) {
            throw BusinessException("VIDEO_DOWNLOAD_FAILED", "다운로드된 영상 크기를 확인할 수 없습니다.")
        }
        if (!downloaded.contentType.lowercase().startsWith("video/")) {
            throw BusinessException("VIDEO_DOWNLOAD_TYPE_INVALID", "동영상 파일만 가져올 수 있습니다.")
        }
    }

    private fun extensionOf(filename: String): String {
        val extension = filename.substringAfterLast('.', "mp4")
            .lowercase()
            .replace(Regex("[^a-z0-9]"), "")
        return extension.takeIf { it in ALLOWED_EXTENSIONS } ?: "mp4"
    }

    companion object {
        const val MAX_FILE_SIZE_BYTES: Long = 2L * 1024 * 1024 * 1024
        private const val MAX_TITLE_LENGTH = 200
        private const val MAX_FILENAME_LENGTH = 500
        private val ALLOWED_EXTENSIONS = setOf("mp4", "mov", "webm", "mkv", "avi")
    }
}

data class VideoDownloadRequest(
    val url: String,
    val title: String? = null,
)

data class VideoDownloadResult(
    val videoId: Long,
    val title: String,
    val provider: com.ongo.domain.videodownload.VideoDownloadProvider,
    val fileUrl: String?,
)
