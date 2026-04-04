package com.ongo.infrastructure.external.platform

import com.ongo.application.video.PlatformStreamWriter
import com.ongo.application.video.PlatformStreamWriterFactory
import com.ongo.application.video.PlatformUploadResult
import com.ongo.common.enums.Platform
import com.ongo.domain.video.VideoPlatformMeta
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * Threads Graph API는 공개 URL이 필요해 직접 스트리밍 업로드 불가.
 * Instagram과 동일한 제약 — 향후 임시 presigned URL 방식으로 구현 예정.
 */
@Component
class ThreadsStreamWriterFactory : PlatformStreamWriterFactory {
    override val platform = Platform.THREADS
    override fun createWriter(): PlatformStreamWriter = ThreadsStreamWriter()
}

class ThreadsStreamWriter : PlatformStreamWriter {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun initSession(
        meta: VideoPlatformMeta,
        accessToken: String,
        platformChannelId: String?,
        fileSize: Long,
        scheduledAt: LocalDateTime?,
    ): String {
        if (scheduledAt != null) {
            log.warn("Threads 예약 게시는 스트리밍 업로드에서 지원하지 않습니다. scheduledAt={} 무시됨", scheduledAt)
        }
        log.warn("Threads 스트리밍 업로드는 현재 지원하지 않습니다. Threads는 공개 비디오 URL이 필요합니다.")
        return "threads-not-supported"
    }

    override fun writeChunk(chunk: ByteArray, offset: Long, totalSize: Long) {
        // no-op: Threads는 스트리밍 불가
    }

    override fun complete(): PlatformUploadResult {
        return PlatformUploadResult(
            success = false,
            errorMessage = "Threads 스트리밍 업로드는 현재 지원하지 않습니다. Threads는 공개 비디오 URL이 필요합니다.",
        )
    }
}
