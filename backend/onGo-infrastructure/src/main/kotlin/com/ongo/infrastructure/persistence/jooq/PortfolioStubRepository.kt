package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.portfolio.CreatorPortfolio
import com.ongo.domain.portfolio.PortfolioRepository
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class PortfolioJooqRepository(
    private val dsl: DSLContext,
) : PortfolioRepository {

    companion object {
        private val TABLE = DSL.table("creator_portfolios")
        private val DISPLAY_NAME = DSL.field("display_name", String::class.java)
        private val BIO = DSL.field("bio", String::class.java)
        private val CATEGORY = DSL.field("category", String::class.java)
        private val PROFILE_IMAGE_URL = DSL.field("profile_image_url", String::class.java)
        private val THEME = DSL.field("theme", String::class.java)
        private val PUBLIC_SLUG = DSL.field("public_slug", String::class.java)
        private val SHOWCASE_VIDEOS = DSL.field("showcase_videos", String::class.java)
        private val BRAND_HISTORY = DSL.field("brand_history", String::class.java)
        private val IS_PUBLIC = DSL.field("is_public", Boolean::class.java)
    }

    override fun findById(id: Long): CreatorPortfolio? =
        dsl.select()
            .from(TABLE)
            .where(ID.eq(id))
            .fetchOne()
            ?.toPortfolio()

    override fun findByUserId(userId: Long): CreatorPortfolio? =
        dsl.select()
            .from(TABLE)
            .where(USER_ID.eq(userId))
            .fetchOne()
            ?.toPortfolio()

    override fun findBySlug(slug: String): CreatorPortfolio? =
        dsl.select()
            .from(TABLE)
            .where(PUBLIC_SLUG.eq(slug))
            .fetchOne()
            ?.toPortfolio()

    override fun save(portfolio: CreatorPortfolio): CreatorPortfolio {
        val id = dsl.insertInto(TABLE)
            .set(USER_ID, portfolio.userId)
            .set(DISPLAY_NAME, portfolio.displayName)
            .set(BIO, portfolio.bio)
            .set(CATEGORY, portfolio.category)
            .set(PROFILE_IMAGE_URL, portfolio.profileImageUrl)
            .set(THEME, portfolio.theme)
            .set(PUBLIC_SLUG, portfolio.publicSlug)
            .set(SHOWCASE_VIDEOS, DSL.field("?::jsonb", String::class.java, portfolio.showcaseVideos))
            .set(BRAND_HISTORY, DSL.field("?::jsonb", String::class.java, portfolio.brandHistory))
            .set(IS_PUBLIC, portfolio.isPublic)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return findById(id)!!
    }

    override fun update(portfolio: CreatorPortfolio): CreatorPortfolio {
        dsl.update(TABLE)
            .set(DISPLAY_NAME, portfolio.displayName)
            .set(BIO, portfolio.bio)
            .set(CATEGORY, portfolio.category)
            .set(PROFILE_IMAGE_URL, portfolio.profileImageUrl)
            .set(THEME, portfolio.theme)
            .set(PUBLIC_SLUG, portfolio.publicSlug)
            .set(SHOWCASE_VIDEOS, DSL.field("?::jsonb", String::class.java, portfolio.showcaseVideos))
            .set(BRAND_HISTORY, DSL.field("?::jsonb", String::class.java, portfolio.brandHistory))
            .set(IS_PUBLIC, portfolio.isPublic)
            .set(UPDATED_AT, LocalDateTime.now())
            .where(ID.eq(portfolio.id))
            .execute()

        return findById(portfolio.id!!)!!
    }

    override fun delete(id: Long) {
        dsl.deleteFrom(TABLE)
            .where(ID.eq(id))
            .execute()
    }

    private fun Record.toPortfolio(): CreatorPortfolio {
        val showcaseRaw = get("showcase_videos")
        val brandRaw = get("brand_history")
        return CreatorPortfolio(
            id = get(ID),
            userId = get(USER_ID),
            displayName = get(DISPLAY_NAME),
            bio = get(BIO),
            category = get(CATEGORY),
            profileImageUrl = get(PROFILE_IMAGE_URL),
            theme = get(THEME) ?: "default",
            publicSlug = get(PUBLIC_SLUG),
            showcaseVideos = when (showcaseRaw) {
                is String -> showcaseRaw
                else -> showcaseRaw?.toString() ?: "[]"
            },
            brandHistory = when (brandRaw) {
                is String -> brandRaw
                else -> brandRaw?.toString() ?: "[]"
            },
            isPublic = get(IS_PUBLIC) ?: false,
            createdAt = localDateTime(CREATED_AT),
            updatedAt = localDateTime(UPDATED_AT),
        )
    }
}
