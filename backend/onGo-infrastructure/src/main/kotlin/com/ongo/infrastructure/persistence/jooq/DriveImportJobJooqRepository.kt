package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.contentsource.DriveImportJob
import com.ongo.domain.contentsource.DriveImportJobRepository
import com.ongo.domain.contentsource.DriveImportStatus
import com.ongo.infrastructure.persistence.jooq.Fields.BYTES_TRANSFERRED
import com.ongo.infrastructure.persistence.jooq.Fields.COMPLETED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.CONTENT_SOURCE_ID
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.DRIVE_FILE_ID
import com.ongo.infrastructure.persistence.jooq.Fields.DRIVE_FILE_NAME
import com.ongo.infrastructure.persistence.jooq.Fields.ERROR_MESSAGE
import com.ongo.infrastructure.persistence.jooq.Fields.FILE_SIZE_BYTES
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.RETRY_COUNT
import com.ongo.infrastructure.persistence.jooq.Fields.S3_KEY
import com.ongo.infrastructure.persistence.jooq.Fields.STARTED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS_TEXT
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Fields.VIDEO_ID
import com.ongo.infrastructure.persistence.jooq.Fields.enumValue
import com.ongo.infrastructure.persistence.jooq.Tables.DRIVE_IMPORT_JOBS
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.time.ZoneOffset

@Repository
class DriveImportJobJooqRepository(
    private val dsl: DSLContext,
) : DriveImportJobRepository {

    override fun findById(id: Long): DriveImportJob? =
        dsl.select().from(DRIVE_IMPORT_JOBS).where(ID.eq(id))
            .fetchOne()?.toDriveImportJob()

    override fun findByVideoId(videoId: Long): DriveImportJob? =
        dsl.select().from(DRIVE_IMPORT_JOBS).where(VIDEO_ID.eq(videoId))
            .fetchOne()?.toDriveImportJob()

    override fun findActiveByUserAndFileId(userId: Long, driveFileId: String): List<DriveImportJob> =
        dsl.select().from(DRIVE_IMPORT_JOBS)
            .where(USER_ID.eq(userId))
            .and(DRIVE_FILE_ID.eq(driveFileId))
            .and(STATUS_TEXT.`in`("PENDING", "DOWNLOADING", "COMPLETED"))
            .forUpdate()
            .fetch().map { it.toDriveImportJob() }

    override fun countActiveByUser(userId: Long): Int =
        dsl.selectCount().from(DRIVE_IMPORT_JOBS)
            .where(USER_ID.eq(userId))
            .and(STATUS_TEXT.`in`("PENDING", "DOWNLOADING"))
            .fetchOne(0, Int::class.java) ?: 0

    override fun listActiveByUser(userId: Long): List<DriveImportJob> =
        dsl.select().from(DRIVE_IMPORT_JOBS)
            .where(USER_ID.eq(userId))
            .and(STATUS_TEXT.`in`("PENDING", "DOWNLOADING"))
            .orderBy(CREATED_AT.desc())
            .fetch().map { it.toDriveImportJob() }

    override fun listStale(olderThanSeconds: Long): List<DriveImportJob> =
        dsl.select().from(DRIVE_IMPORT_JOBS)
            .where(STATUS_TEXT.`in`("PENDING", "DOWNLOADING"))
            .and(UPDATED_AT.lt(LocalDateTime.now().minusSeconds(olderThanSeconds)))
            .fetch().map { it.toDriveImportJob() }

    override fun save(job: DriveImportJob): DriveImportJob {
        val now = LocalDateTime.now()
        val id = if (job.id == 0L) {
            dsl.insertInto(DRIVE_IMPORT_JOBS)
                .set(VIDEO_ID, job.videoId)
                .set(USER_ID, job.userId)
                .set(CONTENT_SOURCE_ID, job.contentSourceId)
                .set(DRIVE_FILE_ID, job.driveFileId)
                .set(DRIVE_FILE_NAME, job.driveFileName)
                .set(FILE_SIZE_BYTES, job.fileSizeBytes)
                .set(BYTES_TRANSFERRED, job.bytesTransferred)
                .set(STATUS, enumValue("drive_import_status", job.status.name))
                .set(S3_KEY, job.s3Key)
                .set(ERROR_MESSAGE, job.errorMessage)
                .set(RETRY_COUNT, job.retryCount)
                .set(UPDATED_AT, now)
                .returningResult(ID).fetchOne()!!.get(ID)
        } else {
            job.id
        }
        return findById(id)!!
    }

    override fun updateStatus(id: Long, status: DriveImportStatus, errorMessage: String?) {
        val now = LocalDateTime.now()
        val q = dsl.update(DRIVE_IMPORT_JOBS)
            .set(STATUS, enumValue("drive_import_status", status.name))
            .set(ERROR_MESSAGE, errorMessage)
            .set(UPDATED_AT, now)
        // DOWNLOADING 첫 진입 시 started_at
        if (status == DriveImportStatus.DOWNLOADING) q.set(STARTED_AT, now)
        if (status.isTerminal()) q.set(COMPLETED_AT, now)
        q.where(ID.eq(id)).execute()
    }

    override fun updateProgress(id: Long, bytesTransferred: Long) {
        dsl.update(DRIVE_IMPORT_JOBS)
            .set(BYTES_TRANSFERRED, bytesTransferred)
            .set(UPDATED_AT, LocalDateTime.now())
            .where(ID.eq(id)).execute()
    }

    override fun markCompleted(id: Long, s3Key: String) {
        val now = LocalDateTime.now()
        dsl.update(DRIVE_IMPORT_JOBS)
            .set(STATUS, enumValue("drive_import_status", DriveImportStatus.COMPLETED.name))
            .set(S3_KEY, s3Key)
            .set(COMPLETED_AT, now)
            .set(UPDATED_AT, now)
            .where(ID.eq(id)).execute()
    }

    private fun Record.toDriveImportJob(): DriveImportJob {
        val statusStr = get(STATUS) ?: "PENDING"
        return DriveImportJob(
            id = get(ID),
            videoId = get(VIDEO_ID),
            userId = get(USER_ID),
            contentSourceId = get(CONTENT_SOURCE_ID),
            driveFileId = get(DRIVE_FILE_ID),
            driveFileName = get(DRIVE_FILE_NAME),
            fileSizeBytes = get(FILE_SIZE_BYTES),
            bytesTransferred = get(BYTES_TRANSFERRED),
            status = try {
                DriveImportStatus.valueOf(statusStr)
            } catch (_: Exception) {
                DriveImportStatus.PENDING
            },
            s3Key = get(S3_KEY),
            errorMessage = get(ERROR_MESSAGE),
            retryCount = get(RETRY_COUNT),
            startedAt = localDateTime(STARTED_AT)?.atZone(ZoneOffset.UTC)?.toInstant(),
            completedAt = localDateTime(COMPLETED_AT)?.atZone(ZoneOffset.UTC)?.toInstant(),
            createdAt = localDateTime(CREATED_AT)!!.atZone(ZoneOffset.UTC).toInstant(),
            updatedAt = localDateTime(UPDATED_AT)!!.atZone(ZoneOffset.UTC).toInstant(),
        )
    }
}
