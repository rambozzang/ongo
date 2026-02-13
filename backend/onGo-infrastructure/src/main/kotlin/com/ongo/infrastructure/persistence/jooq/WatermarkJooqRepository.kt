package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.watermark.Watermark
import com.ongo.domain.watermark.WatermarkRepository
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.IMAGE_URL
import com.ongo.infrastructure.persistence.jooq.Fields.IS_DEFAULT
import com.ongo.infrastructure.persistence.jooq.Fields.NAME
import com.ongo.infrastructure.persistence.jooq.Fields.OPACITY
import com.ongo.infrastructure.persistence.jooq.Fields.POSITION
import com.ongo.infrastructure.persistence.jooq.Fields.SIZE
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Tables.WATERMARKS
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository
import java.math.BigDecimal

@Repository
class WatermarkJooqRepository(
    private val dsl: DSLContext,
) : WatermarkRepository {

    override fun findById(id: Long): Watermark? =
        dsl.select()
            .from(WATERMARKS)
            .where(ID.eq(id))
            .fetchOne()
            ?.toWatermark()

    override fun findByUserId(userId: Long): List<Watermark> =
        dsl.select()
            .from(WATERMARKS)
            .where(USER_ID.eq(userId))
            .orderBy(CREATED_AT.desc())
            .fetch()
            .map { it.toWatermark() }

    override fun findDefaultByUserId(userId: Long): Watermark? =
        dsl.select()
            .from(WATERMARKS)
            .where(USER_ID.eq(userId))
            .and(IS_DEFAULT.eq(true))
            .fetchOne()
            ?.toWatermark()

    override fun save(watermark: Watermark): Watermark {
        val record = dsl.insertInto(WATERMARKS)
            .set(USER_ID, watermark.userId)
            .set(NAME, watermark.name)
            .set(IMAGE_URL, watermark.imageUrl)
            .set(POSITION, watermark.position)
            .set(OPACITY, BigDecimal.valueOf(watermark.opacity))
            .set(SIZE, watermark.size)
            .set(IS_DEFAULT, watermark.isDefault)
            .returning()
            .fetchOne()!!

        return record.toWatermark()
    }

    override fun update(watermark: Watermark): Watermark {
        val record = dsl.update(WATERMARKS)
            .set(NAME, watermark.name)
            .set(IMAGE_URL, watermark.imageUrl)
            .set(POSITION, watermark.position)
            .set(OPACITY, BigDecimal.valueOf(watermark.opacity))
            .set(SIZE, watermark.size)
            .set(IS_DEFAULT, watermark.isDefault)
            .where(ID.eq(watermark.id))
            .returning()
            .fetchOne()!!

        return record.toWatermark()
    }

    override fun delete(id: Long) {
        dsl.deleteFrom(WATERMARKS)
            .where(ID.eq(id))
            .execute()
    }

    override fun clearDefault(userId: Long) {
        dsl.update(WATERMARKS)
            .set(IS_DEFAULT, false)
            .where(USER_ID.eq(userId))
            .execute()
    }

    private fun Record.toWatermark(): Watermark = Watermark(
        id = get(ID),
        userId = get(USER_ID),
        name = get(NAME),
        imageUrl = get(IMAGE_URL),
        position = get(POSITION) ?: "BOTTOM_RIGHT",
        opacity = (get(OPACITY) as? BigDecimal)?.toDouble() ?: 0.8,
        size = get(SIZE) ?: 100,
        isDefault = get(IS_DEFAULT) ?: false,
        createdAt = localDateTime(CREATED_AT),
    )
}
