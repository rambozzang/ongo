package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.ugc.shorts.PipelineStage
import com.ongo.domain.ugc.shorts.ShortsPrompt
import com.ongo.domain.ugc.shorts.ShortsPromptRepository
import com.ongo.domain.ugc.shorts.ShortsPromptRevision
import com.ongo.infrastructure.persistence.jooq.Fields.CHANGED_BY
import com.ongo.infrastructure.persistence.jooq.Fields.CHANGE_NOTE
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_BY
import com.ongo.infrastructure.persistence.jooq.Fields.DESCRIPTION
import com.ongo.infrastructure.persistence.jooq.Fields.EXECUTABLE
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.NAME
import com.ongo.infrastructure.persistence.jooq.Fields.PROMPT_ID
import com.ongo.infrastructure.persistence.jooq.Fields.REVISION
import com.ongo.infrastructure.persistence.jooq.Fields.STAGE
import com.ongo.infrastructure.persistence.jooq.Fields.SYSTEM_PROMPT
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.USER_PROMPT
import com.ongo.infrastructure.persistence.jooq.Fields.VERSION
import com.ongo.infrastructure.persistence.jooq.Fields.WORKSPACE_ID
import com.ongo.infrastructure.persistence.jooq.Tables.UGC_SHORTS_PROMPTS
import com.ongo.infrastructure.persistence.jooq.Tables.UGC_SHORTS_PROMPT_REVISIONS
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.time.ZoneOffset

@Repository
class ShortsPromptJooqRepository(
    private val dsl: DSLContext,
) : ShortsPromptRepository {

    override fun findDefaults(): List<ShortsPrompt> =
        dsl.select()
            .from(UGC_SHORTS_PROMPTS)
            .where(WORKSPACE_ID.isNull)
            .fetch()
            .map { it.toShortsPrompt() }

    override fun findDefaultByStage(stage: PipelineStage): ShortsPrompt? =
        dsl.select()
            .from(UGC_SHORTS_PROMPTS)
            .where(WORKSPACE_ID.isNull)
            .and(STAGE.eq(stage.name))
            .fetchOne()
            ?.toShortsPrompt()

    override fun findByWorkspace(workspaceId: Long): List<ShortsPrompt> =
        dsl.select()
            .from(UGC_SHORTS_PROMPTS)
            .where(WORKSPACE_ID.eq(workspaceId))
            .fetch()
            .map { it.toShortsPrompt() }

    override fun findByWorkspaceAndStage(workspaceId: Long, stage: PipelineStage): ShortsPrompt? =
        dsl.select()
            .from(UGC_SHORTS_PROMPTS)
            .where(WORKSPACE_ID.eq(workspaceId))
            .and(STAGE.eq(stage.name))
            .fetchOne()
            ?.toShortsPrompt()

    override fun save(prompt: ShortsPrompt): ShortsPrompt {
        val id = dsl.insertInto(UGC_SHORTS_PROMPTS)
            .set(WORKSPACE_ID, prompt.workspaceId)
            .set(STAGE, prompt.stage.name)
            .set(NAME, prompt.name)
            .set(DESCRIPTION, prompt.description)
            .set(SYSTEM_PROMPT, prompt.systemPrompt)
            .set(USER_PROMPT, prompt.userPrompt)
            .set(EXECUTABLE, prompt.executable)
            .set(REVISION, prompt.revision)
            .set(CREATED_BY, prompt.createdBy)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return findById(id)!!
    }

    override fun update(prompt: ShortsPrompt): ShortsPrompt {
        // 낙관적 락: 로드 시점 version과 일치할 때만 갱신하고 version을 증가시킨다.
        val affected = dsl.update(UGC_SHORTS_PROMPTS)
            .set(NAME, prompt.name)
            .set(DESCRIPTION, prompt.description)
            .set(SYSTEM_PROMPT, prompt.systemPrompt)
            .set(USER_PROMPT, prompt.userPrompt)
            .set(EXECUTABLE, prompt.executable)
            .set(REVISION, prompt.revision)
            .set(VERSION, prompt.version + 1)
            .set(UPDATED_AT, LocalDateTime.now())
            .where(ID.eq(prompt.id))
            .and(VERSION.eq(prompt.version))
            .execute()

        if (affected == 0) {
            throw IllegalStateException("프롬프트가 다른 곳에서 수정되었습니다. 새로고침 후 다시 시도해 주세요")
        }
        return findById(prompt.id)!!
    }

    override fun deleteByWorkspaceAndStage(workspaceId: Long, stage: PipelineStage): Boolean =
        dsl.deleteFrom(UGC_SHORTS_PROMPTS)
            .where(WORKSPACE_ID.eq(workspaceId))
            .and(STAGE.eq(stage.name))
            .execute() > 0

    override fun saveRevision(revision: ShortsPromptRevision): ShortsPromptRevision {
        val id = dsl.insertInto(UGC_SHORTS_PROMPT_REVISIONS)
            .set(PROMPT_ID, revision.promptId)
            .set(REVISION, revision.revision)
            .set(SYSTEM_PROMPT, revision.systemPrompt)
            .set(USER_PROMPT, revision.userPrompt)
            .set(CHANGE_NOTE, revision.changeNote)
            .set(CHANGED_BY, revision.changedBy)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return dsl.select()
            .from(UGC_SHORTS_PROMPT_REVISIONS)
            .where(ID.eq(id))
            .fetchOne()!!
            .toShortsPromptRevision()
    }

    override fun findRevisions(promptId: Long): List<ShortsPromptRevision> =
        dsl.select()
            .from(UGC_SHORTS_PROMPT_REVISIONS)
            .where(PROMPT_ID.eq(promptId))
            .orderBy(REVISION.desc())
            .fetch()
            .map { it.toShortsPromptRevision() }

    override fun findRevision(promptId: Long, revision: Int): ShortsPromptRevision? =
        dsl.select()
            .from(UGC_SHORTS_PROMPT_REVISIONS)
            .where(PROMPT_ID.eq(promptId))
            .and(REVISION.eq(revision))
            .fetchOne()
            ?.toShortsPromptRevision()

    private fun findById(id: Long): ShortsPrompt? =
        dsl.select()
            .from(UGC_SHORTS_PROMPTS)
            .where(ID.eq(id))
            .fetchOne()
            ?.toShortsPrompt()

    private fun Record.toShortsPrompt(): ShortsPrompt = ShortsPrompt(
        id = get(ID),
        workspaceId = get(WORKSPACE_ID),
        stage = PipelineStage.valueOf(get(STAGE)),
        name = get(NAME),
        description = get(DESCRIPTION),
        systemPrompt = get(SYSTEM_PROMPT),
        userPrompt = get(USER_PROMPT),
        executable = get(EXECUTABLE),
        revision = get(REVISION),
        createdBy = get(CREATED_BY),
        createdAt = localDateTime(CREATED_AT)!!.atZone(ZoneOffset.UTC).toInstant(),
        updatedAt = localDateTime(UPDATED_AT)!!.atZone(ZoneOffset.UTC).toInstant(),
        version = get(VERSION),
    )

    private fun Record.toShortsPromptRevision(): ShortsPromptRevision = ShortsPromptRevision(
        id = get(ID),
        promptId = get(PROMPT_ID),
        revision = get(REVISION),
        systemPrompt = get(SYSTEM_PROMPT),
        userPrompt = get(USER_PROMPT),
        changeNote = get(CHANGE_NOTE),
        changedBy = get(CHANGED_BY),
        createdAt = localDateTime(CREATED_AT)!!.atZone(ZoneOffset.UTC).toInstant(),
    )
}
