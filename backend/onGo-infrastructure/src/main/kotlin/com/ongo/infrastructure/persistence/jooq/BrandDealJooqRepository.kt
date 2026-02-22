package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.branddeal.BrandDeal
import com.ongo.domain.branddeal.BrandDealRepository
import com.ongo.domain.branddeal.MediaKit
import com.ongo.infrastructure.persistence.jooq.Fields.BIO
import com.ongo.infrastructure.persistence.jooq.Fields.BRAND_NAME
import com.ongo.infrastructure.persistence.jooq.Fields.CATEGORIES_MK
import com.ongo.infrastructure.persistence.jooq.Fields.CONTACT_EMAIL
import com.ongo.infrastructure.persistence.jooq.Fields.CONTACT_NAME
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.CURRENCY
import com.ongo.infrastructure.persistence.jooq.Fields.DEADLINE_BD
import com.ongo.infrastructure.persistence.jooq.Fields.DEAL_VALUE
import com.ongo.infrastructure.persistence.jooq.Fields.DELIVERABLES
import com.ongo.infrastructure.persistence.jooq.Fields.DISPLAY_NAME
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.IS_PUBLIC_MK
import com.ongo.infrastructure.persistence.jooq.Fields.NOTES_BD
import com.ongo.infrastructure.persistence.jooq.Fields.RATE_CARD
import com.ongo.infrastructure.persistence.jooq.Fields.SLUG
import com.ongo.infrastructure.persistence.jooq.Fields.SOCIAL_LINKS
import com.ongo.infrastructure.persistence.jooq.Fields.STATS_SNAPSHOT
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Tables.BRAND_DEALS
import com.ongo.infrastructure.persistence.jooq.Tables.MEDIA_KITS
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository

@Repository
class BrandDealJooqRepository(
    private val dsl: DSLContext,
) : BrandDealRepository {

    override fun findDealsByUserId(userId: Long, status: String?): List<BrandDeal> {
        var query = dsl.select().from(BRAND_DEALS).where(USER_ID.eq(userId))
        if (status != null) query = query.and(STATUS.eq(status))
        return query.orderBy(CREATED_AT.desc()).fetch().map { it.toBrandDeal() }
    }

    override fun findDealById(id: Long): BrandDeal? =
        dsl.select().from(BRAND_DEALS).where(ID.eq(id)).fetchOne()?.toBrandDeal()

    override fun saveDeal(deal: BrandDeal): BrandDeal {
        val id = dsl.insertInto(BRAND_DEALS)
            .set(USER_ID, deal.userId)
            .set(BRAND_NAME, deal.brandName)
            .set(CONTACT_NAME, deal.contactName)
            .set(CONTACT_EMAIL, deal.contactEmail)
            .set(DEAL_VALUE, deal.dealValue)
            .set(CURRENCY, deal.currency)
            .set(STATUS, deal.status)
            .set(DEADLINE_BD, deal.deadline)
            .set(DELIVERABLES, deal.deliverables)
            .set(NOTES_BD, deal.notes)
            .returningResult(ID)
            .fetchOne()!!.get(ID)
        return findDealById(id)!!
    }

    override fun updateDeal(id: Long, brandName: String?, contactName: String?, contactEmail: String?, dealValue: Long?, status: String?, deadline: java.time.LocalDate?, deliverables: String?, notes: String?) {
        val sets = mutableMapOf<org.jooq.Field<*>, Any?>()
        if (brandName != null) sets[BRAND_NAME] = brandName
        if (contactName != null) sets[CONTACT_NAME] = contactName
        if (contactEmail != null) sets[CONTACT_EMAIL] = contactEmail
        if (dealValue != null) sets[DEAL_VALUE] = dealValue
        if (status != null) sets[STATUS] = status
        if (deadline != null) sets[DEADLINE_BD] = deadline
        if (deliverables != null) sets[DELIVERABLES] = deliverables
        if (notes != null) sets[NOTES_BD] = notes
        sets[UPDATED_AT] = java.time.LocalDateTime.now()
        if (sets.size <= 1) return
        dsl.update(BRAND_DEALS).set(sets).where(ID.eq(id)).execute()
    }

    override fun deleteDeal(id: Long) {
        dsl.deleteFrom(BRAND_DEALS).where(ID.eq(id)).execute()
    }

    // Media Kit

    override fun findMediaKitByUserId(userId: Long): MediaKit? =
        dsl.select().from(MEDIA_KITS).where(USER_ID.eq(userId)).fetchOne()?.toMediaKit()

    override fun findMediaKitBySlug(slug: String): MediaKit? =
        dsl.select().from(MEDIA_KITS).where(SLUG.eq(slug)).fetchOne()?.toMediaKit()

    override fun saveMediaKit(kit: MediaKit): MediaKit {
        val id = dsl.insertInto(MEDIA_KITS)
            .set(USER_ID, kit.userId)
            .set(DISPLAY_NAME, kit.displayName)
            .set(BIO, kit.bio)
            .set(CATEGORIES_MK, kit.categories)
            .set(SOCIAL_LINKS, kit.socialLinks)
            .set(STATS_SNAPSHOT, kit.statsSnapshot)
            .set(RATE_CARD, kit.rateCard)
            .set(IS_PUBLIC_MK, kit.isPublic)
            .set(SLUG, kit.slug)
            .returningResult(ID)
            .fetchOne()!!.get(ID)
        return dsl.select().from(MEDIA_KITS).where(ID.eq(id)).fetchOne()!!.toMediaKit()
    }

    override fun updateMediaKit(id: Long, displayName: String?, bio: String?, categories: String?, socialLinks: String?, statsSnapshot: String?, rateCard: String?, isPublic: Boolean?, slug: String?) {
        val sets = mutableMapOf<org.jooq.Field<*>, Any?>()
        if (displayName != null) sets[DISPLAY_NAME] = displayName
        if (bio != null) sets[BIO] = bio
        if (categories != null) sets[CATEGORIES_MK] = categories
        if (socialLinks != null) sets[SOCIAL_LINKS] = socialLinks
        if (statsSnapshot != null) sets[STATS_SNAPSHOT] = statsSnapshot
        if (rateCard != null) sets[RATE_CARD] = rateCard
        if (isPublic != null) sets[IS_PUBLIC_MK] = isPublic
        if (slug != null) sets[SLUG] = slug
        sets[UPDATED_AT] = java.time.LocalDateTime.now()
        if (sets.size <= 1) return
        dsl.update(MEDIA_KITS).set(sets).where(ID.eq(id)).execute()
    }

    private fun Record.toBrandDeal() = BrandDeal(
        id = get(ID),
        userId = get(USER_ID),
        brandName = get(BRAND_NAME),
        contactName = get(CONTACT_NAME),
        contactEmail = get(CONTACT_EMAIL),
        dealValue = get(DEAL_VALUE),
        currency = get(CURRENCY),
        status = get(STATUS),
        deadline = localDate(DEADLINE_BD),
        deliverables = get(DELIVERABLES),
        notes = get(NOTES_BD),
        createdAt = localDateTime(CREATED_AT),
        updatedAt = localDateTime(UPDATED_AT),
    )

    private fun Record.toMediaKit() = MediaKit(
        id = get(ID),
        userId = get(USER_ID),
        displayName = get(DISPLAY_NAME),
        bio = get(BIO),
        categories = get(CATEGORIES_MK),
        socialLinks = get(SOCIAL_LINKS),
        statsSnapshot = get(STATS_SNAPSHOT),
        rateCard = get(RATE_CARD),
        isPublic = get(IS_PUBLIC_MK),
        slug = get(SLUG),
        createdAt = localDateTime(CREATED_AT),
        updatedAt = localDateTime(UPDATED_AT),
    )
}
