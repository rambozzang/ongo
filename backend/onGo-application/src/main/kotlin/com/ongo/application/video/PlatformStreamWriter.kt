package com.ongo.application.video

import com.ongo.domain.video.VideoPlatformMeta
import java.time.LocalDateTime

/**
 * 플랫폼별 스트리밍 업로드 핸들러 인터페이스.
 * 구현체는 stateful — 하나의 업로드 세션당 하나의 인스턴스.
 */
interface PlatformStreamWriter {
    /**
     * 플랫폼 API에 업로드 세션을 초기화하고 세션 ID(또는 로깅용 식별자)를 반환.
     * @param scheduledAt null이면 즉시 게시, 값이 있으면 플랫폼 네이티브 예약 게시
     */
    fun initSession(
        meta: VideoPlatformMeta,
        accessToken: String,
        platformChannelId: String?,
        fileSize: Long,
        scheduledAt: LocalDateTime? = null,
    ): String

    /** 256KB 청크를 수신하여 플랫폼으로 전송 */
    fun writeChunk(chunk: ByteArray, offset: Long, totalSize: Long)
    /** 업로드를 완료하고 결과를 반환 */
    fun complete(): PlatformUploadResult
}
