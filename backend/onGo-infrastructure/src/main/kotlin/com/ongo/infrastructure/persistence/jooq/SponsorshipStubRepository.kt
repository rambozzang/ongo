package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.sponsorship.*
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
import java.time.LocalDate
import java.time.LocalDateTime

@Repository
class SponsorshipJooqRepository(
    private val dsl: DSLContext,
) : SponsorshipRepository {

    companion object {
        private val TABLE = DSL.table("sponsorships")
        private val BRAND_NAME = DSL.field("brand_name", String::class.java)
        private val BRAND_LOGO = DSL.field("brand_logo", String::class.java)
        private val CONTACT_NAME = DSL.field("contact_name", String::class.java)
        private val CONTACT_EMAIL = DSL.field("contact_email", String::class.java)
        private val DEAL_VALUE = DSL.field("deal_value", Long::class.java)
        private val CURRENCY = DSL.field("currency", String::class.java)
        private val START_DATE = DSL.field("start_date", LocalDate::class.java)
        private val END_DATE = DSL.field("end_date", LocalDate::class.java)
        private val NOTES = DSL.field("notes", String::class.java)
        private val CONTRACT_URL = DSL.field("contract_url", String::class.java)
        private val PAYMENT_STATUS = DSL.field("payment_status", String::class.java)
        private val PAID_AMOUNT = DSL.field("paid_amount", Long::class.java)
    }

    override fun findByUserId(userId: Long): List<Sponsorship> =
        dsl.select().from(TABLE).where(USER_ID.eq(userId)).fetch { it.toSponsorship() }

    override fun findByUserIdAndStatus(userId: Long, status: String): List<Sponsorship> =
        dsl.select().from(TABLE)
            .where(USER_ID.eq(userId))
            .and(STATUS.eq(status))
            .fetch { it.toSponsorship() }

    override fun findById(id: Long): Sponsorship? =
        dsl.select().from(TABLE).where(ID.eq(id)).fetchOne()?.toSponsorship()

    override fun save(sponsorship: Sponsorship): Sponsorship {
        val id = dsl.insertInto(TABLE)
            .set(USER_ID, sponsorship.userId)
            .set(BRAND_NAME, sponsorship.brandName)
            .set(BRAND_LOGO, sponsorship.brandLogo)
            .set(CONTACT_NAME, sponsorship.contactName)
            .set(CONTACT_EMAIL, sponsorship.contactEmail)
            .set(STATUS, sponsorship.status)
            .set(DEAL_VALUE, sponsorship.dealValue)
            .set(CURRENCY, sponsorship.currency)
            .set(START_DATE, sponsorship.startDate)
            .set(END_DATE, sponsorship.endDate)
            .set(NOTES, sponsorship.notes)
            .set(CONTRACT_URL, sponsorship.contractUrl)
            .set(PAYMENT_STATUS, sponsorship.paymentStatus)
            .set(PAID_AMOUNT, sponsorship.paidAmount)
            .returningResult(ID)
            .fetchOne()!!.get(ID)
        return findById(id)!!
    }

    override fun update(sponsorship: Sponsorship): Sponsorship {
        dsl.update(TABLE)
            .set(BRAND_NAME, sponsorship.brandName)
            .set(BRAND_LOGO, sponsorship.brandLogo)
            .set(CONTACT_NAME, sponsorship.contactName)
            .set(CONTACT_EMAIL, sponsorship.contactEmail)
            .set(STATUS, sponsorship.status)
            .set(DEAL_VALUE, sponsorship.dealValue)
            .set(CURRENCY, sponsorship.currency)
            .set(START_DATE, sponsorship.startDate)
            .set(END_DATE, sponsorship.endDate)
            .set(NOTES, sponsorship.notes)
            .set(CONTRACT_URL, sponsorship.contractUrl)
            .set(PAYMENT_STATUS, sponsorship.paymentStatus)
            .set(PAID_AMOUNT, sponsorship.paidAmount)
            .set(UPDATED_AT, LocalDateTime.now())
            .where(ID.eq(sponsorship.id))
            .execute()
        return findById(sponsorship.id)!!
    }

    override fun deleteById(id: Long) {
        dsl.deleteFrom(TABLE).where(ID.eq(id)).execute()
    }

    private fun Record.toSponsorship(): Sponsorship = Sponsorship(
        id = get(ID),
        userId = get(USER_ID),
        brandName = get(BRAND_NAME),
        brandLogo = get(BRAND_LOGO),
        contactName = get(CONTACT_NAME),
        contactEmail = get(CONTACT_EMAIL),
        status = get(STATUS) ?: "INQUIRY",
        dealValue = get(DEAL_VALUE) ?: 0,
        currency = get(CURRENCY) ?: "KRW",
        startDate = localDate(START_DATE)!!,
        endDate = localDate(END_DATE)!!,
        notes = get(NOTES),
        contractUrl = get(CONTRACT_URL),
        paymentStatus = get(PAYMENT_STATUS) ?: "PENDING",
        paidAmount = get(PAID_AMOUNT) ?: 0,
        createdAt = localDateTime(CREATED_AT) ?: LocalDateTime.now(),
        updatedAt = localDateTime(UPDATED_AT) ?: LocalDateTime.now(),
    )
}

@Repository
class SponsorshipDeliverableJooqRepository(
    private val dsl: DSLContext,
) : SponsorshipDeliverableRepository {

    companion object {
        private val TABLE = DSL.table("sponsorship_deliverables")
        private val SPONSORSHIP_ID = DSL.field("sponsorship_id", Long::class.java)
        private val TYPE = DSL.field("type", String::class.java)
        private val DESCRIPTION = DSL.field("description", String::class.java)
        private val DUE_DATE = DSL.field("due_date", LocalDate::class.java)
        private val IS_COMPLETED = DSL.field("is_completed", Boolean::class.java)
        private val VIDEO_ID_STR = DSL.field("video_id", String::class.java)
        private val PLATFORM = DSL.field("platform", String::class.java)
        private val COMPLETED_AT = DSL.field("completed_at", LocalDateTime::class.java)
    }

    override fun findBySponsorshipId(sponsorshipId: Long): List<SponsorshipDeliverable> =
        dsl.select().from(TABLE)
            .where(SPONSORSHIP_ID.eq(sponsorshipId))
            .fetch { it.toDeliverable() }

    override fun findById(id: Long): SponsorshipDeliverable? =
        dsl.select().from(TABLE).where(ID.eq(id)).fetchOne()?.toDeliverable()

    override fun save(deliverable: SponsorshipDeliverable): SponsorshipDeliverable {
        val id = dsl.insertInto(TABLE)
            .set(SPONSORSHIP_ID, deliverable.sponsorshipId)
            .set(TYPE, deliverable.type)
            .set(TITLE, deliverable.title)
            .set(DESCRIPTION, deliverable.description)
            .set(DUE_DATE, deliverable.dueDate)
            .set(IS_COMPLETED, deliverable.isCompleted)
            .set(VIDEO_ID_STR, deliverable.videoId)
            .set(PLATFORM, deliverable.platform)
            .set(COMPLETED_AT, deliverable.completedAt)
            .returningResult(ID)
            .fetchOne()!!.get(ID)
        return findById(id)!!
    }

    override fun update(deliverable: SponsorshipDeliverable): SponsorshipDeliverable {
        dsl.update(TABLE)
            .set(TYPE, deliverable.type)
            .set(TITLE, deliverable.title)
            .set(DESCRIPTION, deliverable.description)
            .set(DUE_DATE, deliverable.dueDate)
            .set(IS_COMPLETED, deliverable.isCompleted)
            .set(VIDEO_ID_STR, deliverable.videoId)
            .set(PLATFORM, deliverable.platform)
            .set(COMPLETED_AT, deliverable.completedAt)
            .where(ID.eq(deliverable.id))
            .execute()
        return findById(deliverable.id)!!
    }

    override fun deleteById(id: Long) {
        dsl.deleteFrom(TABLE).where(ID.eq(id)).execute()
    }

    private fun Record.toDeliverable(): SponsorshipDeliverable = SponsorshipDeliverable(
        id = get(ID),
        sponsorshipId = get(SPONSORSHIP_ID),
        type = get(TYPE),
        title = get(TITLE),
        description = get(DESCRIPTION),
        dueDate = localDate(DUE_DATE)!!,
        isCompleted = get(IS_COMPLETED) ?: false,
        videoId = get(VIDEO_ID_STR),
        platform = get(PLATFORM),
        completedAt = localDateTime(COMPLETED_AT),
        createdAt = localDateTime(CREATED_AT) ?: LocalDateTime.now(),
    )
}
