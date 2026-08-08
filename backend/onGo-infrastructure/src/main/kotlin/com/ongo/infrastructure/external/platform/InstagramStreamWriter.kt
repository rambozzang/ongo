package com.ongo.infrastructure.external.platform

import com.ongo.application.video.PlatformStreamWriter
import com.ongo.application.video.PlatformStreamWriterFactory
import com.ongo.application.video.PlatformUploadResult
import com.ongo.common.enums.Platform
import com.ongo.domain.channel.PlainToken
import com.ongo.domain.video.VideoPlatformMeta
import com.ongo.infrastructure.external.storage.StorageClient
import com.ongo.infrastructure.external.instagram.InstagramClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.util.UUID

/**
 * Instagram Graph API는 공개 URL을 요구한다. 수신 청크를 임시 오브젝트에
 * 저장한 뒤 Graph API의 실제 컨테이너→폴링→게시 흐름으로 넘긴다.
 */
@Component
class InstagramStreamWriterFactory(
    private val instagramClient: InstagramClient,
    private val storageClient: StorageClient,
) : PlatformStreamWriterFactory {
    override val platform = Platform.INSTAGRAM
    override fun createWriter(): PlatformStreamWriter = InstagramStreamWriter(instagramClient, storageClient)
}

class InstagramStreamWriter(
    private val instagramClient: InstagramClient,
    private val storageClient: StorageClient,
) : PlatformStreamWriter {

    private val log = LoggerFactory.getLogger(javaClass)
    private val buffer = TempFileChunkBuffer("instagram")
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
        require(fileSize <= 500L * 1024 * 1024) { "Instagram 릴스는 500MB 이하만 지원합니다." }
        require(scheduledAt == null) { "Instagram 스트리밍 업로드는 예약 시각에 도달한 뒤 실행해야 합니다." }
        require(!platformChannelId.isNullOrBlank()) { "Instagram 사용자 ID가 필요합니다." }
        this.meta = meta
        this.accessToken = accessToken
        this.platformChannelId = platformChannelId
        return "instagram-stream-${UUID.randomUUID()}"
    }

    override fun writeChunk(chunk: ByteArray, offset: Long, totalSize: Long) {
        buffer.write(chunk, offset)
    }

    override fun complete(): PlatformUploadResult {
        val currentMeta = meta ?: throw IllegalStateException("initSession() 호출 필요")
        val token = accessToken ?: throw IllegalStateException("initSession() 호출 필요")
        val file = buffer.finish()
        val key = "temporary/platform-stream/instagram/${UUID.randomUUID()}.mp4"
        storageKey = key
        return try {
            val publicUrl = file.inputStream().buffered().use { input ->
                storageClient.uploadFile(key, input, "video/mp4", file.length())
            }
            val result = instagramClient.uploadVideo(
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
            PlatformUploadResult(
                success = true,
                platformVideoId = result.platformVideoId,
                platformUrl = result.platformUrl.ifBlank { null },
                published = result.status.equals("PUBLISHED", ignoreCase = true),
                pollToken = result.platformVideoId,
            )
        } catch (e: Exception) {
            log.error("Instagram 스트리밍 업로드 실패", e)
            PlatformUploadResult(success = false, errorMessage = e.message)
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
