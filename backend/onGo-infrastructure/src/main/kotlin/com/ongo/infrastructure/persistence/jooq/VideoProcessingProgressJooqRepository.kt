package com.ongo.infrastructure.persistence.jooq

import com.ongo.common.enums.Platform
import com.ongo.domain.video.VideoProcessingProgressRepository
import com.ongo.domain.video.entity.ProcessingStage
import com.ongo.domain.video.entity.VideoProcessingProgress
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.MESSAGE
import com.ongo.infrastructure.persistence.jooq.Fields.PLATFORM
import com.ongo.infrastructure.persistence.jooq.Fields.PROGRESS_PERCENT
import com.ongo.infrastructure.persistence.jooq.Fields.STAGE
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.VIDEO_ID
import com.ongo.infrastructure.persistence.jooq.Tables.VIDEO_PROCESSING_PROGRESS
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository

@Repository
class VideoProcessingProgressJooqRepository(
    private val dsl: DSLContext,
) : VideoProcessingProgressRepository {

    override fun save(progress: VideoProcessingProgress): VideoProcessingProgress {
        val id = dsl.insertInto(VIDEO_PROCESSING_PROGRESS)
            .set(VIDEO_ID, progress.videoId)
            .set(DSL.field("stage", String::class.java), progress.stage.name)
            .set(PLATFORM, progress.platform?.name)
            .set(PROGRESS_PERCENT, progress.progressPercent)
            .set(MESSAGE, progress.message)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return findById(id)!!
    }

    override fun upsertProgress(videoId: Long, stage: ProcessingStage, platform: Platform?, progressPercent: Int, message: String?) {
        val stageField = DSL.field("stage", String::class.java)
        dsl.insertInto(VIDEO_PROCESSING_PROGRESS)
            .set(VIDEO_ID, videoId)
            .set(stageField, stage.name)
            .set(PLATFORM, platform?.name)
            .set(PROGRESS_PERCENT, progressPercent)
            .set(MESSAGE, message)
            .onConflict(VIDEO_ID, stageField, PLATFORM)
            .doUpdate()
            .set(PROGRESS_PERCENT, progressPercent)
            .set(MESSAGE, message)
            .execute()
    }

    override fun findByVideoId(videoId: Long): List<VideoProcessingProgress> =
        dsl.select()
            .from(VIDEO_PROCESSING_PROGRESS)
            .where(VIDEO_ID.eq(videoId))
            .orderBy(CREATED_AT.asc())
            .fetch()
            .map { it.toProgress() }

    override fun deleteByVideoId(videoId: Long) {
        dsl.deleteFrom(VIDEO_PROCESSING_PROGRESS)
            .where(VIDEO_ID.eq(videoId))
            .execute()
    }

    private fun findById(id: Long): VideoProcessingProgress? =
        dsl.select()
            .from(VIDEO_PROCESSING_PROGRESS)
            .where(ID.eq(id))
            .fetchOne()
            ?.toProgress()

    private fun Record.toProgress(): VideoProcessingProgress {
        val stageStr = get(STAGE) ?: "PROBE"
        val platformStr = get(PLATFORM)
        return VideoProcessingProgress(
            id = get(ID),
            videoId = get(VIDEO_ID),
            stage = try { ProcessingStage.valueOf(stageStr) } catch (_: Exception) { ProcessingStage.PROBE },
            platform = platformStr?.let { try { Platform.valueOf(it) } catch (_: Exception) { null } },
            progressPercent = get(PROGRESS_PERCENT) ?: 0,
            message = get(MESSAGE),
            createdAt = localDateTime(CREATED_AT),
            updatedAt = localDateTime(UPDATED_AT),
        )
    }
}
