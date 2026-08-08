package com.ongo.infrastructure.external.platform

import com.ongo.application.video.PlatformStreamWriter
import com.ongo.application.video.PlatformStreamWriterFactory
import com.ongo.application.video.PlatformUploadResult
import com.ongo.common.enums.Platform
import com.ongo.domain.channel.PlainToken
import com.ongo.domain.video.VideoPlatformMeta
import com.ongo.infrastructure.external.storage.StorageClient
import com.ongo.infrastructure.external.threads.ThreadsClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.util.UUID

/** Threads의 공개 URL 요구를 임시 오브젝트 저장으로 충족하고 실제 Graph API에 게시한다. */
@Component
class ThreadsStreamWriterFactory(
    private val threadsClient: ThreadsClient,
    private val storageClient: StorageClient,
) : PlatformStreamWriterFactory {
    override val platform = Platform.THREADS
    override fun createWriter(): PlatformStreamWriter = ThreadsStreamWriter(threadsClient, storageClient)
}

class ThreadsStreamWriter(
    private val threadsClient: ThreadsClient,
    private val storageClient: StorageClient,
) : PlatformStreamWriter {

    private val log = LoggerFactory.getLogger(javaClass)
    private val buffer = TempFileChunkBuffer("threads")
    private var meta: VideoPlatformMeta? = null
    private var accessToken: PlainToken? = null
    private var platformChannelId: String? = null
    private var storageKey: String? = null

    override fun initSession(
        meta: VideoPlatformMeta,
        accessToken: PlainToken,
        platformChannelId: String?,
        fileSize: Long,
        scheduledAt: LocalDateTime?,
    ): String {
        require(fileSize <= 500L * 1024 * 1024) { "Threads 영상은 500MB 이하만 지원합니다." }
        require(scheduledAt == null) { "Threads 스트리밍 업로드는 예약 시각에 도달한 뒤 실행해야 합니다." }
        require(!platformChannelId.isNullOrBlank()) { "Threads 사용자 ID가 필요합니다." }
        this.meta = meta
        this.accessToken = accessToken
        this.platformChannelId = platformChannelId
        return "threads-stream-${UUID.randomUUID()}"
    }

    override fun writeChunk(chunk: ByteArray, offset: Long, totalSize: Long) {
        buffer.write(chunk, offset)
    }

    override fun complete(): PlatformUploadResult {
        val currentMeta = meta ?: throw IllegalStateException("initSession() 호출 필요")
        val token = accessToken ?: throw IllegalStateException("initSession() 호출 필요")
        val file = buffer.finish()
        val key = "temporary/platform-stream/threads/${UUID.randomUUID()}.mp4"
        storageKey = key
        return try {
            val publicUrl = file.inputStream().buffered().use { input ->
                storageClient.uploadFile(key, input, "video/mp4", file.length())
            }
            val result = threadsClient.uploadVideo(
                PlatformUploadRequest(
                    fileUrl = publicUrl,
                    title = currentMeta.title ?: "Untitled",
                    description = currentMeta.description ?: "",
                    tags = currentMeta.tags,
                    visibility = currentMeta.visibility.name,
                    thumbnailUrl = currentMeta.customThumbnailUrl,
                    accessToken = token.value,
                    platformChannelId = platformChannelId,
                    fileSize = file.length(),
                )
            )
            val platformVideoId = result.platformVideoId?.takeIf { it.isNotBlank() }
            val platformUrl = result.platformUrl.takeIf { it.isNotBlank() }
            if (platformVideoId == null) {
                PlatformUploadResult(success = false, errorMessage = "Threads 게시 ID를 받지 못했습니다.")
            } else {
                PlatformUploadResult(
                    success = true,
                    platformVideoId = platformVideoId,
                    platformUrl = platformUrl,
                    published = result.status.equals("PUBLISHED", ignoreCase = true) && platformUrl != null,
                    pollToken = platformVideoId,
                )
            }
        } catch (e: Exception) {
            log.error("Threads 스트리밍 업로드 실패", e)
            uploadFailureResult(e)
        } finally {
            runCatching { storageClient.deleteFile(key) }
            storageKey = null
            buffer.cleanup()
        }
    }

    override fun abort() {
        storageKey?.let { runCatching { storageClient.deleteFile(it) } }
        buffer.cleanup()
    }

}
