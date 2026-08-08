package com.ongo.domain.video

import com.ongo.common.enums.Platform
import java.time.LocalDateTime

interface VideoUploadRepository {
    fun findById(id: Long): VideoUpload?
    fun findByVideoId(videoId: Long): List<VideoUpload>
    fun findByVideoIds(videoIds: List<Long>): Map<Long, List<VideoUpload>>
    fun findByVideoIdAndPlatform(videoId: Long, platform: Platform): VideoUpload?
    fun findByPlatformAndUserId(platform: Platform, userId: Long): List<VideoUpload>
    fun save(upload: VideoUpload): VideoUpload
    fun update(upload: VideoUpload): VideoUpload
    /** lease를 보유한 작업자만 외부 호출 결과를 반영한다. */
    fun updateOwned(upload: VideoUpload, owner: String): Boolean
    fun findPendingUploads(): List<VideoUpload>
    fun findDueScheduledUploads(now: LocalDateTime): List<VideoUpload>
    /** 외부 플랫폼의 비동기 처리를 확인할 polling 대상. */
    fun findDueProcessingUploads(now: LocalDateTime): List<VideoUpload>
    /** 외부 호출 직전에 한 작업자만 원자적으로 lease를 획득한다. */
    fun claim(id: Long, owner: String, now: LocalDateTime, leaseUntil: LocalDateTime): VideoUpload?
    /** lease가 만료된 작업은 외부 재전송 없이 결과 확인 필요 상태로 보낸다. */
    fun recoverExpiredLeases(now: LocalDateTime): List<VideoUpload>
    fun findByUserId(userId: Long): List<VideoUpload>
}
