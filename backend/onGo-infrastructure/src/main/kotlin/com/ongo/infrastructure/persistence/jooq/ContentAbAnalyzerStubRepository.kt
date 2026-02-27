package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.contentabanalyzer.*
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.TITLE
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDate

@Repository
class ContentAbTestJooqRepository(
    private val dsl: DSLContext,
) : ContentAbTestRepository {

    companion object {
        private val TABLE = DSL.table("content_ab_tests")
        private val WINNER = DSL.field("winner", String::class.java)
        private val CONFIDENCE = DSL.field("confidence", BigDecimal::class.java)
        private val START_DATE = DSL.field("start_date", LocalDate::class.java)
        private val END_DATE = DSL.field("end_date", LocalDate::class.java)
    }

    override fun findById(id: Long): ContentAbTest? =
        dsl.select()
            .from(TABLE)
            .where(ID.eq(id))
            .fetchOne()
            ?.toEntity()

    override fun findByUserId(userId: Long): List<ContentAbTest> =
        dsl.select()
            .from(TABLE)
            .where(USER_ID.eq(userId))
            .orderBy(CREATED_AT.desc())
            .fetch()
            .map { it.toEntity() }

    override fun findByStatus(userId: Long, status: String): List<ContentAbTest> =
        dsl.select()
            .from(TABLE)
            .where(USER_ID.eq(userId))
            .and(STATUS.eq(status))
            .orderBy(CREATED_AT.desc())
            .fetch()
            .map { it.toEntity() }

    override fun save(test: ContentAbTest): ContentAbTest {
        val id = dsl.insertInto(TABLE)
            .set(USER_ID, test.userId)
            .set(TITLE, test.title)
            .set(STATUS, test.status)
            .set(WINNER, test.winner)
            .set(CONFIDENCE, test.confidence)
            .set(START_DATE, test.startDate)
            .set(END_DATE, test.endDate)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return findById(id)!!
    }

    override fun updateStatus(id: Long, status: String, winner: String?) {
        val query = dsl.update(TABLE)
            .set(STATUS, status)
            .set(WINNER, winner)

        if (status == "COMPLETED") {
            query.set(END_DATE, LocalDate.now())
        }

        query.where(ID.eq(id))
            .execute()
    }

    private fun Record.toEntity(): ContentAbTest {
        val startDateRaw = get("start_date")
        val endDateRaw = get("end_date")
        return ContentAbTest(
            id = get(ID),
            userId = get(USER_ID),
            title = get(TITLE),
            status = get(STATUS),
            winner = get(WINNER),
            confidence = get(CONFIDENCE) ?: BigDecimal.ZERO,
            startDate = when (startDateRaw) {
                is LocalDate -> startDateRaw
                is java.sql.Date -> startDateRaw.toLocalDate()
                else -> LocalDate.now()
            },
            endDate = when (endDateRaw) {
                is LocalDate -> endDateRaw
                is java.sql.Date -> endDateRaw.toLocalDate()
                else -> null
            },
            createdAt = localDateTime(CREATED_AT),
        )
    }
}

@Repository
class ContentVariantJooqRepository(
    private val dsl: DSLContext,
) : ContentVariantRepository {

    companion object {
        private val TABLE = DSL.table("content_variants")
        private val TEST_ID = DSL.field("test_id", Long::class.java)
        private val LABEL = DSL.field("label", String::class.java)
        private val VIDEO_ID = DSL.field("video_id", Long::class.java)
        private val VIDEO_TITLE = DSL.field("video_title", String::class.java)
        private val VIEWS = DSL.field("views", Long::class.java)
        private val LIKES = DSL.field("likes", Long::class.java)
        private val COMMENTS = DSL.field("comments", Long::class.java)
        private val CTR = DSL.field("ctr", BigDecimal::class.java)
        private val AVG_WATCH_TIME = DSL.field("avg_watch_time", Int::class.java)
    }

    override fun findByTestId(testId: Long): List<ContentVariant> =
        dsl.select()
            .from(TABLE)
            .where(TEST_ID.eq(testId))
            .orderBy(Fields.ID.asc())
            .fetch()
            .map { it.toEntity() }

    override fun save(variant: ContentVariant): ContentVariant {
        val id = dsl.insertInto(TABLE)
            .set(TEST_ID, variant.testId)
            .set(LABEL, variant.label)
            .set(VIDEO_ID, variant.videoId)
            .set(VIDEO_TITLE, variant.videoTitle)
            .set(VIEWS, variant.views)
            .set(LIKES, variant.likes)
            .set(COMMENTS, variant.comments)
            .set(CTR, variant.ctr)
            .set(AVG_WATCH_TIME, variant.avgWatchTime)
            .returningResult(Fields.ID)
            .fetchOne()!!
            .get(Fields.ID)

        return dsl.select()
            .from(TABLE)
            .where(Fields.ID.eq(id))
            .fetchOne()!!
            .toEntity()
    }

    private fun Record.toEntity(): ContentVariant {
        val viewsRaw = get("views")
        val likesRaw = get("likes")
        val commentsRaw = get("comments")
        return ContentVariant(
            id = get(Fields.ID),
            testId = get(TEST_ID),
            label = get(LABEL),
            videoId = when (val v = get("video_id")) {
                is Long -> v
                is Number -> v.toLong()
                else -> 0L
            },
            videoTitle = get(VIDEO_TITLE) ?: "",
            views = when (viewsRaw) {
                is Long -> viewsRaw
                is Number -> viewsRaw.toLong()
                else -> 0L
            },
            likes = when (likesRaw) {
                is Long -> likesRaw
                is Number -> likesRaw.toLong()
                else -> 0L
            },
            comments = when (commentsRaw) {
                is Long -> commentsRaw
                is Number -> commentsRaw.toLong()
                else -> 0L
            },
            ctr = get(CTR) ?: BigDecimal.ZERO,
            avgWatchTime = get(AVG_WATCH_TIME) ?: 0,
        )
    }
}
