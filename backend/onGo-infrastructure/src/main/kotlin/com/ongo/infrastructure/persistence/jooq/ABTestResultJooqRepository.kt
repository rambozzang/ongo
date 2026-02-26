package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.abtestresult.ABTestResult
import com.ongo.domain.abtestresult.ABTestResultRepository
import com.ongo.domain.abtestresult.TestVariant
import com.ongo.domain.abtestresult.TestVariantRepository
import com.ongo.infrastructure.persistence.jooq.Fields.AVG_WATCH_TIME
import com.ongo.infrastructure.persistence.jooq.Fields.CLICKS
import com.ongo.infrastructure.persistence.jooq.Fields.CONFIDENCE
import com.ongo.infrastructure.persistence.jooq.Fields.CONVERSIONS
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.CTR
import com.ongo.infrastructure.persistence.jooq.Fields.DESCRIPTION
import com.ongo.infrastructure.persistence.jooq.Fields.ENGAGEMENT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.IS_CONTROL
import com.ongo.infrastructure.persistence.jooq.Fields.IS_WINNER
import com.ongo.infrastructure.persistence.jooq.Fields.METRIC
import com.ongo.infrastructure.persistence.jooq.Fields.NAME
import com.ongo.infrastructure.persistence.jooq.Fields.PLATFORM
import com.ongo.infrastructure.persistence.jooq.Fields.RESULT_ID
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.TEST_ID
import com.ongo.infrastructure.persistence.jooq.Fields.TEST_NAME
import com.ongo.infrastructure.persistence.jooq.Fields.THUMBNAIL_URL
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.VARIANT_ID
import com.ongo.infrastructure.persistence.jooq.Fields.VIEWS
import com.ongo.infrastructure.persistence.jooq.Fields.WINNER
import com.ongo.infrastructure.persistence.jooq.Fields.WORKSPACE_ID
import com.ongo.infrastructure.persistence.jooq.Tables.AB_TEST_RESULTS
import com.ongo.infrastructure.persistence.jooq.Tables.TEST_VARIANTS
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDateTime

@Repository
class ABTestResultJooqRepository(
    private val dsl: DSLContext,
) : ABTestResultRepository {

    override fun findByWorkspaceId(workspaceId: Long): List<ABTestResult> =
        dsl.select()
            .from(AB_TEST_RESULTS)
            .where(WORKSPACE_ID.eq(workspaceId))
            .orderBy(CREATED_AT.desc())
            .fetch()
            .map { it.toABTestResult() }

    override fun findByWorkspaceIdAndStatus(workspaceId: Long, status: String): List<ABTestResult> =
        dsl.select()
            .from(AB_TEST_RESULTS)
            .where(WORKSPACE_ID.eq(workspaceId))
            .and(STATUS.eq(status))
            .orderBy(CREATED_AT.desc())
            .fetch()
            .map { it.toABTestResult() }

    override fun findById(id: Long): ABTestResult? =
        dsl.select()
            .from(AB_TEST_RESULTS)
            .where(ID.eq(id))
            .fetchOne()
            ?.toABTestResult()

    override fun save(result: ABTestResult): ABTestResult {
        val startDateField = DSL.field("start_date", LocalDateTime::class.java)
        val endDateField = DSL.field("end_date", LocalDateTime::class.java)
        val newId = dsl.insertInto(AB_TEST_RESULTS)
            .set(WORKSPACE_ID, result.workspaceId)
            .set(TEST_ID, result.testId)
            .set(TEST_NAME, result.testName)
            .set(STATUS, result.status)
            .set(startDateField, result.startDate)
            .set(endDateField, result.endDate)
            .set(WINNER, result.winner)
            .set(CONFIDENCE, result.confidence)
            .set(METRIC, result.metric)
            .set(PLATFORM, result.platform)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return findById(newId)!!
    }

    override fun update(result: ABTestResult): ABTestResult {
        val startDateField = DSL.field("start_date", LocalDateTime::class.java)
        val endDateField = DSL.field("end_date", LocalDateTime::class.java)
        dsl.update(AB_TEST_RESULTS)
            .set(TEST_NAME, result.testName)
            .set(STATUS, result.status)
            .set(startDateField, result.startDate)
            .set(endDateField, result.endDate)
            .set(WINNER, result.winner)
            .set(CONFIDENCE, result.confidence)
            .set(METRIC, result.metric)
            .set(PLATFORM, result.platform)
            .set(UPDATED_AT, LocalDateTime.now())
            .where(ID.eq(result.id))
            .execute()

        return findById(result.id)!!
    }

    override fun deleteById(id: Long) {
        dsl.deleteFrom(AB_TEST_RESULTS)
            .where(ID.eq(id))
            .execute()
    }

    private fun Record.toABTestResult(): ABTestResult {
        val startDateField = DSL.field("start_date", LocalDateTime::class.java)
        val endDateField = DSL.field("end_date", LocalDateTime::class.java)
        return ABTestResult(
            id = get(ID),
            workspaceId = get(WORKSPACE_ID),
            testId = get(TEST_ID),
            testName = get(TEST_NAME),
            status = get(STATUS),
            startDate = localDateTime(startDateField)!!,
            endDate = localDateTime(endDateField),
            winner = get(WINNER),
            confidence = get(CONFIDENCE) ?: BigDecimal.ZERO,
            metric = get(METRIC),
            platform = get(PLATFORM),
            createdAt = localDateTime(CREATED_AT)!!,
            updatedAt = localDateTime(UPDATED_AT)!!,
        )
    }
}

@Repository
class TestVariantJooqRepository(
    private val dsl: DSLContext,
) : TestVariantRepository {

    override fun findByResultId(resultId: Long): List<TestVariant> =
        dsl.select()
            .from(TEST_VARIANTS)
            .where(RESULT_ID.eq(resultId))
            .orderBy(CREATED_AT.asc())
            .fetch()
            .map { it.toTestVariant() }

    override fun findById(id: Long): TestVariant? =
        dsl.select()
            .from(TEST_VARIANTS)
            .where(ID.eq(id))
            .fetchOne()
            ?.toTestVariant()

    override fun save(variant: TestVariant): TestVariant {
        val viewsField = DSL.field("views", Long::class.java)
        val clicksField = DSL.field("clicks", Long::class.java)
        val newId = dsl.insertInto(TEST_VARIANTS)
            .set(RESULT_ID, variant.resultId)
            .set(VARIANT_ID, variant.variantId)
            .set(NAME, variant.name)
            .set(DESCRIPTION, variant.description)
            .set(THUMBNAIL_URL, variant.thumbnailUrl)
            .set(viewsField, variant.views)
            .set(clicksField, variant.clicks)
            .set(CTR, variant.ctr)
            .set(AVG_WATCH_TIME, variant.avgWatchTime)
            .set(ENGAGEMENT, variant.engagement)
            .set(CONVERSIONS, variant.conversions)
            .set(IS_CONTROL, variant.isControl)
            .set(IS_WINNER, variant.isWinner)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return findById(newId)!!
    }

    override fun saveBatch(variants: List<TestVariant>): List<TestVariant> =
        variants.map { save(it) }

    override fun update(variant: TestVariant): TestVariant {
        val viewsField = DSL.field("views", Long::class.java)
        val clicksField = DSL.field("clicks", Long::class.java)
        dsl.update(TEST_VARIANTS)
            .set(NAME, variant.name)
            .set(DESCRIPTION, variant.description)
            .set(THUMBNAIL_URL, variant.thumbnailUrl)
            .set(viewsField, variant.views)
            .set(clicksField, variant.clicks)
            .set(CTR, variant.ctr)
            .set(AVG_WATCH_TIME, variant.avgWatchTime)
            .set(ENGAGEMENT, variant.engagement)
            .set(CONVERSIONS, variant.conversions)
            .set(IS_CONTROL, variant.isControl)
            .set(IS_WINNER, variant.isWinner)
            .where(ID.eq(variant.id))
            .execute()

        return findById(variant.id)!!
    }

    override fun deleteByResultId(resultId: Long) {
        dsl.deleteFrom(TEST_VARIANTS)
            .where(RESULT_ID.eq(resultId))
            .execute()
    }

    private fun Record.toTestVariant(): TestVariant {
        val viewsRaw = get("views")
        val clicksRaw = get("clicks")
        return TestVariant(
            id = get(ID),
            resultId = get(RESULT_ID),
            variantId = get(VARIANT_ID),
            name = get(NAME),
            description = get(DESCRIPTION),
            thumbnailUrl = get(THUMBNAIL_URL),
            views = when (viewsRaw) {
                is Long -> viewsRaw
                is Number -> viewsRaw.toLong()
                else -> 0L
            },
            clicks = when (clicksRaw) {
                is Long -> clicksRaw
                is Number -> clicksRaw.toLong()
                else -> 0L
            },
            ctr = get(CTR) ?: BigDecimal.ZERO,
            avgWatchTime = get(AVG_WATCH_TIME) ?: 0,
            engagement = get(ENGAGEMENT) ?: BigDecimal.ZERO,
            conversions = get(CONVERSIONS) ?: 0,
            isControl = get(IS_CONTROL) ?: false,
            isWinner = get(IS_WINNER) ?: false,
            createdAt = localDateTime(CREATED_AT)!!,
        )
    }
}
