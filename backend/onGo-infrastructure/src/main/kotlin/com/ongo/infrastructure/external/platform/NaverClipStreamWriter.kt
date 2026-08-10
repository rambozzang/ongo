package com.ongo.infrastructure.external.platform

import com.ongo.application.video.PlatformStreamWriter
import com.ongo.application.video.PlatformStreamWriterFactory
import com.ongo.common.enums.Platform
import com.ongo.domain.channel.PlainToken
import com.ongo.domain.video.VideoPlatformMeta
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * Tombstone for legacy Naver Clip rows. Naver does not expose a public Clip
 * upload API, so this writer deliberately never creates an HTTP session.
 */
@Component
class NaverClipStreamWriterFactory : PlatformStreamWriterFactory {
    override val platform = Platform.NAVER_CLIP
    override fun createWriter(): PlatformStreamWriter = NaverClipStreamWriter()
}

class NaverClipStreamWriter : PlatformStreamWriter {
    override fun initSession(
        meta: VideoPlatformMeta,
        accessToken: PlainToken,
        platformChannelId: String?,
        fileSize: Long,
        scheduledAt: LocalDateTime?,
    ): String = throw UnsupportedOperationException(
        "Naver Clip은 공개 업로드 API를 제공하지 않아 onGo에서 업로드할 수 없습니다.",
    )

    override fun writeChunk(chunk: ByteArray, offset: Long, totalSize: Long) {
        throw IllegalStateException("Naver Clip 업로드 세션이 생성되지 않았습니다.")
    }

    override fun complete() = throw IllegalStateException("Naver Clip 업로드 세션이 생성되지 않았습니다.")

    override fun abort() = Unit
}
