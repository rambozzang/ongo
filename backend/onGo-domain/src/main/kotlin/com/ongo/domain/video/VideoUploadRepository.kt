package com.ongo.domain.video

import com.ongo.common.enums.Platform
import java.time.LocalDateTime

interface VideoUploadRepository {
    fun findById(id: Long): VideoUpload?
    fun findByVideoId(videoId: Long): List<VideoUpload>
    fun findByVideoIds(videoIds: List<Long>): Map<Long, List<VideoUpload>>
    fun findByVideoIdAndPlatform(videoId: Long, platform: Platform): VideoUpload?
    fun findByVideoIdAndChannelId(videoId: Long, channelId: Long): VideoUpload?
    fun findByPlatformAndUserId(platform: Platform, userId: Long): List<VideoUpload>
    fun save(upload: VideoUpload): VideoUpload
    fun update(upload: VideoUpload): VideoUpload
    /** DRAFT/CANCELLED 초안 행만 정리한다. 게시 중인 외부 결과는 절대 삭제하지 않는다. */
    fun deleteEditableByVideoIdExceptPlatforms(videoId: Long, platforms: Set<Platform>): Int
    /** lease를 보유한 작업자만 외부 호출 결과를 반영한다. */
    fun updateOwned(upload: VideoUpload, owner: String): Boolean
    fun findPendingUploads(): List<VideoUpload>
    fun findDueScheduledUploads(now: LocalDateTime): List<VideoUpload>
    /** 외부 플랫폼의 비동기 처리를 확인할 polling 대상. */
    fun findDueProcessingUploads(now: LocalDateTime): List<VideoUpload>
    /** 외부 호출 직전에 한 작업자만 원자적으로 lease를 획득한다. */
    fun claim(id: Long, owner: String, now: LocalDateTime, leaseUntil: LocalDateTime): VideoUpload?
    /** 이미 수락된 외부 작업의 상태 조회만 lease한다. 새 업로드를 시작하지 않는다. */
    fun claimForStatusCheck(id: Long, owner: String, now: LocalDateTime, leaseUntil: LocalDateTime): VideoUpload?
    /** 실패한 업로드를 한 번의 재시도 이벤트로 원자적으로 예약한다. */
    fun claimForRetry(id: Long): Boolean
    /** lease가 만료된 작업은 외부 재전송 없이 결과 확인 필요 상태로 보낸다. */
    fun recoverExpiredLeases(now: LocalDateTime): List<VideoUpload>
    /** 아직 외부 전송을 시작하지 않은 예약 업로드를 취소한다. */
    fun cancelScheduledUploads(videoId: Long, now: LocalDateTime): Int
    /** 화면에서 예약 시간을 옮길 때 durable queue의 실제 게시 시간도 갱신한다. */
    fun rescheduleScheduledUploads(videoId: Long, scheduledAtByUploadId: Map<Long, LocalDateTime>): Int
    fun findByUserId(userId: Long): List<VideoUpload>
}
