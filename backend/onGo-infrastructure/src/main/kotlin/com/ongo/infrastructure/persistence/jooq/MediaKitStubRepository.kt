package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.mediakit.*
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class MediaKitJooqRepository(
    private val dsl: DSLContext,
) : MediaKitRepository {

    companion object {
        private val TABLE = DSL.table("media_kits")
        private val TITLE = DSL.field("title", String::class.java)
        private val TEMPLATE_STYLE = DSL.field("template_style", String::class.java)
        private val BIO = DSL.field("bio", String::class.java)
        private val PROFILE_IMAGE_URL = DSL.field("profile_image_url", String::class.java)
        private val PLATFORMS = DSL.field("platforms", String::class.java)
        private val DEMOGRAPHICS = DSL.field("demographics", String::class.java)
        private val TOP_CONTENT = DSL.field("top_content", String::class.java)
        private val CAMPAIGN_RESULTS = DSL.field("campaign_results", String::class.java)
        private val RATE_CARDS = DSL.field("rate_cards", String::class.java)
        private val CONTACT_EMAIL = DSL.field("contact_email", String::class.java)
        private val PUBLISHED_URL = DSL.field("published_url", String::class.java)
    }

    override fun findById(id: Long): MediaKit? =
        dsl.select().from(TABLE)
            .where(ID.eq(id))
            .fetchOne()?.toMediaKit()

    override fun findByUserId(userId: Long): List<MediaKit> =
        dsl.select().from(TABLE)
            .where(USER_ID.eq(userId))
            .fetch { it.toMediaKit() }

    override fun save(kit: MediaKit): MediaKit {
        val id = dsl.insertInto(TABLE)
            .set(USER_ID, kit.userId)
            .set(TITLE, kit.title)
            .set(TEMPLATE_STYLE, kit.templateStyle)
            .set(BIO, kit.bio)
            .set(PROFILE_IMAGE_URL, kit.profileImageUrl)
            .set(PLATFORMS, DSL.field("?::jsonb", String::class.java, kit.platforms))
            .set(DEMOGRAPHICS, DSL.field("?::jsonb", String::class.java, kit.demographics))
            .set(TOP_CONTENT, DSL.field("?::jsonb", String::class.java, kit.topContent))
            .set(CAMPAIGN_RESULTS, DSL.field("?::jsonb", String::class.java, kit.campaignResults))
            .set(RATE_CARDS, DSL.field("?::jsonb", String::class.java, kit.rateCards))
            .set(CONTACT_EMAIL, kit.contactEmail)
            .set(STATUS, kit.status)
            .set(PUBLISHED_URL, kit.publishedUrl)
            .returningResult(ID)
            .fetchOne()!!.get(ID)

        return findById(id)!!
    }

    override fun update(kit: MediaKit): MediaKit {
        dsl.update(TABLE)
            .set(TITLE, kit.title)
            .set(TEMPLATE_STYLE, kit.templateStyle)
            .set(BIO, kit.bio)
            .set(PROFILE_IMAGE_URL, kit.profileImageUrl)
            .set(PLATFORMS, DSL.field("?::jsonb", String::class.java, kit.platforms))
            .set(DEMOGRAPHICS, DSL.field("?::jsonb", String::class.java, kit.demographics))
            .set(TOP_CONTENT, DSL.field("?::jsonb", String::class.java, kit.topContent))
            .set(CAMPAIGN_RESULTS, DSL.field("?::jsonb", String::class.java, kit.campaignResults))
            .set(RATE_CARDS, DSL.field("?::jsonb", String::class.java, kit.rateCards))
            .set(CONTACT_EMAIL, kit.contactEmail)
            .set(STATUS, kit.status)
            .set(PUBLISHED_URL, kit.publishedUrl)
            .set(UPDATED_AT, LocalDateTime.now())
            .where(ID.eq(kit.id))
            .execute()

        return findById(kit.id!!)!!
    }

    override fun delete(id: Long) {
        dsl.deleteFrom(TABLE).where(ID.eq(id)).execute()
    }

    private fun Record.toMediaKit(): MediaKit {
        val platformsRaw = get("platforms")
        val demographicsRaw = get("demographics")
        val topContentRaw = get("top_content")
        val campaignResultsRaw = get("campaign_results")
        val rateCardsRaw = get("rate_cards")
        return MediaKit(
            id = get(ID),
            userId = get(USER_ID),
            title = get(TITLE),
            templateStyle = get(TEMPLATE_STYLE) ?: "MODERN",
            bio = get(BIO),
            profileImageUrl = get(PROFILE_IMAGE_URL),
            platforms = when (platformsRaw) {
                is String -> platformsRaw
                else -> platformsRaw?.toString() ?: "[]"
            },
            demographics = when (demographicsRaw) {
                is String -> demographicsRaw
                else -> demographicsRaw?.toString() ?: "[]"
            },
            topContent = when (topContentRaw) {
                is String -> topContentRaw
                else -> topContentRaw?.toString() ?: "[]"
            },
            campaignResults = when (campaignResultsRaw) {
                is String -> campaignResultsRaw
                else -> campaignResultsRaw?.toString() ?: "[]"
            },
            rateCards = when (rateCardsRaw) {
                is String -> rateCardsRaw
                else -> rateCardsRaw?.toString() ?: "[]"
            },
            contactEmail = get(CONTACT_EMAIL),
            status = get(STATUS) ?: "DRAFT",
            publishedUrl = get(PUBLISHED_URL),
            createdAt = localDateTime(CREATED_AT),
            updatedAt = localDateTime(UPDATED_AT),
        )
    }
}
