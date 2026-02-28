package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.contentrepurposer.*
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.WORKSPACE_ID
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class RepurposeJobJooqRepository(
    private val dsl: DSLContext,
) : RepurposeJobRepository {

    companion object {
        private val TABLE = DSL.table("repurpose_jobs")
        private val ORIGINAL_TITLE = DSL.field("original_title", String::class.java)
        private val ORIGINAL_PLATFORM = DSL.field("original_platform", String::class.java)
        private val TARGET_PLATFORM = DSL.field("target_platform", String::class.java)
        private val TARGET_FORMAT = DSL.field("target_format", String::class.java)
        private val PROGRESS = DSL.field("progress", Int::class.java)
        private val OUTPUT_URL = DSL.field("output_url", String::class.java)
    }

    override fun findByWorkspaceId(workspaceId: Long): List<RepurposeJob> =
        dsl.select().from(TABLE).where(WORKSPACE_ID.eq(workspaceId))
            .orderBy(CREATED_AT.desc())
            .fetch().map { it.toJob() }

    override fun findByWorkspaceIdAndStatus(workspaceId: Long, status: String): List<RepurposeJob> =
        dsl.select().from(TABLE)
            .where(WORKSPACE_ID.eq(workspaceId))
            .and(STATUS.eq(status))
            .orderBy(CREATED_AT.desc())
            .fetch().map { it.toJob() }

    override fun save(job: RepurposeJob): RepurposeJob {
        val id = dsl.insertInto(TABLE)
            .set(WORKSPACE_ID, job.workspaceId)
            .set(ORIGINAL_TITLE, job.originalTitle)
            .set(ORIGINAL_PLATFORM, job.originalPlatform)
            .set(TARGET_PLATFORM, job.targetPlatform)
            .set(TARGET_FORMAT, job.targetFormat)
            .set(STATUS, job.status)
            .set(PROGRESS, job.progress)
            .set(OUTPUT_URL, job.outputUrl)
            .returningResult(ID)
            .fetchOne()!!.get(ID)

        return dsl.select().from(TABLE).where(ID.eq(id)).fetchOne()!!.toJob()
    }

    private fun Record.toJob(): RepurposeJob =
        RepurposeJob(
            id = get(ID),
            workspaceId = get(WORKSPACE_ID),
            originalTitle = get(ORIGINAL_TITLE),
            originalPlatform = get(ORIGINAL_PLATFORM),
            targetPlatform = get(TARGET_PLATFORM),
            targetFormat = get(TARGET_FORMAT),
            status = get(STATUS) ?: "PENDING",
            progress = get(PROGRESS) ?: 0,
            outputUrl = get(OUTPUT_URL),
            createdAt = localDateTime(CREATED_AT) ?: LocalDateTime.now(),
        )
}

@Repository
class RepurposeTemplateJooqRepository(
    private val dsl: DSLContext,
) : RepurposeTemplateRepository {

    companion object {
        private val TABLE = DSL.table("repurpose_templates")
        private val NAME = DSL.field("name", String::class.java)
        private val SOURCE_PLATFORM = DSL.field("source_platform", String::class.java)
        private val TARGET_PLATFORM = DSL.field("target_platform", String::class.java)
        private val TARGET_FORMAT = DSL.field("target_format", String::class.java)
        private val DESCRIPTION = DSL.field("description", String::class.java)
        private val IS_DEFAULT = DSL.field("is_default", Boolean::class.java)
    }

    override fun findByWorkspaceIdOrDefault(workspaceId: Long): List<RepurposeTemplate> =
        dsl.select().from(TABLE)
            .where(WORKSPACE_ID.eq(workspaceId).or(IS_DEFAULT.eq(true)))
            .orderBy(CREATED_AT.desc())
            .fetch().map { it.toTemplate() }

    private fun Record.toTemplate(): RepurposeTemplate =
        RepurposeTemplate(
            id = get(ID),
            workspaceId = get("workspace_id")?.let { (it as Number).toLong() },
            name = get(NAME),
            sourcePlatform = get(SOURCE_PLATFORM),
            targetPlatform = get(TARGET_PLATFORM),
            targetFormat = get(TARGET_FORMAT),
            description = get(DESCRIPTION),
            isDefault = get(IS_DEFAULT) ?: false,
            createdAt = localDateTime(CREATED_AT) ?: LocalDateTime.now(),
        )
}
