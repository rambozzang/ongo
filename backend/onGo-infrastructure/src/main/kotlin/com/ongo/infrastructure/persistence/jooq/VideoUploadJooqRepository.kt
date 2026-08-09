package com.ongo.infrastructure.persistence.jooq

import com.ongo.common.enums.Platform
import com.ongo.common.enums.UploadStatus
import com.ongo.domain.video.VideoUpload
import com.ongo.domain.video.VideoUploadTarget
import com.ongo.domain.video.VideoUploadRepository
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.CHANNEL_ID
import com.ongo.infrastructure.persistence.jooq.Fields.ERROR_MESSAGE
import com.ongo.infrastructure.persistence.jooq.Fields.VIDEO_ATTEMPT_COUNT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.VIDEO_LAST_ERROR
import com.ongo.infrastructure.persistence.jooq.Fields.VIDEO_LEASE_OWNER
import com.ongo.infrastructure.persistence.jooq.Fields.VIDEO_LEASE_UNTIL
import com.ongo.infrastructure.persistence.jooq.Fields.VIDEO_NEXT_RETRY_AT
import com.ongo.infrastructure.persistence.jooq.Fields.PLATFORM
import com.ongo.infrastructure.persistence.jooq.Fields.PLATFORM_URL
import com.ongo.infrastructure.persistence.jooq.Fields.PLATFORM_VIDEO_ID
import com.ongo.infrastructure.persistence.jooq.Fields.PUBLISHED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.VIDEO_POLL_TOKEN
import com.ongo.infrastructure.persistence.jooq.Fields.VIDEO_SCHEDULED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS_TEXT
import com.ongo.infrastructure.persistence.jooq.Fields.PLATFORM_TEXT
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.VIDEO_ID
import com.ongo.infrastructure.persistence.jooq.Fields.enumValue
import com.ongo.infrastructure.persistence.jooq.Tables.VIDEO_UPLOADS
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class VideoUploadJooqRepository(
    private val dsl: DSLContext,
) : VideoUploadRepository {

    private companion object {
        const val MAX_ERROR_LENGTH = 2_000
    }

    override fun findById(id: Long): VideoUpload? =
        dsl.select()
            .from(VIDEO_UPLOADS)
            .where(ID.eq(id))
            .fetchOne()
            ?.toVideoUpload()

    override fun findByVideoId(videoId: Long): List<VideoUpload> =
        dsl.select()
            .from(VIDEO_UPLOADS)
            .where(VIDEO_ID.eq(videoId))
            .orderBy(CREATED_AT.asc())
            .fetch()
            .map { it.toVideoUpload() }

    override fun findByVideoIds(videoIds: List<Long>): Map<Long, List<VideoUpload>> {
        if (videoIds.isEmpty()) return emptyMap()
        return dsl.select()
            .from(VIDEO_UPLOADS)
            .where(VIDEO_ID.`in`(videoIds))
            .orderBy(CREATED_AT.asc())
            .fetch()
            .map { it.toVideoUpload() }
            .groupBy { it.videoId }
    }

    override fun findByVideoIdAndPlatform(videoId: Long, platform: Platform): VideoUpload? =
        dsl.select()
            .from(VIDEO_UPLOADS)
            .where(VIDEO_ID.eq(videoId))
            .and(PLATFORM_TEXT.eq(platform.name))
            .fetchOne()
            ?.toVideoUpload()

    override fun findByVideoIdAndChannelId(videoId: Long, channelId: Long): VideoUpload? =
        dsl.select()
            .from(VIDEO_UPLOADS)
            .where(VIDEO_ID.eq(videoId))
            .and(CHANNEL_ID.eq(channelId))
            .fetchOne()
            ?.toVideoUpload()

    override fun findByPlatformAndUserId(platform: Platform, userId: Long): List<VideoUpload> =
        dsl.select(
            DSL.field("video_uploads.id", Long::class.java).`as`("id"),
            DSL.field("video_uploads.video_id", Long::class.java).`as`("video_id"),
            DSL.field("video_uploads.channel_id", Long::class.java).`as`("channel_id"),
            DSL.field("video_uploads.platform", String::class.java).`as`("platform"),
            DSL.field("video_uploads.platform_video_id", String::class.java).`as`("platform_video_id"),
            DSL.field("video_uploads.status", String::class.java).`as`("status"),
            DSL.field("video_uploads.error_message", String::class.java).`as`("error_message"),
            DSL.field("video_uploads.platform_url", String::class.java).`as`("platform_url"),
            DSL.field("video_uploads.published_at", java.time.LocalDateTime::class.java).`as`("published_at"),
            DSL.field("video_uploads.created_at", java.time.LocalDateTime::class.java).`as`("created_at"),
            DSL.field("video_uploads.updated_at", java.time.LocalDateTime::class.java).`as`("updated_at"),
            DSL.field("video_uploads.attempt_count", Int::class.java).`as`("attempt_count"),
            DSL.field("video_uploads.next_retry_at", java.time.LocalDateTime::class.java).`as`("next_retry_at"),
            DSL.field("video_uploads.lease_owner", String::class.java).`as`("lease_owner"),
            DSL.field("video_uploads.lease_until", java.time.LocalDateTime::class.java).`as`("lease_until"),
            DSL.field("video_uploads.poll_token", String::class.java).`as`("poll_token"),
            DSL.field("video_uploads.last_error", String::class.java).`as`("last_error"),
            DSL.field("video_uploads.scheduled_at", java.time.LocalDateTime::class.java).`as`("scheduled_at")
        )
            .from(VIDEO_UPLOADS)
            .join(Tables.VIDEOS).on(
                DSL.field("video_uploads.video_id", Long::class.java)
                    .eq(DSL.field("videos.id", Long::class.java))
            )
            .where(DSL.field("video_uploads.platform", String::class.java).eq(platform.name))
            .and(DSL.field("videos.user_id", Long::class.java).eq(userId))
            .orderBy(DSL.field("video_uploads.created_at"))
            .fetch()
            .map { it.toVideoUpload() }

    override fun save(upload: VideoUpload): VideoUpload {
        val id = dsl.insertInto(VIDEO_UPLOADS)
            .set(VIDEO_ID, upload.videoId)
            .set(CHANNEL_ID, upload.channelId)
            .set(PLATFORM, enumValue("platform_type", upload.platform.name))
            .set(PLATFORM_VIDEO_ID, upload.platformVideoId)
            .set(STATUS, statusValue(upload.status))
            .set(ERROR_MESSAGE, upload.errorMessage?.take(MAX_ERROR_LENGTH))
            .set(PLATFORM_URL, upload.platformUrl)
            .set(VIDEO_ATTEMPT_COUNT, upload.attemptCount)
            .set(VIDEO_NEXT_RETRY_AT, upload.nextRetryAt)
            .set(VIDEO_LEASE_OWNER, upload.leaseOwner)
            .set(VIDEO_LEASE_UNTIL, upload.leaseUntil)
            .set(VIDEO_POLL_TOKEN, upload.pollToken)
            .set(VIDEO_LAST_ERROR, upload.lastError?.take(MAX_ERROR_LENGTH))
            .set(VIDEO_SCHEDULED_AT, upload.scheduledAt)
            .set(PUBLISHED_AT, upload.publishedAt)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return findById(id)!!
    }

    override fun update(upload: VideoUpload): VideoUpload {
        dsl.update(VIDEO_UPLOADS)
            .set(PLATFORM_VIDEO_ID, upload.platformVideoId)
            .set(CHANNEL_ID, upload.channelId)
            .set(STATUS, statusValue(upload.status))
            .set(ERROR_MESSAGE, upload.errorMessage?.take(MAX_ERROR_LENGTH))
            .set(PLATFORM_URL, upload.platformUrl)
            .set(VIDEO_ATTEMPT_COUNT, upload.attemptCount)
            .set(VIDEO_NEXT_RETRY_AT, upload.nextRetryAt)
            .set(VIDEO_LEASE_OWNER, upload.leaseOwner)
            .set(VIDEO_LEASE_UNTIL, upload.leaseUntil)
            .set(VIDEO_POLL_TOKEN, upload.pollToken)
            .set(VIDEO_LAST_ERROR, upload.lastError?.take(MAX_ERROR_LENGTH))
            .set(VIDEO_SCHEDULED_AT, upload.scheduledAt)
            .set(PUBLISHED_AT, upload.publishedAt)
            .where(ID.eq(upload.id))
            .execute()

        return findById(upload.id!!)!!
    }

    override fun deleteEditableByVideoIdExceptTargets(videoId: Long, targets: Set<VideoUploadTarget>): Int {
        val condition = VIDEO_ID.eq(videoId)
            .and(STATUS_TEXT.`in`(UploadStatus.DRAFT.name, UploadStatus.CANCELLED.name))
        val keepCondition = targets
            .map { target ->
                PLATFORM_TEXT.eq(target.platform.name).and(
                    target.channelId?.let { CHANNEL_ID.eq(it) } ?: CHANNEL_ID.isNull
                )
            }
            .reduceOrNull { left, right -> left.or(right) }
        val scoped = if (keepCondition == null) condition else condition.and(keepCondition.not())
        return dsl.deleteFrom(VIDEO_UPLOADS)
            .where(scoped)
            .execute()
    }

    override fun updateOwned(upload: VideoUpload, owner: String): Boolean {
        val changed = dsl.update(VIDEO_UPLOADS)
            .set(PLATFORM_VIDEO_ID, upload.platformVideoId)
            .set(CHANNEL_ID, upload.channelId)
            .set(STATUS, statusValue(upload.status))
            .set(ERROR_MESSAGE, upload.errorMessage?.take(MAX_ERROR_LENGTH))
            .set(PLATFORM_URL, upload.platformUrl)
            .set(VIDEO_ATTEMPT_COUNT, upload.attemptCount)
            .set(VIDEO_NEXT_RETRY_AT, upload.nextRetryAt)
            .set(VIDEO_LEASE_OWNER, upload.leaseOwner)
            .set(VIDEO_LEASE_UNTIL, upload.leaseUntil)
            .set(VIDEO_POLL_TOKEN, upload.pollToken)
            .set(VIDEO_LAST_ERROR, upload.lastError?.take(MAX_ERROR_LENGTH))
            .set(VIDEO_SCHEDULED_AT, upload.scheduledAt)
            .set(PUBLISHED_AT, upload.publishedAt)
            .where(ID.eq(upload.id))
            .and(VIDEO_LEASE_OWNER.eq(owner))
            .execute()
        return changed == 1
    }

    override fun findByUserId(userId: Long): List<VideoUpload> =
        dsl.select(
            DSL.field("video_uploads.id", Long::class.java).`as`("id"),
            DSL.field("video_uploads.video_id", Long::class.java).`as`("video_id"),
            DSL.field("video_uploads.channel_id", Long::class.java).`as`("channel_id"),
            DSL.field("video_uploads.platform", String::class.java).`as`("platform"),
            DSL.field("video_uploads.platform_video_id", String::class.java).`as`("platform_video_id"),
            DSL.field("video_uploads.status", String::class.java).`as`("status"),
            DSL.field("video_uploads.error_message", String::class.java).`as`("error_message"),
            DSL.field("video_uploads.platform_url", String::class.java).`as`("platform_url"),
            DSL.field("video_uploads.published_at", java.time.LocalDateTime::class.java).`as`("published_at"),
            DSL.field("video_uploads.created_at", java.time.LocalDateTime::class.java).`as`("created_at"),
            DSL.field("video_uploads.updated_at", java.time.LocalDateTime::class.java).`as`("updated_at"),
            DSL.field("video_uploads.attempt_count", Int::class.java).`as`("attempt_count"),
            DSL.field("video_uploads.next_retry_at", java.time.LocalDateTime::class.java).`as`("next_retry_at"),
            DSL.field("video_uploads.lease_owner", String::class.java).`as`("lease_owner"),
            DSL.field("video_uploads.lease_until", java.time.LocalDateTime::class.java).`as`("lease_until"),
            DSL.field("video_uploads.poll_token", String::class.java).`as`("poll_token"),
            DSL.field("video_uploads.last_error", String::class.java).`as`("last_error"),
            DSL.field("video_uploads.scheduled_at", java.time.LocalDateTime::class.java).`as`("scheduled_at")
        )
            .from(VIDEO_UPLOADS)
            .join(Tables.VIDEOS).on(
                DSL.field("video_uploads.video_id", Long::class.java)
                    .eq(DSL.field("videos.id", Long::class.java))
            )
            .where(DSL.field("videos.user_id", Long::class.java).eq(userId))
            .orderBy(DSL.field("video_uploads.created_at"))
            .fetch()
            .map { it.toVideoUpload() }

    override fun findPendingUploads(): List<VideoUpload> =
        dsl.select()
            .from(VIDEO_UPLOADS)
            .where(STATUS_TEXT.`in`(
                UploadStatus.UPLOADING.name,
                UploadStatus.PROCESSING.name,
                UploadStatus.UNCONFIRMED.name,
            ))
            .orderBy(CREATED_AT.asc())
            .fetch()
            .map { it.toVideoUpload() }

    override fun findDueScheduledUploads(now: LocalDateTime): List<VideoUpload> =
        dsl.select()
            .from(VIDEO_UPLOADS)
            .where(VIDEO_SCHEDULED_AT.isNotNull.and(VIDEO_SCHEDULED_AT.le(now)))
            .and(STATUS_TEXT.eq(UploadStatus.UPLOADING.name))
            .and(VIDEO_LEASE_UNTIL.isNull.or(VIDEO_LEASE_UNTIL.lt(now)))
            .orderBy(VIDEO_SCHEDULED_AT.asc())
            .fetch()
            .map { it.toVideoUpload() }

    override fun findDueRetryUploads(now: LocalDateTime): List<VideoUpload> =
        dsl.select()
            .from(VIDEO_UPLOADS)
            .where(STATUS_TEXT.eq(UploadStatus.UPLOADING.name))
            .and(VIDEO_SCHEDULED_AT.isNull)
            .and(VIDEO_NEXT_RETRY_AT.isNotNull.and(VIDEO_NEXT_RETRY_AT.le(now)))
            .and(VIDEO_LEASE_UNTIL.isNull.or(VIDEO_LEASE_UNTIL.lt(now)))
            .orderBy(VIDEO_NEXT_RETRY_AT.asc())
            .fetch()
            .map { it.toVideoUpload() }

    override fun findDueProcessingUploads(now: LocalDateTime): List<VideoUpload> =
        dsl.select()
            .from(VIDEO_UPLOADS)
            .where(STATUS_TEXT.eq(UploadStatus.PROCESSING.name))
            .and(VIDEO_POLL_TOKEN.isNotNull)
            .and(VIDEO_NEXT_RETRY_AT.isNull.or(VIDEO_NEXT_RETRY_AT.le(now)))
            .and(VIDEO_LEASE_UNTIL.isNull.or(VIDEO_LEASE_UNTIL.lt(now)))
            .orderBy(UPDATED_AT.asc())
            .fetch()
            .map { it.toVideoUpload() }

    override fun claim(id: Long, owner: String, now: LocalDateTime, leaseUntil: LocalDateTime): VideoUpload? {
        val changed = dsl.update(VIDEO_UPLOADS)
            .set(VIDEO_LEASE_OWNER, owner)
            .set(VIDEO_LEASE_UNTIL, leaseUntil)
            .set(VIDEO_ATTEMPT_COUNT, VIDEO_ATTEMPT_COUNT.plus(1))
            .where(ID.eq(id))
            .and(STATUS_TEXT.eq(UploadStatus.UPLOADING.name))
            .and(VIDEO_NEXT_RETRY_AT.isNull.or(VIDEO_NEXT_RETRY_AT.le(now)))
            .and(VIDEO_LEASE_UNTIL.isNull.or(VIDEO_LEASE_UNTIL.lt(now)))
            .execute()
        return if (changed == 1) findById(id) else null
    }

    override fun claimForStatusCheck(id: Long, owner: String, now: LocalDateTime, leaseUntil: LocalDateTime): VideoUpload? {
        val changed = dsl.update(VIDEO_UPLOADS)
            .set(VIDEO_LEASE_OWNER, owner)
            .set(VIDEO_LEASE_UNTIL, leaseUntil)
            .set(VIDEO_ATTEMPT_COUNT, VIDEO_ATTEMPT_COUNT.plus(1))
            .where(ID.eq(id))
            .and(STATUS_TEXT.`in`(UploadStatus.PROCESSING.name, UploadStatus.UNCONFIRMED.name))
            .and(VIDEO_NEXT_RETRY_AT.isNull.or(VIDEO_NEXT_RETRY_AT.le(now)))
            .and(VIDEO_LEASE_UNTIL.isNull.or(VIDEO_LEASE_UNTIL.lt(now)))
            .execute()
        return if (changed == 1) findById(id) else null
    }

    override fun claimForRetry(id: Long): Boolean =
        dsl.update(VIDEO_UPLOADS)
            .set(STATUS, statusValue(UploadStatus.UPLOADING))
            .set(ERROR_MESSAGE, null as String?)
            .set(VIDEO_NEXT_RETRY_AT, null as LocalDateTime?)
            .set(VIDEO_LEASE_OWNER, null as String?)
            .set(VIDEO_LEASE_UNTIL, null as LocalDateTime?)
            .set(VIDEO_POLL_TOKEN, null as String?)
            .set(VIDEO_LAST_ERROR, null as String?)
            .where(ID.eq(id))
            .and(STATUS_TEXT.`in`(UploadStatus.FAILED.name, UploadStatus.REJECTED.name))
            .execute() == 1

    override fun recoverExpiredLeases(now: LocalDateTime): List<VideoUpload> {
        val expired = dsl.select(ID)
            .from(VIDEO_UPLOADS)
            .where(VIDEO_LEASE_UNTIL.isNotNull.and(VIDEO_LEASE_UNTIL.lt(now)))
            .and(STATUS_TEXT.`in`(UploadStatus.UPLOADING.name, UploadStatus.PROCESSING.name))
            .fetch(ID)
        if (expired.isEmpty()) return emptyList()

        dsl.update(VIDEO_UPLOADS)
            .set(STATUS, DSL.`when`(VIDEO_POLL_TOKEN.isNotNull, statusValue(UploadStatus.PROCESSING))
                .else_(statusValue(UploadStatus.UNCONFIRMED)))
            .set(ERROR_MESSAGE, "작업 lease가 만료되어 게시 결과 확인이 필요합니다.")
            .set(VIDEO_LAST_ERROR, "작업자 lease 만료: $now")
            .set(VIDEO_NEXT_RETRY_AT, DSL.`when`(VIDEO_POLL_TOKEN.isNotNull, now).else_(null as LocalDateTime?))
            .set(VIDEO_LEASE_OWNER, null as String?)
            .set(VIDEO_LEASE_UNTIL, null as LocalDateTime?)
            .where(ID.`in`(expired))
            .and(VIDEO_LEASE_UNTIL.isNotNull.and(VIDEO_LEASE_UNTIL.lt(now)))
            .execute()
        return expired.mapNotNull { findById(it) }
    }

    override fun cancelScheduledUploads(videoId: Long, now: LocalDateTime): Int =
        dsl.update(VIDEO_UPLOADS)
            .set(STATUS, statusValue(UploadStatus.CANCELLED))
            .set(ERROR_MESSAGE, "예약 게시가 취소되었습니다.")
            .set(VIDEO_LAST_ERROR, "사용자가 예약 게시를 취소했습니다: $now")
            .set(VIDEO_NEXT_RETRY_AT, null as LocalDateTime?)
            .set(VIDEO_POLL_TOKEN, null as String?)
            .set(VIDEO_LEASE_OWNER, null as String?)
            .set(VIDEO_LEASE_UNTIL, null as LocalDateTime?)
            .where(VIDEO_ID.eq(videoId))
            .and(STATUS_TEXT.eq(UploadStatus.UPLOADING.name))
            .and(VIDEO_SCHEDULED_AT.isNotNull)
            .execute()

    override fun cancelScheduledUploadsByIds(uploadIds: Set<Long>, now: LocalDateTime): Int {
        if (uploadIds.isEmpty()) return 0
        return dsl.update(VIDEO_UPLOADS)
            .set(STATUS, statusValue(UploadStatus.CANCELLED))
            .set(ERROR_MESSAGE, "예약 게시가 취소되었습니다.")
            .set(VIDEO_LAST_ERROR, "사용자가 공개 API 예약 게시를 취소했습니다: $now")
            .set(VIDEO_NEXT_RETRY_AT, null as LocalDateTime?)
            .set(VIDEO_POLL_TOKEN, null as String?)
            .set(VIDEO_LEASE_OWNER, null as String?)
            .set(VIDEO_LEASE_UNTIL, null as LocalDateTime?)
            .where(ID.`in`(uploadIds))
            .and(STATUS_TEXT.eq(UploadStatus.UPLOADING.name))
            .and(VIDEO_SCHEDULED_AT.isNotNull)
            .execute()
    }

    override fun cancelScheduledUploadsByChannelId(channelId: Long, now: LocalDateTime): Int =
        dsl.update(VIDEO_UPLOADS)
            .set(STATUS, statusValue(UploadStatus.CANCELLED))
            .set(ERROR_MESSAGE, "채널 연동 해제로 예약 게시가 취소되었습니다.")
            .set(VIDEO_LAST_ERROR, "채널 연동 해제: $now")
            .set(VIDEO_NEXT_RETRY_AT, null as LocalDateTime?)
            .set(VIDEO_POLL_TOKEN, null as String?)
            .set(VIDEO_LEASE_OWNER, null as String?)
            .set(VIDEO_LEASE_UNTIL, null as LocalDateTime?)
            .where(CHANNEL_ID.eq(channelId))
            .and(STATUS_TEXT.eq(UploadStatus.UPLOADING.name))
            .and(VIDEO_SCHEDULED_AT.isNotNull)
            .execute()

    override fun rescheduleScheduledUploads(
        videoId: Long,
        scheduledAtByUploadId: Map<Long, LocalDateTime>,
    ): Int = scheduledAtByUploadId.entries.sumOf { (uploadId, scheduledAt) ->
        dsl.update(VIDEO_UPLOADS)
            .set(VIDEO_SCHEDULED_AT, scheduledAt)
            .set(VIDEO_NEXT_RETRY_AT, null as LocalDateTime?)
            .where(VIDEO_ID.eq(videoId))
            .and(ID.eq(uploadId))
            .and(STATUS_TEXT.eq(UploadStatus.UPLOADING.name))
            .and(VIDEO_SCHEDULED_AT.isNotNull)
            .execute()
    }

    private fun Record.toVideoUpload(): VideoUpload {
        val platformStr = get(PLATFORM) ?: "YOUTUBE"
        val statusStr = get(STATUS) ?: "DRAFT"
        return VideoUpload(
            id = get(ID),
            videoId = get(VIDEO_ID),
            platform = try { Platform.valueOf(platformStr) } catch (_: Exception) { Platform.YOUTUBE },
            channelId = get(CHANNEL_ID),
            platformVideoId = get(PLATFORM_VIDEO_ID),
            status = try { UploadStatus.valueOf(statusStr) } catch (_: Exception) { UploadStatus.DRAFT },
            errorMessage = get(ERROR_MESSAGE),
            platformUrl = get(PLATFORM_URL),
            attemptCount = get(VIDEO_ATTEMPT_COUNT) ?: 0,
            nextRetryAt = localDateTime(VIDEO_NEXT_RETRY_AT),
            leaseOwner = get(VIDEO_LEASE_OWNER),
            leaseUntil = localDateTime(VIDEO_LEASE_UNTIL),
            pollToken = get(VIDEO_POLL_TOKEN),
            lastError = get(VIDEO_LAST_ERROR),
            scheduledAt = localDateTime(VIDEO_SCHEDULED_AT),
            publishedAt = localDateTime(PUBLISHED_AT),
            createdAt = localDateTime(CREATED_AT),
            updatedAt = localDateTime(UPDATED_AT),
        )
    }

    /** PostgreSQL enum columns reject a VARCHAR bind on both predicates and assignments. */
    private fun statusValue(status: UploadStatus) =
        DSL.field("?::upload_status", String::class.java, status.name)
}
