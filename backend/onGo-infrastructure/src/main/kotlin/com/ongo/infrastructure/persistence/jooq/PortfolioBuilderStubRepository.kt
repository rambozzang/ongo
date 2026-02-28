package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.portfoliobuilder.Portfolio
import com.ongo.domain.portfoliobuilder.PortfolioRepository as PortfolioBuilderPortfolioRepository
import com.ongo.domain.portfoliobuilder.PortfolioSection
import com.ongo.domain.portfoliobuilder.PortfolioSectionRepository
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.TITLE
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class PortfolioBuilderJooqRepository(
    private val dsl: DSLContext,
) : PortfolioBuilderPortfolioRepository {

    companion object {
        private val TABLE = DSL.table("portfolios")
        private val DESCRIPTION = DSL.field("description", String::class.java)
        private val TEMPLATE = DSL.field("template", String::class.java)
        private val THEME = DSL.field("theme", String::class.java)
        private val IS_PUBLISHED = DSL.field("is_published", Boolean::class.java)
        private val PUBLIC_URL = DSL.field("public_url", String::class.java)
        private val VIEW_COUNT = DSL.field("view_count", Int::class.java)
    }

    override fun findById(id: Long): Portfolio? =
        dsl.select().from(TABLE).where(ID.eq(id)).fetchOne()?.toPortfolio()

    override fun findByUserId(userId: Long): List<Portfolio> =
        dsl.select().from(TABLE).where(USER_ID.eq(userId)).fetch().map { it.toPortfolio() }

    override fun save(portfolio: Portfolio): Portfolio {
        val newId = dsl.insertInto(TABLE)
            .set(USER_ID, portfolio.userId)
            .set(TITLE, portfolio.title)
            .set(DESCRIPTION, portfolio.description)
            .set(TEMPLATE, portfolio.template)
            .set(THEME, portfolio.theme)
            .set(IS_PUBLISHED, portfolio.isPublished)
            .set(PUBLIC_URL, portfolio.publicUrl)
            .set(VIEW_COUNT, portfolio.viewCount)
            .returningResult(ID)
            .fetchOne()!!.get(ID)
        return findById(newId)!!
    }

    override fun update(portfolio: Portfolio): Portfolio {
        dsl.update(TABLE)
            .set(TITLE, portfolio.title)
            .set(DESCRIPTION, portfolio.description)
            .set(TEMPLATE, portfolio.template)
            .set(THEME, portfolio.theme)
            .set(IS_PUBLISHED, portfolio.isPublished)
            .set(PUBLIC_URL, portfolio.publicUrl)
            .set(VIEW_COUNT, portfolio.viewCount)
            .set(UPDATED_AT, LocalDateTime.now())
            .where(ID.eq(portfolio.id))
            .execute()
        return findById(portfolio.id!!)!!
    }

    override fun deleteById(id: Long) {
        dsl.deleteFrom(TABLE).where(ID.eq(id)).execute()
    }

    private fun Record.toPortfolio(): Portfolio =
        Portfolio(
            id = get(ID),
            userId = get(USER_ID),
            title = get(TITLE),
            description = get(DESCRIPTION) ?: "",
            template = get(TEMPLATE) ?: "MINIMAL",
            theme = get(THEME) ?: "light",
            isPublished = get(IS_PUBLISHED) ?: false,
            publicUrl = get(PUBLIC_URL),
            viewCount = get(VIEW_COUNT) ?: 0,
            createdAt = localDateTime(CREATED_AT),
            updatedAt = localDateTime(UPDATED_AT),
        )
}

@Repository
class PortfolioSectionJooqRepository(
    private val dsl: DSLContext,
) : PortfolioSectionRepository {

    companion object {
        private val TABLE = DSL.table("portfolio_sections")
        private val PORTFOLIO_ID = DSL.field("portfolio_id", Long::class.java)
        private val SECTION_TYPE = DSL.field("section_type", String::class.java)
        private val CONTENT = DSL.field("content", String::class.java)
        private val SORT_ORDER = DSL.field("sort_order", Int::class.java)
        private val IS_VISIBLE = DSL.field("is_visible", Boolean::class.java)
    }

    override fun findByPortfolioId(portfolioId: Long): List<PortfolioSection> =
        dsl.select().from(TABLE)
            .where(PORTFOLIO_ID.eq(portfolioId))
            .orderBy(SORT_ORDER.asc())
            .fetch().map { it.toPortfolioSection() }

    override fun save(section: PortfolioSection): PortfolioSection {
        val newId = dsl.insertInto(TABLE)
            .set(PORTFOLIO_ID, section.portfolioId)
            .set(SECTION_TYPE, section.sectionType)
            .set(TITLE, section.title)
            .set(CONTENT, section.content)
            .set(SORT_ORDER, section.sortOrder)
            .set(IS_VISIBLE, section.isVisible)
            .returningResult(ID)
            .fetchOne()!!.get(ID)
        return dsl.select().from(TABLE).where(ID.eq(newId)).fetchOne()!!.toPortfolioSection()
    }

    override fun deleteByPortfolioId(portfolioId: Long) {
        dsl.deleteFrom(TABLE).where(PORTFOLIO_ID.eq(portfolioId)).execute()
    }

    private fun Record.toPortfolioSection(): PortfolioSection =
        PortfolioSection(
            id = get(ID),
            portfolioId = get(PORTFOLIO_ID),
            sectionType = get(SECTION_TYPE),
            title = get(TITLE),
            content = get(CONTENT) ?: "",
            sortOrder = get(SORT_ORDER) ?: 0,
            isVisible = get(IS_VISIBLE) ?: true,
        )
}
