package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.linkbio.LinkBioLink
import com.ongo.domain.linkbio.LinkBioPage
import com.ongo.domain.linkbio.LinkBioRepository
import com.ongo.infrastructure.persistence.jooq.Fields.AVATAR_URL
import com.ongo.infrastructure.persistence.jooq.Fields.BACKGROUND_COLOR
import com.ongo.infrastructure.persistence.jooq.Fields.BIO
import com.ongo.infrastructure.persistence.jooq.Fields.CLICK_COUNT
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ICON
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.IS_ACTIVE
import com.ongo.infrastructure.persistence.jooq.Fields.IS_PUBLISHED
import com.ongo.infrastructure.persistence.jooq.Fields.PAGE_ID
import com.ongo.infrastructure.persistence.jooq.Fields.SLUG
import com.ongo.infrastructure.persistence.jooq.Fields.SORT_ORDER
import com.ongo.infrastructure.persistence.jooq.Fields.TEXT_COLOR
import com.ongo.infrastructure.persistence.jooq.Fields.THEME
import com.ongo.infrastructure.persistence.jooq.Fields.TITLE
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.URL
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Fields.VIEW_COUNT
import com.ongo.infrastructure.persistence.jooq.Tables.LINK_BIO_LINKS
import com.ongo.infrastructure.persistence.jooq.Tables.LINK_BIO_PAGES
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository

@Repository
class LinkBioJooqRepository(
    private val dsl: DSLContext,
) : LinkBioRepository {

    override fun findPageById(id: Long): LinkBioPage? =
        dsl.select()
            .from(LINK_BIO_PAGES)
            .where(ID.eq(id))
            .fetchOne()
            ?.toPage()

    override fun findPageByUserId(userId: Long): LinkBioPage? =
        dsl.select()
            .from(LINK_BIO_PAGES)
            .where(USER_ID.eq(userId))
            .fetchOne()
            ?.toPage()

    override fun findPageBySlug(slug: String): LinkBioPage? =
        dsl.select()
            .from(LINK_BIO_PAGES)
            .where(SLUG.eq(slug))
            .fetchOne()
            ?.toPage()

    override fun savePage(page: LinkBioPage): LinkBioPage {
        val id = dsl.insertInto(LINK_BIO_PAGES)
            .set(USER_ID, page.userId)
            .set(SLUG, page.slug)
            .set(TITLE, page.title)
            .set(BIO, page.bio)
            .set(AVATAR_URL, page.avatarUrl)
            .set(THEME, page.theme)
            .set(BACKGROUND_COLOR, page.backgroundColor)
            .set(TEXT_COLOR, page.textColor)
            .set(IS_PUBLISHED, page.isPublished)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return findPageById(id)!!
    }

    override fun updatePage(page: LinkBioPage): LinkBioPage {
        dsl.update(LINK_BIO_PAGES)
            .set(SLUG, page.slug)
            .set(TITLE, page.title)
            .set(BIO, page.bio)
            .set(AVATAR_URL, page.avatarUrl)
            .set(THEME, page.theme)
            .set(BACKGROUND_COLOR, page.backgroundColor)
            .set(TEXT_COLOR, page.textColor)
            .set(IS_PUBLISHED, page.isPublished)
            .set(UPDATED_AT, java.time.LocalDateTime.now())
            .where(ID.eq(page.id))
            .execute()

        return findPageById(page.id!!)!!
    }

    override fun incrementViewCount(pageId: Long) {
        dsl.update(LINK_BIO_PAGES)
            .set(VIEW_COUNT, VIEW_COUNT.plus(1))
            .where(ID.eq(pageId))
            .execute()
    }

    private fun findLinkById(id: Long): LinkBioLink? =
        dsl.select()
            .from(LINK_BIO_LINKS)
            .where(ID.eq(id))
            .fetchOne()
            ?.toLink()

    override fun findLinksByPageId(pageId: Long): List<LinkBioLink> =
        dsl.select()
            .from(LINK_BIO_LINKS)
            .where(PAGE_ID.eq(pageId))
            .orderBy(SORT_ORDER.asc())
            .fetch()
            .map { it.toLink() }

    override fun saveLink(link: LinkBioLink): LinkBioLink {
        val id = dsl.insertInto(LINK_BIO_LINKS)
            .set(PAGE_ID, link.pageId)
            .set(TITLE, link.title)
            .set(URL, link.url)
            .set(ICON, link.icon)
            .set(SORT_ORDER, link.sortOrder)
            .set(IS_ACTIVE, link.isActive)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return findLinkById(id)!!
    }

    override fun updateLink(link: LinkBioLink): LinkBioLink {
        dsl.update(LINK_BIO_LINKS)
            .set(TITLE, link.title)
            .set(URL, link.url)
            .set(ICON, link.icon)
            .set(SORT_ORDER, link.sortOrder)
            .set(IS_ACTIVE, link.isActive)
            .where(ID.eq(link.id))
            .execute()

        return findLinkById(link.id!!)!!
    }

    override fun deleteLink(id: Long) {
        dsl.deleteFrom(LINK_BIO_LINKS)
            .where(ID.eq(id))
            .execute()
    }

    override fun deleteAllLinksByPageId(pageId: Long) {
        dsl.deleteFrom(LINK_BIO_LINKS)
            .where(PAGE_ID.eq(pageId))
            .execute()
    }

    override fun incrementClickCount(linkId: Long) {
        dsl.update(LINK_BIO_LINKS)
            .set(CLICK_COUNT, CLICK_COUNT.plus(1))
            .where(ID.eq(linkId))
            .execute()
    }

    private fun Record.toPage(): LinkBioPage = LinkBioPage(
        id = get(ID),
        userId = get(USER_ID),
        slug = get(SLUG),
        title = get(TITLE),
        bio = get(BIO),
        avatarUrl = get(AVATAR_URL),
        theme = get(THEME) ?: "default",
        backgroundColor = get(BACKGROUND_COLOR) ?: "#ffffff",
        textColor = get(TEXT_COLOR) ?: "#000000",
        isPublished = get(IS_PUBLISHED) ?: false,
        viewCount = get(VIEW_COUNT) ?: 0,
        createdAt = localDateTime(CREATED_AT),
        updatedAt = localDateTime(UPDATED_AT),
    )

    private fun Record.toLink(): LinkBioLink = LinkBioLink(
        id = get(ID),
        pageId = get(PAGE_ID),
        title = get(TITLE),
        url = get(URL),
        icon = get(ICON),
        sortOrder = get(SORT_ORDER) ?: 0,
        clickCount = get(CLICK_COUNT) ?: 0,
        isActive = get(IS_ACTIVE) ?: true,
        createdAt = localDateTime(CREATED_AT),
    )
}
