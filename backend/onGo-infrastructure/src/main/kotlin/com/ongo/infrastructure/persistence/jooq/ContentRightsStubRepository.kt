package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.contentrights.*
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Tables.CONTENT_RIGHTS
import com.ongo.infrastructure.persistence.jooq.Tables.RIGHTS_ALERTS
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository

@Repository
class ContentRightJooqRepository(
    private val dsl: DSLContext,
) : ContentRightRepository {

    companion object {
        private val VIDEO_ID = DSL.field("video_id", Long::class.java)
        private val VIDEO_TITLE = DSL.field("video_title", String::class.java)
        private val ASSET_NAME = DSL.field("asset_name", String::class.java)
        private val ASSET_TYPE = DSL.field("asset_type", String::class.java)
        private val LICENSE_TYPE = DSL.field("license_type", String::class.java)
        private val LICENSE_STATUS = DSL.field("license_status", String::class.java)
        private val SOURCE = DSL.field("source", String::class.java)
        private val LICENSE_URL = DSL.field("license_url", String::class.java)
        private val EXPIRES_AT = DSL.field("expires_at", java.time.LocalDateTime::class.java)
        private val PURCHASED_AT = DSL.field("purchased_at", java.time.LocalDateTime::class.java)
        private val COST = DSL.field("cost", Long::class.java)
        private val CURRENCY = DSL.field("currency", String::class.java)
        private val NOTES = DSL.field("notes", String::class.java)
    }

    override fun findById(id: Long): ContentRight? =
        dsl.select()
            .from(CONTENT_RIGHTS)
            .where(ID.eq(id))
            .fetchOne()
            ?.toEntity()

    override fun findByUserId(userId: Long): List<ContentRight> =
        dsl.select()
            .from(CONTENT_RIGHTS)
            .where(USER_ID.eq(userId))
            .orderBy(CREATED_AT.desc())
            .fetch()
            .map { it.toEntity() }

    override fun save(right: ContentRight): ContentRight {
        val id = dsl.insertInto(CONTENT_RIGHTS)
            .set(USER_ID, right.userId)
            .set(VIDEO_ID, right.videoId)
            .set(VIDEO_TITLE, right.videoTitle)
            .set(ASSET_NAME, right.assetName)
            .set(ASSET_TYPE, right.assetType)
            .set(LICENSE_TYPE, right.licenseType)
            .set(LICENSE_STATUS, right.licenseStatus)
            .set(SOURCE, right.source)
            .set(LICENSE_URL, right.licenseUrl)
            .set(EXPIRES_AT, right.expiresAt)
            .set(PURCHASED_AT, right.purchasedAt)
            .set(COST, right.cost)
            .set(CURRENCY, right.currency)
            .set(NOTES, right.notes)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return findById(id)!!
    }

    override fun update(right: ContentRight): ContentRight {
        dsl.update(CONTENT_RIGHTS)
            .set(ASSET_NAME, right.assetName)
            .set(ASSET_TYPE, right.assetType)
            .set(LICENSE_TYPE, right.licenseType)
            .set(LICENSE_STATUS, right.licenseStatus)
            .set(SOURCE, right.source)
            .set(LICENSE_URL, right.licenseUrl)
            .set(EXPIRES_AT, right.expiresAt)
            .set(COST, right.cost)
            .set(CURRENCY, right.currency)
            .set(NOTES, right.notes)
            .set(UPDATED_AT, java.time.LocalDateTime.now())
            .where(ID.eq(right.id))
            .execute()

        return findById(right.id!!)!!
    }

    override fun delete(id: Long) {
        dsl.deleteFrom(CONTENT_RIGHTS)
            .where(ID.eq(id))
            .execute()
    }

    private fun Record.toEntity(): ContentRight = ContentRight(
        id = get(ID),
        userId = get(USER_ID),
        videoId = get(VIDEO_ID),
        videoTitle = get(VIDEO_TITLE),
        assetName = get(ASSET_NAME) ?: "",
        assetType = get(ASSET_TYPE) ?: "MUSIC",
        licenseType = get(LICENSE_TYPE) ?: "FREE",
        licenseStatus = get(LICENSE_STATUS) ?: "ACTIVE",
        source = get(SOURCE),
        licenseUrl = get(LICENSE_URL),
        expiresAt = localDateTime(EXPIRES_AT),
        purchasedAt = localDateTime(PURCHASED_AT),
        cost = get(COST) ?: 0,
        currency = get(CURRENCY) ?: "KRW",
        notes = get(NOTES),
        createdAt = localDateTime(CREATED_AT),
        updatedAt = localDateTime(UPDATED_AT),
    )
}

@Repository
class RightsAlertJooqRepository(
    private val dsl: DSLContext,
) : RightsAlertRepository {

    companion object {
        private val CONTENT_RIGHT_ID = DSL.field("content_right_id", Long::class.java)
        private val ASSET_NAME = DSL.field("asset_name", String::class.java)
        private val ASSET_TYPE = DSL.field("asset_type", String::class.java)
        private val MESSAGE = DSL.field("message", String::class.java)
        private val SEVERITY = DSL.field("severity", String::class.java)
        private val DAYS_UNTIL_EXPIRY = DSL.field("days_until_expiry", Int::class.java)
        private val IS_READ = DSL.field("is_read", Boolean::class.java)
    }

    override fun findById(id: Long): RightsAlert? =
        dsl.select()
            .from(RIGHTS_ALERTS)
            .where(ID.eq(id))
            .fetchOne()
            ?.toEntity()

    override fun findByUserId(userId: Long): List<RightsAlert> =
        dsl.select()
            .from(RIGHTS_ALERTS)
            .where(USER_ID.eq(userId))
            .orderBy(CREATED_AT.desc())
            .fetch()
            .map { it.toEntity() }

    override fun save(alert: RightsAlert): RightsAlert {
        val id = dsl.insertInto(RIGHTS_ALERTS)
            .set(USER_ID, alert.userId)
            .set(CONTENT_RIGHT_ID, alert.contentRightId)
            .set(ASSET_NAME, alert.assetName)
            .set(ASSET_TYPE, alert.assetType)
            .set(MESSAGE, alert.message)
            .set(SEVERITY, alert.severity)
            .set(DAYS_UNTIL_EXPIRY, alert.daysUntilExpiry)
            .set(IS_READ, alert.isRead)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return findById(id)!!
    }

    override fun markRead(id: Long) {
        dsl.update(RIGHTS_ALERTS)
            .set(IS_READ, true)
            .where(ID.eq(id))
            .execute()
    }

    override fun delete(id: Long) {
        dsl.deleteFrom(RIGHTS_ALERTS)
            .where(ID.eq(id))
            .execute()
    }

    private fun Record.toEntity(): RightsAlert = RightsAlert(
        id = get(ID),
        userId = get(USER_ID),
        contentRightId = get(CONTENT_RIGHT_ID),
        assetName = get(ASSET_NAME) ?: "",
        assetType = get(ASSET_TYPE) ?: "",
        message = get(MESSAGE) ?: "",
        severity = get(SEVERITY) ?: "INFO",
        daysUntilExpiry = get(DAYS_UNTIL_EXPIRY) ?: 0,
        isRead = get(IS_READ) ?: false,
        createdAt = localDateTime(CREATED_AT),
    )
}
