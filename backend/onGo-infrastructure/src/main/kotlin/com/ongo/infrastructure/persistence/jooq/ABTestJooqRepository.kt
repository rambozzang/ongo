package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.abtest.ABTest
import com.ongo.domain.abtest.ABTestRepository
import com.ongo.domain.abtest.ABTestVariant
import com.ongo.domain.abtest.ABTestVariantRepository
import com.ongo.infrastructure.persistence.jooq.Fields.CLICKS
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.DESCRIPTION
import com.ongo.infrastructure.persistence.jooq.Fields.ENDED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ENGAGEMENT_RATE
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.METRIC_TYPE
import com.ongo.infrastructure.persistence.jooq.Fields.STARTED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.TEST_ID
import com.ongo.infrastructure.persistence.jooq.Fields.TEST_NAME
import com.ongo.infrastructure.persistence.jooq.Fields.THUMBNAIL_URL
import com.ongo.infrastructure.persistence.jooq.Fields.TITLE
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Fields.VARIANT_NAME
import com.ongo.infrastructure.persistence.jooq.Fields.VIDEO_ID
import com.ongo.infrastructure.persistence.jooq.Fields.VIEWS
import com.ongo.infrastructure.persistence.jooq.Fields.WINNER_VARIANT_ID
import com.ongo.infrastructure.persistence.jooq.Tables.AB_TESTS
import com.ongo.infrastructure.persistence.jooq.Tables.AB_TEST_VARIANTS
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository
import java.math.BigDecimal

@Repository
class ABTestJooqRepository(
    private val dsl: DSLContext,
) : ABTestRepository {

    override fun findById(id: Long): ABTest? =
        dsl.select()
            .from(AB_TESTS)
            .where(ID.eq(id))
            .fetchOne()
            ?.toABTest()

    override fun findByUserId(userId: Long): List<ABTest> =
        dsl.select()
            .from(AB_TESTS)
            .where(USER_ID.eq(userId))
            .orderBy(CREATED_AT.desc())
            .fetch()
            .map { it.toABTest() }

    override fun findByStatus(status: String): List<ABTest> =
        dsl.select()
            .from(AB_TESTS)
            .where(STATUS.eq(status))
            .orderBy(CREATED_AT.desc())
            .fetch()
            .map { it.toABTest() }

    override fun save(abTest: ABTest): ABTest {
        val id = dsl.insertInto(AB_TESTS)
            .set(USER_ID, abTest.userId)
            .set(VIDEO_ID, abTest.videoId)
            .set(TEST_NAME, abTest.testName)
            .set(STATUS, abTest.status)
            .set(METRIC_TYPE, abTest.metricType)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return findById(id)!!
    }

    override fun update(abTest: ABTest): ABTest {
        dsl.update(AB_TESTS)
            .set(TEST_NAME, abTest.testName)
            .set(STATUS, abTest.status)
            .set(METRIC_TYPE, abTest.metricType)
            .set(WINNER_VARIANT_ID, abTest.winnerVariantId)
            .set(STARTED_AT, abTest.startedAt)
            .set(ENDED_AT, abTest.endedAt)
            .where(ID.eq(abTest.id))
            .execute()

        return findById(abTest.id!!)!!
    }

    override fun delete(id: Long) {
        dsl.deleteFrom(AB_TESTS)
            .where(ID.eq(id))
            .execute()
    }

    private fun Record.toABTest(): ABTest = ABTest(
        id = get(ID),
        userId = get(USER_ID),
        videoId = get(VIDEO_ID),
        testName = get(TEST_NAME),
        status = get(STATUS),
        metricType = get(METRIC_TYPE),
        winnerVariantId = get(WINNER_VARIANT_ID),
        startedAt = localDateTime(STARTED_AT),
        endedAt = localDateTime(ENDED_AT),
        createdAt = localDateTime(CREATED_AT),
    )
}

@Repository
class ABTestVariantJooqRepository(
    private val dsl: DSLContext,
) : ABTestVariantRepository {

    override fun findByTestId(testId: Long): List<ABTestVariant> =
        dsl.select()
            .from(AB_TEST_VARIANTS)
            .where(TEST_ID.eq(testId))
            .orderBy(CREATED_AT.asc())
            .fetch()
            .map { it.toVariant() }

    override fun save(variant: ABTestVariant): ABTestVariant {
        val viewsField = org.jooq.impl.DSL.field("views", Long::class.java)
        val clicksField = org.jooq.impl.DSL.field("clicks", Long::class.java)
        val id = dsl.insertInto(AB_TEST_VARIANTS)
            .set(TEST_ID, variant.testId)
            .set(VARIANT_NAME, variant.variantName)
            .set(TITLE, variant.title)
            .set(DESCRIPTION, variant.description)
            .set(THUMBNAIL_URL, variant.thumbnailUrl)
            .set(viewsField, variant.views)
            .set(clicksField, variant.clicks)
            .set(ENGAGEMENT_RATE, variant.engagementRate)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return dsl.select()
            .from(AB_TEST_VARIANTS)
            .where(ID.eq(id))
            .fetchOne()!!
            .toVariant()
    }

    override fun deleteByTestId(testId: Long) {
        dsl.deleteFrom(AB_TEST_VARIANTS)
            .where(TEST_ID.eq(testId))
            .execute()
    }

    private fun Record.toVariant(): ABTestVariant {
        val viewsRaw = get("views")
        val clicksRaw = get("clicks")
        return ABTestVariant(
            id = get(ID),
            testId = get(TEST_ID),
            variantName = get(VARIANT_NAME),
            title = get(TITLE),
            description = get(DESCRIPTION),
            thumbnailUrl = get(THUMBNAIL_URL),
            views = when (viewsRaw) {
                is Long -> viewsRaw
                is Int -> viewsRaw.toLong()
                is Number -> viewsRaw.toLong()
                else -> 0L
            },
            clicks = when (clicksRaw) {
                is Long -> clicksRaw
                is Int -> clicksRaw.toLong()
                is Number -> clicksRaw.toLong()
                else -> 0L
            },
            engagementRate = get(ENGAGEMENT_RATE) ?: BigDecimal.ZERO,
            createdAt = localDateTime(CREATED_AT),
        )
    }
}
