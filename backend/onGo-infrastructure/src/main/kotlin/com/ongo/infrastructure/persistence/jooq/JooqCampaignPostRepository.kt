package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.ugc.publishing.CampaignPost
import com.ongo.domain.ugc.publishing.CampaignPostRepository
import com.ongo.domain.ugc.publishing.PostStatus
import com.ongo.domain.ugc.publishing.PostType
import com.ongo.infrastructure.persistence.jooq.Fields.CAMPAIGN_ID
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.CREATOR_ID
import com.ongo.infrastructure.persistence.jooq.Fields.ERROR_MESSAGE
import com.ongo.infrastructure.persistence.jooq.Fields.EXTERNAL_POST_URL
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.IDEMPOTENCY_KEY
import com.ongo.infrastructure.persistence.jooq.Fields.PLATFORM
import com.ongo.infrastructure.persistence.jooq.Fields.PLATFORM_POST_ID
import com.ongo.infrastructure.persistence.jooq.Fields.POST_TYPE
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.SUBMISSION_ID
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.VIDEO_UPLOAD_ID
import com.ongo.infrastructure.persistence.jooq.Tables.UGC_CAMPAIGN_POSTS
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class JooqCampaignPostRepository(
    private val dsl: DSLContext,
) : CampaignPostRepository {

    override fun findById(id: Long): CampaignPost? =
        dsl.select().from(UGC_CAMPAIGN_POSTS).where(ID.eq(id)).fetchOne()?.toPost()

    override fun findAll(): List<CampaignPost> =
        dsl.select().from(UGC_CAMPAIGN_POSTS)
            .orderBy(CREATED_AT.desc())
            .fetch().map { it.toPost() }

    override fun findByCampaignId(campaignId: Long): List<CampaignPost> =
        dsl.select().from(UGC_CAMPAIGN_POSTS)
            .where(CAMPAIGN_ID.eq(campaignId))
            .orderBy(CREATED_AT.desc())
            .fetch().map { it.toPost() }

    override fun findBySubmissionId(submissionId: Long): List<CampaignPost> =
        dsl.select().from(UGC_CAMPAIGN_POSTS)
            .where(SUBMISSION_ID.eq(submissionId))
            .orderBy(CREATED_AT.desc())
            .fetch().map { it.toPost() }

    override fun findByIdempotencyKey(idempotencyKey: String): CampaignPost? =
        dsl.select().from(UGC_CAMPAIGN_POSTS).where(IDEMPOTENCY_KEY.eq(idempotencyKey)).fetchOne()?.toPost()

    override fun save(post: CampaignPost): CampaignPost {
        val id = dsl.insertInto(UGC_CAMPAIGN_POSTS)
            .set(CAMPAIGN_ID, post.campaignId)
            .set(SUBMISSION_ID, post.submissionId)
            .set(CREATOR_ID, post.creatorId)
            .set(PLATFORM, post.platform)
            .set(POST_TYPE, post.postType.name)
            .set(VIDEO_UPLOAD_ID, post.videoUploadId)
            .set(EXTERNAL_POST_URL, post.externalPostUrl)
            .set(PLATFORM_POST_ID, post.platformPostId)
            .set(STATUS, post.status.name)
            .set(IDEMPOTENCY_KEY, post.idempotencyKey)
            .set(ERROR_MESSAGE, post.errorMessage)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)
        return findById(id)!!
    }

    override fun updateStatus(id: Long, status: PostStatus, platformPostId: String?, errorMessage: String?) {
        dsl.update(UGC_CAMPAIGN_POSTS)
            .set(STATUS, status.name)
            .set(PLATFORM_POST_ID, platformPostId)
            .set(ERROR_MESSAGE, errorMessage)
            .set(UPDATED_AT, LocalDateTime.now())
            .where(ID.eq(id))
            .execute()
    }

    private fun Record.toPost(): CampaignPost = CampaignPost(
        id = get(ID),
        campaignId = get(CAMPAIGN_ID),
        submissionId = get(SUBMISSION_ID),
        creatorId = get(CREATOR_ID),
        platform = get(PLATFORM),
        postType = PostType.valueOf(get(POST_TYPE)),
        videoUploadId = get(VIDEO_UPLOAD_ID),
        externalPostUrl = get(EXTERNAL_POST_URL),
        platformPostId = get(PLATFORM_POST_ID),
        status = PostStatus.valueOf(get(STATUS)),
        idempotencyKey = get(IDEMPOTENCY_KEY),
        errorMessage = get(ERROR_MESSAGE),
        createdAt = localDateTime(CREATED_AT),
        updatedAt = localDateTime(UPDATED_AT),
    )
}
