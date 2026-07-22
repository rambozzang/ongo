package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.ugc.submission.ContentSubmission
import com.ongo.domain.ugc.submission.SubmissionAsset
import com.ongo.domain.ugc.submission.SubmissionRepository
import com.ongo.domain.ugc.submission.SubmissionStatus
import com.ongo.infrastructure.persistence.jooq.Fields.ASSET_TYPE
import com.ongo.infrastructure.persistence.jooq.Fields.CAMPAIGN_ID
import com.ongo.infrastructure.persistence.jooq.Fields.CAPTION
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.CREATOR_ID
import com.ongo.infrastructure.persistence.jooq.Fields.APPROVED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.EXTERNAL_URL
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.RESOURCE_ID
import com.ongo.infrastructure.persistence.jooq.Fields.RESOURCE_TYPE
import com.ongo.infrastructure.persistence.jooq.Fields.REVISION
import com.ongo.infrastructure.persistence.jooq.Fields.SORT_ORDER
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.SUBMISSION_ID
import com.ongo.infrastructure.persistence.jooq.Fields.SUBMITTED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Tables.UGC_CONTENT_SUBMISSIONS
import com.ongo.infrastructure.persistence.jooq.Tables.UGC_SUBMISSION_ASSETS
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class JooqSubmissionRepository(
    private val dsl: DSLContext,
) : SubmissionRepository {

    override fun findById(id: Long): ContentSubmission? =
        dsl.select().from(UGC_CONTENT_SUBMISSIONS).where(ID.eq(id)).fetchOne()?.toSubmission()

    override fun findByCampaignIdAndCreatorId(campaignId: Long, creatorId: Long): ContentSubmission? =
        dsl.select().from(UGC_CONTENT_SUBMISSIONS)
            .where(CAMPAIGN_ID.eq(campaignId)).and(CREATOR_ID.eq(creatorId))
            .fetchOne()?.toSubmission()

    override fun findByCampaignId(campaignId: Long, status: String?, offset: Int, limit: Int): List<ContentSubmission> {
        var condition = CAMPAIGN_ID.eq(campaignId)
        if (status != null) condition = condition.and(STATUS.eq(status))
        return dsl.select().from(UGC_CONTENT_SUBMISSIONS)
            .where(condition)
            .orderBy(UPDATED_AT.desc())
            .limit(limit).offset(offset)
            .fetch().map { it.toSubmission() }
    }

    override fun countByCampaignId(campaignId: Long, status: String?): Long {
        var condition = CAMPAIGN_ID.eq(campaignId)
        if (status != null) condition = condition.and(STATUS.eq(status))
        return dsl.selectCount().from(UGC_CONTENT_SUBMISSIONS).where(condition).fetchOne(0, Long::class.java) ?: 0L
    }

    override fun save(submission: ContentSubmission): ContentSubmission {
        val id = dsl.insertInto(UGC_CONTENT_SUBMISSIONS)
            .set(CAMPAIGN_ID, submission.campaignId)
            .set(CREATOR_ID, submission.creatorId)
            .set(REVISION, submission.revision)
            .set(CAPTION, submission.caption)
            .set(STATUS, submission.status.name)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)
        insertAssets(id, submission.assets)
        return findById(id)!!
    }

    override fun update(submission: ContentSubmission): ContentSubmission {
        val id = submission.id ?: throw IllegalArgumentException("id가 없는 제출은 수정할 수 없습니다")
        dsl.update(UGC_CONTENT_SUBMISSIONS)
            .set(CAPTION, submission.caption)
            .set(STATUS, submission.status.name)
            .set(REVISION, submission.revision)
            .set(UPDATED_AT, LocalDateTime.now())
            .where(ID.eq(id))
            .execute()
        dsl.deleteFrom(UGC_SUBMISSION_ASSETS).where(SUBMISSION_ID.eq(id)).execute()
        insertAssets(id, submission.assets)
        return findById(id)!!
    }

    override fun updateStatus(submission: ContentSubmission): ContentSubmission {
        val id = submission.id ?: throw IllegalArgumentException("id가 없는 제출은 수정할 수 없습니다")
        dsl.update(UGC_CONTENT_SUBMISSIONS)
            .set(STATUS, submission.status.name)
            .set(SUBMITTED_AT, submission.submittedAt)
            .set(APPROVED_AT, submission.approvedAt)
            .set(UPDATED_AT, LocalDateTime.now())
            .where(ID.eq(id))
            .execute()
        return findById(id)!!
    }

    private fun insertAssets(submissionId: Long, assets: List<SubmissionAsset>) {
        assets.forEachIndexed { index, asset ->
            dsl.insertInto(UGC_SUBMISSION_ASSETS)
                .set(SUBMISSION_ID, submissionId)
                .set(ASSET_TYPE, asset.assetType)
                .set(RESOURCE_TYPE, asset.resourceType)
                .set(RESOURCE_ID, asset.resourceId)
                .set(EXTERNAL_URL, asset.externalUrl)
                .set(SORT_ORDER, index)
                .execute()
        }
    }

    private fun loadAssets(submissionId: Long): List<SubmissionAsset> =
        dsl.select().from(UGC_SUBMISSION_ASSETS)
            .where(SUBMISSION_ID.eq(submissionId))
            .orderBy(SORT_ORDER.asc())
            .fetch()
            .map {
                SubmissionAsset(
                    id = it.get(ID),
                    submissionId = it.get(SUBMISSION_ID),
                    assetType = it.get(ASSET_TYPE),
                    resourceType = it.get(RESOURCE_TYPE),
                    resourceId = it.get(RESOURCE_ID),
                    externalUrl = it.get(EXTERNAL_URL),
                    sortOrder = it.get(SORT_ORDER),
                )
            }

    private fun Record.toSubmission(): ContentSubmission {
        val id = get(ID)
        return ContentSubmission(
            id = id,
            campaignId = get(CAMPAIGN_ID),
            creatorId = get(CREATOR_ID),
            revision = get(REVISION),
            caption = get(CAPTION),
            status = SubmissionStatus.valueOf(get(STATUS)),
            submittedAt = localDateTime(SUBMITTED_AT),
            approvedAt = localDateTime(APPROVED_AT),
            assets = loadAssets(id),
            createdAt = localDateTime(CREATED_AT),
            updatedAt = localDateTime(UPDATED_AT),
        )
    }
}
