package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.ugc.campaign.Playbook
import com.ongo.domain.ugc.campaign.PlaybookRepository
import com.ongo.domain.ugc.campaign.PlaybookStep
import com.ongo.infrastructure.persistence.jooq.Fields.CAMPAIGN_ID
import com.ongo.infrastructure.persistence.jooq.Fields.CONTENT_TYPE
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.EXAMPLE_URL
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.INSTRUCTION
import com.ongo.infrastructure.persistence.jooq.Fields.PLAYBOOK_ID
import com.ongo.infrastructure.persistence.jooq.Fields.REQUIRED
import com.ongo.infrastructure.persistence.jooq.Fields.REVISION
import com.ongo.infrastructure.persistence.jooq.Fields.SORT_ORDER
import com.ongo.infrastructure.persistence.jooq.Fields.STEP_TYPE
import com.ongo.infrastructure.persistence.jooq.Fields.SUMMARY
import com.ongo.infrastructure.persistence.jooq.Fields.TITLE
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Tables.UGC_PLAYBOOKS
import com.ongo.infrastructure.persistence.jooq.Tables.UGC_PLAYBOOK_STEPS
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class JooqPlaybookRepository(
    private val dsl: DSLContext,
) : PlaybookRepository {

    override fun findByCampaignId(campaignId: Long): Playbook? {
        val record = dsl.select()
            .from(UGC_PLAYBOOKS)
            .where(CAMPAIGN_ID.eq(campaignId))
            .fetchOne()
            ?: return null

        val playbookId = record.get(ID)
        val steps = dsl.select()
            .from(UGC_PLAYBOOK_STEPS)
            .where(PLAYBOOK_ID.eq(playbookId))
            .orderBy(SORT_ORDER.asc())
            .fetch()
            .map { it.toStep() }

        return Playbook(
            id = playbookId,
            campaignId = campaignId,
            title = record.get(TITLE),
            summary = record.get(SUMMARY),
            contentType = record.get(CONTENT_TYPE),
            revision = record.get(REVISION),
            steps = steps,
            createdAt = record.localDateTime(CREATED_AT),
            updatedAt = record.localDateTime(UPDATED_AT),
        )
    }

    override fun existsByCampaignId(campaignId: Long): Boolean =
        dsl.fetchExists(
            dsl.selectOne().from(UGC_PLAYBOOKS).where(CAMPAIGN_ID.eq(campaignId)),
        )

    override fun upsert(playbook: Playbook): Playbook {
        val existing = dsl.select()
            .from(UGC_PLAYBOOKS)
            .where(CAMPAIGN_ID.eq(playbook.campaignId))
            .fetchOne()

        val playbookId = if (existing == null) {
            dsl.insertInto(UGC_PLAYBOOKS)
                .set(CAMPAIGN_ID, playbook.campaignId)
                .set(TITLE, playbook.title)
                .set(SUMMARY, playbook.summary)
                .set(CONTENT_TYPE, playbook.contentType)
                .set(REVISION, 1)
                .returningResult(ID)
                .fetchOne()!!
                .get(ID)
        } else {
            val existingId = existing.get(ID)
            dsl.update(UGC_PLAYBOOKS)
                .set(TITLE, playbook.title)
                .set(SUMMARY, playbook.summary)
                .set(CONTENT_TYPE, playbook.contentType)
                .set(REVISION, existing.get(REVISION) + 1)
                .set(UPDATED_AT, LocalDateTime.now())
                .where(ID.eq(existingId))
                .execute()
            existingId
        }

        // 단계 전체 교체 — sort_order는 리스트 인덱스로 재부여해 (playbook_id, sort_order) 유니크 보장
        dsl.deleteFrom(UGC_PLAYBOOK_STEPS)
            .where(PLAYBOOK_ID.eq(playbookId))
            .execute()

        playbook.steps.forEachIndexed { index, step ->
            dsl.insertInto(UGC_PLAYBOOK_STEPS)
                .set(PLAYBOOK_ID, playbookId)
                .set(SORT_ORDER, index)
                .set(STEP_TYPE, step.stepType)
                .set(TITLE, step.title)
                .set(INSTRUCTION, step.instruction)
                .set(EXAMPLE_URL, step.exampleUrl)
                .set(REQUIRED, step.required)
                .execute()
        }

        return findByCampaignId(playbook.campaignId)!!
    }

    private fun Record.toStep(): PlaybookStep = PlaybookStep(
        id = get(ID),
        playbookId = get(PLAYBOOK_ID),
        sortOrder = get(SORT_ORDER),
        stepType = get(STEP_TYPE),
        title = get(TITLE),
        instruction = get(INSTRUCTION),
        exampleUrl = get(EXAMPLE_URL),
        required = get(REQUIRED),
    )
}
