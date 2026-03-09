package com.ongo.infrastructure.external.platform

import com.ongo.application.video.PlatformStreamWriter
import com.ongo.application.video.PlatformStreamWriterFactory
import com.ongo.application.video.PlatformUploadResult
import com.ongo.common.enums.Platform
import com.ongo.domain.video.VideoPlatformMeta
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Instagram Graph API는 공개 URL이 필요해 직접 스트리밍 업로드 불가.
 * 향후 임시 presigned URL 방식으로 구현 예정.
 */
@Component
class InstagramStreamWriterFactory : PlatformStreamWriterFactory {
    override val platform = Platform.INSTAGRAM
    override fun createWriter(): PlatformStreamWriter = InstagramStreamWriter()
}

class InstagramStreamWriter : PlatformStreamWriter {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun initSession(
        meta: VideoPlatformMeta,
        accessToken: String,
        platformChannelId: String?,
        fileSize: Long,
    ): String {
        log.warn("Instagram 스트리밍 업로드는 현재 지원하지 않습니다. Instagram 단독 업로드를 사용하세요.")
        return "instagram-not-supported"
    }

    override fun writeChunk(chunk: ByteArray, offset: Long, totalSize: Long) {
        // no-op: Instagram은 스트리밍 불가
    }

    override fun complete(): PlatformUploadResult {
        return PlatformUploadResult(
            success = false,
            errorMessage = "Instagram 스트리밍 업로드는 현재 지원하지 않습니다. 500MB 이하 단독 업로드를 사용하세요.",
        )
    }
}
