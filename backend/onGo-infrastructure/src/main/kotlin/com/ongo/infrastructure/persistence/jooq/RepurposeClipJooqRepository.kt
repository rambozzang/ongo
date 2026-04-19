package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.repurpose.RepurposeClip
import com.ongo.domain.repurpose.RepurposeClipRepository
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.DESCRIPTION
import com.ongo.infrastructure.persistence.jooq.Fields.END_TIME
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.JOB_ID
import com.ongo.infrastructure.persistence.jooq.Fields.REASONING
import com.ongo.infrastructure.persistence.jooq.Fields.START_TIME
import com.ongo.infrastructure.persistence.jooq.Fields.SUGGESTED_PLATFORM
import com.ongo.infrastructure.persistence.jooq.Fields.TITLE
import com.ongo.infrastructure.persistence.jooq.Fields.VIRAL_SCORE
import com.ongo.infrastructure.persistence.jooq.Tables.REPURPOSE_CLIPS
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository

@Repository
class RepurposeClipJooqRepository(
    private val dsl: DSLContext,
) : RepurposeClipRepository {

    override fun findByJobId(jobId: Long): List<RepurposeClip> =
        dsl.select()
            .from(REPURPOSE_CLIPS)
            .where(JOB_ID.eq(jobId))
            .orderBy(VIRAL_SCORE.desc())
            .fetch()
            .map { it.toRepurposeClip() }

    override fun saveAll(clips: List<RepurposeClip>): List<RepurposeClip> {
        if (clips.isEmpty()) return emptyList()

        val ids = clips.map { clip ->
            dsl.insertInto(REPURPOSE_CLIPS)
                .set(JOB_ID, clip.jobId)
                .set(START_TIME, clip.startTime)
                .set(END_TIME, clip.endTime)
                .set(TITLE, clip.title)
                .set(DESCRIPTION, clip.description)
                .set(VIRAL_SCORE, clip.viralScore)
                .set(REASONING, clip.reasoning)
                .set(SUGGESTED_PLATFORM, clip.suggestedPlatform)
                .returningResult(ID)
                .fetchOne()!!
                .get(ID)
        }

        return dsl.select()
            .from(REPURPOSE_CLIPS)
            .where(ID.`in`(ids))
            .orderBy(VIRAL_SCORE.desc())
            .fetch()
            .map { it.toRepurposeClip() }
    }

    private fun Record.toRepurposeClip() = RepurposeClip(
        id = get(ID),
        jobId = get(JOB_ID),
        startTime = get(START_TIME) ?: "",
        endTime = get(END_TIME) ?: "",
        title = get(TITLE) ?: "",
        description = get(DESCRIPTION) ?: "",
        viralScore = get(VIRAL_SCORE) ?: 0,
        reasoning = get(REASONING) ?: "",
        suggestedPlatform = get(SUGGESTED_PLATFORM) ?: "",
        createdAt = localDateTime(CREATED_AT) ?: java.time.LocalDateTime.now(),
    )
}
