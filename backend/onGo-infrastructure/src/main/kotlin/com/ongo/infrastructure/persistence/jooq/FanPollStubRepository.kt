package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.fanpoll.*
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
import java.time.Instant
import java.time.LocalDate

@Repository
class FanPollJooqRepository(
    private val dsl: DSLContext,
) : FanPollRepository {

    companion object {
        private val TABLE = DSL.table("fan_polls")
        private val DESCRIPTION = DSL.field("description", String::class.java)
        private val TYPE = DSL.field("type", String::class.java)
        private val TOTAL_VOTES = DSL.field("total_votes", Int::class.java)
        private val START_DATE = DSL.field("start_date", LocalDate::class.java)
        private val END_DATE = DSL.field("end_date", LocalDate::class.java)
    }

    override fun findByUserId(userId: Long): List<FanPoll> =
        dsl.select().from(TABLE)
            .where(USER_ID.eq(userId))
            .fetch { it.toPoll() }

    override fun findById(id: Long): FanPoll? =
        dsl.select().from(TABLE).where(ID.eq(id)).fetchOne()?.toPoll()

    override fun findByUserIdAndStatus(userId: Long, status: String): List<FanPoll> =
        dsl.select().from(TABLE)
            .where(USER_ID.eq(userId))
            .and(STATUS.eq(status))
            .fetch { it.toPoll() }

    override fun save(poll: FanPoll): FanPoll {
        val id = dsl.insertInto(TABLE)
            .set(USER_ID, poll.userId)
            .set(TITLE, poll.title)
            .set(DESCRIPTION, poll.description)
            .set(TYPE, poll.type)
            .set(STATUS, poll.status)
            .set(TOTAL_VOTES, poll.totalVotes)
            .set(START_DATE, poll.startDate)
            .set(END_DATE, poll.endDate)
            .returningResult(ID)
            .fetchOne()!!.get(ID)
        return findById(id)!!
    }

    override fun updateStatus(id: Long, status: String) {
        dsl.update(TABLE)
            .set(STATUS, status)
            .where(ID.eq(id))
            .execute()
    }

    override fun deleteById(id: Long) {
        dsl.deleteFrom(TABLE).where(ID.eq(id)).execute()
    }

    private fun Record.toPoll(): FanPoll {
        val createdAtValue = get("created_at")
        val createdInstant = when (createdAtValue) {
            is java.sql.Timestamp -> createdAtValue.toInstant()
            is java.time.LocalDateTime -> createdAtValue.atZone(java.time.ZoneId.systemDefault()).toInstant()
            is java.time.OffsetDateTime -> createdAtValue.toInstant()
            else -> Instant.now()
        }
        val startDateValue = get("start_date")
        val startDate = when (startDateValue) {
            is LocalDate -> startDateValue
            is java.sql.Date -> startDateValue.toLocalDate()
            else -> null
        }
        val endDateValue = get("end_date")
        val endDate = when (endDateValue) {
            is LocalDate -> endDateValue
            is java.sql.Date -> endDateValue.toLocalDate()
            else -> null
        }
        return FanPoll(
            id = get(ID),
            userId = get(USER_ID),
            title = get(TITLE),
            description = get(DESCRIPTION),
            type = get(TYPE) ?: "SINGLE",
            status = get(STATUS) ?: "DRAFT",
            totalVotes = get(TOTAL_VOTES) ?: 0,
            startDate = startDate,
            endDate = endDate,
            createdAt = createdInstant,
        )
    }
}

@Repository
class PollOptionJooqRepository(
    private val dsl: DSLContext,
) : PollOptionRepository {

    companion object {
        private val TABLE = DSL.table("poll_options")
        private val POLL_ID = DSL.field("poll_id", Long::class.java)
        private val TEXT = DSL.field("text", String::class.java)
        private val VOTES = DSL.field("votes", Int::class.java)
        private val PERCENTAGE = DSL.field("percentage", BigDecimal::class.java)
    }

    override fun findByPollId(pollId: Long): List<PollOption> =
        dsl.select().from(TABLE)
            .where(POLL_ID.eq(pollId))
            .fetch { it.toOption() }

    override fun saveAll(options: List<PollOption>): List<PollOption> {
        if (options.isEmpty()) return emptyList()
        val pollId = options.first().pollId
        options.forEach { option ->
            dsl.insertInto(TABLE)
                .set(POLL_ID, option.pollId)
                .set(TEXT, option.text)
                .set(VOTES, option.votes)
                .set(PERCENTAGE, option.percentage)
                .execute()
        }
        return findByPollId(pollId)
    }

    override fun deleteByPollId(pollId: Long) {
        dsl.deleteFrom(TABLE).where(POLL_ID.eq(pollId)).execute()
    }

    private fun Record.toOption(): PollOption = PollOption(
        id = get(ID),
        pollId = get(POLL_ID),
        text = get(TEXT),
        votes = get(VOTES) ?: 0,
        percentage = get(PERCENTAGE) ?: BigDecimal.ZERO,
    )
}
