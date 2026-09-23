package com.ongo.infrastructure.persistence.jooq

import com.ongo.application.ai.SentimentUsageRepository
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
class SentimentUsageJooqRepository(
    private val dsl: DSLContext,
) : SentimentUsageRepository {
    override fun tryConsumeBatch(userId: Long, date: LocalDate, dailyLimit: Int): Boolean {
        require(dailyLimit > 0)
        return dsl.fetchOne(
            """
            INSERT INTO ai_sentiment_daily_usage (user_id, usage_date, batch_count)
            VALUES (?, ?, 1)
            ON CONFLICT (user_id, usage_date) DO UPDATE
            SET batch_count = ai_sentiment_daily_usage.batch_count + 1
            WHERE ai_sentiment_daily_usage.batch_count < ?
            RETURNING batch_count
            """.trimIndent(),
            userId,
            date,
            dailyLimit,
        ) != null
    }
}
