package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.repurpose.RepurposeJob
import com.ongo.domain.repurpose.RepurposeJobRepository
import com.ongo.infrastructure.persistence.jooq.Fields.CLIP_COUNT
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Fields.VIDEO_ID
import com.ongo.infrastructure.persistence.jooq.Fields.VIDEO_TITLE
import com.ongo.infrastructure.persistence.jooq.Tables.REPURPOSE_JOBS
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository

@Repository
class RepurposeJobJooqRepository(
    private val dsl: DSLContext,
) : RepurposeJobRepository {

    override fun findById(id: Long): RepurposeJob? =
        dsl.select()
            .from(REPURPOSE_JOBS)
            .where(ID.eq(id))
            .fetchOne()
            ?.toRepurposeJob()

    override fun findByUserId(userId: Long): List<RepurposeJob> =
        dsl.select()
            .from(REPURPOSE_JOBS)
            .where(USER_ID.eq(userId))
            .orderBy(CREATED_AT.desc())
            .fetch()
            .map { it.toRepurposeJob() }

    override fun save(job: RepurposeJob): RepurposeJob {
        val id = dsl.insertInto(REPURPOSE_JOBS)
            .set(USER_ID, job.userId)
            .set(VIDEO_ID, job.videoId)
            .set(VIDEO_TITLE, job.videoTitle)
            .set(STATUS, job.status)
            .set(CLIP_COUNT, job.clipCount)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return findById(id)!!
    }

    override fun updateStatus(id: Long, status: String, clipCount: Int): RepurposeJob? {
        dsl.update(REPURPOSE_JOBS)
            .set(STATUS, status)
            .set(CLIP_COUNT, clipCount)
            .where(ID.eq(id))
            .execute()

        return findById(id)
    }

    private fun Record.toRepurposeJob() = RepurposeJob(
        id = get(ID),
        userId = get(USER_ID),
        videoId = get(VIDEO_ID),
        videoTitle = get(VIDEO_TITLE) ?: "",
        status = get(STATUS) ?: "PENDING",
        clipCount = get(CLIP_COUNT) ?: 0,
        createdAt = localDateTime(CREATED_AT) ?: java.time.LocalDateTime.now(),
    )
}
