package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.multibrand.*
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.TITLE
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class BrandJooqRepository(
    private val dsl: DSLContext,
) : BrandRepository {

    companion object {
        private val TABLE = DSL.table("brands")
        private val NAME = DSL.field("name", String::class.java)
        private val LOGO_URL = DSL.field("logo_url", String::class.java)
        private val COLOR = DSL.field("color", String::class.java)
        private val CATEGORY = DSL.field("category", String::class.java)
        private val ASSIGNED_EDITORS = DSL.field("assigned_editors", String::class.java)
        private val IS_ACTIVE = DSL.field("is_active", Boolean::class.java)
    }

    override fun findById(id: Long): Brand? =
        dsl.select().from(TABLE).where(ID.eq(id)).fetchOne()?.toBrand()

    override fun findByUserId(userId: Long): List<Brand> =
        dsl.select().from(TABLE).where(USER_ID.eq(userId)).fetch().map { it.toBrand() }

    override fun save(brand: Brand): Brand {
        val newId = dsl.insertInto(TABLE)
            .set(USER_ID, brand.userId)
            .set(NAME, brand.name)
            .set(LOGO_URL, brand.logoUrl)
            .set(COLOR, brand.color)
            .set(CATEGORY, brand.category)
            .set(ASSIGNED_EDITORS, brand.assignedEditors)
            .set(IS_ACTIVE, brand.isActive)
            .returningResult(ID)
            .fetchOne()!!.get(ID)
        return findById(newId)!!
    }

    override fun update(brand: Brand): Brand {
        dsl.update(TABLE)
            .set(NAME, brand.name)
            .set(LOGO_URL, brand.logoUrl)
            .set(COLOR, brand.color)
            .set(CATEGORY, brand.category)
            .set(ASSIGNED_EDITORS, brand.assignedEditors)
            .set(IS_ACTIVE, brand.isActive)
            .set(UPDATED_AT, LocalDateTime.now())
            .where(ID.eq(brand.id))
            .execute()
        return findById(brand.id!!)!!
    }

    override fun delete(id: Long) {
        dsl.deleteFrom(TABLE).where(ID.eq(id)).execute()
    }

    private fun Record.toBrand(): Brand =
        Brand(
            id = get(ID),
            userId = get(USER_ID),
            name = get(NAME),
            logoUrl = get(LOGO_URL),
            color = get(COLOR) ?: "BLUE",
            category = get(CATEGORY),
            assignedEditors = get(ASSIGNED_EDITORS) ?: "[]",
            isActive = get(IS_ACTIVE) ?: true,
            createdAt = localDateTime(CREATED_AT),
            updatedAt = localDateTime(UPDATED_AT),
        )
}

@Repository
class BrandScheduleItemJooqRepository(
    private val dsl: DSLContext,
) : BrandScheduleItemRepository {

    companion object {
        private val TABLE = DSL.table("brand_schedule_items")
        private val BRAND_ID = DSL.field("brand_id", Long::class.java)
        private val PLATFORM = DSL.field("platform", String::class.java)
        private val SCHEDULED_AT = DSL.field("scheduled_at", LocalDateTime::class.java)
        private val ASSIGNED_TO = DSL.field("assigned_to", String::class.java)
        private val VIDEO_ID = DSL.field("video_id", String::class.java)
        private val NOTES = DSL.field("notes", String::class.java)
    }

    override fun findById(id: Long): BrandScheduleItem? =
        dsl.select().from(TABLE).where(ID.eq(id)).fetchOne()?.toBrandScheduleItem()

    override fun findByUserId(userId: Long): List<BrandScheduleItem> =
        dsl.select().from(TABLE).where(USER_ID.eq(userId)).fetch().map { it.toBrandScheduleItem() }

    override fun findByUserIdAndDateRange(userId: Long, startDate: LocalDateTime, endDate: LocalDateTime): List<BrandScheduleItem> =
        dsl.select().from(TABLE)
            .where(USER_ID.eq(userId))
            .and(SCHEDULED_AT.ge(startDate))
            .and(SCHEDULED_AT.le(endDate))
            .fetch().map { it.toBrandScheduleItem() }

    override fun findByUserIdAndBrandId(userId: Long, brandId: Long): List<BrandScheduleItem> =
        dsl.select().from(TABLE)
            .where(USER_ID.eq(userId))
            .and(BRAND_ID.eq(brandId))
            .fetch().map { it.toBrandScheduleItem() }

    override fun save(item: BrandScheduleItem): BrandScheduleItem {
        val newId = dsl.insertInto(TABLE)
            .set(USER_ID, item.userId)
            .set(BRAND_ID, item.brandId)
            .set(TITLE, item.title)
            .set(PLATFORM, item.platform)
            .set(SCHEDULED_AT, item.scheduledAt)
            .set(STATUS, item.status)
            .set(ASSIGNED_TO, item.assignedTo)
            .set(VIDEO_ID, item.videoId)
            .set(NOTES, item.notes)
            .returningResult(ID)
            .fetchOne()!!.get(ID)
        return findById(newId)!!
    }

    override fun update(item: BrandScheduleItem): BrandScheduleItem {
        dsl.update(TABLE)
            .set(BRAND_ID, item.brandId)
            .set(TITLE, item.title)
            .set(PLATFORM, item.platform)
            .set(SCHEDULED_AT, item.scheduledAt)
            .set(STATUS, item.status)
            .set(ASSIGNED_TO, item.assignedTo)
            .set(VIDEO_ID, item.videoId)
            .set(NOTES, item.notes)
            .set(UPDATED_AT, LocalDateTime.now())
            .where(ID.eq(item.id))
            .execute()
        return findById(item.id!!)!!
    }

    override fun delete(id: Long) {
        dsl.deleteFrom(TABLE).where(ID.eq(id)).execute()
    }

    private fun Record.toBrandScheduleItem(): BrandScheduleItem =
        BrandScheduleItem(
            id = get(ID),
            userId = get(USER_ID),
            brandId = get(BRAND_ID),
            title = get(TITLE),
            platform = get(PLATFORM),
            scheduledAt = localDateTime(SCHEDULED_AT) ?: LocalDateTime.now(),
            status = get(STATUS) ?: "DRAFT",
            assignedTo = get(ASSIGNED_TO),
            videoId = get(VIDEO_ID),
            notes = get(NOTES),
            createdAt = localDateTime(CREATED_AT),
            updatedAt = localDateTime(UPDATED_AT),
        )
}
