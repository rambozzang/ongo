package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.brandkit.BrandKit
import com.ongo.domain.brandkit.BrandKitRepository
import com.ongo.infrastructure.persistence.jooq.Fields.ACCENT_COLOR
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.FONT_FAMILY
import com.ongo.infrastructure.persistence.jooq.Fields.GUIDELINES
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.INTRO_TEMPLATE_URL
import com.ongo.infrastructure.persistence.jooq.Fields.IS_DEFAULT
import com.ongo.infrastructure.persistence.jooq.Fields.LOGO_URL
import com.ongo.infrastructure.persistence.jooq.Fields.NAME
import com.ongo.infrastructure.persistence.jooq.Fields.OUTRO_TEMPLATE_URL
import com.ongo.infrastructure.persistence.jooq.Fields.PRIMARY_COLOR
import com.ongo.infrastructure.persistence.jooq.Fields.SECONDARY_COLOR
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Fields.WATERMARK_URL
import com.ongo.infrastructure.persistence.jooq.Tables.BRAND_KITS
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository

@Repository
class BrandKitJooqRepository(
    private val dsl: DSLContext,
) : BrandKitRepository {

    override fun findById(id: Long): BrandKit? =
        dsl.select()
            .from(BRAND_KITS)
            .where(ID.eq(id))
            .fetchOne()
            ?.toBrandKit()

    override fun findByUserId(userId: Long): List<BrandKit> =
        dsl.select()
            .from(BRAND_KITS)
            .where(USER_ID.eq(userId))
            .orderBy(CREATED_AT.desc())
            .fetch()
            .map { it.toBrandKit() }

    override fun save(brandKit: BrandKit): BrandKit {
        val record = dsl.insertInto(BRAND_KITS)
            .set(USER_ID, brandKit.userId)
            .set(NAME, brandKit.name)
            .set(PRIMARY_COLOR, brandKit.primaryColor)
            .set(SECONDARY_COLOR, brandKit.secondaryColor)
            .set(ACCENT_COLOR, brandKit.accentColor)
            .set(FONT_FAMILY, brandKit.fontFamily)
            .set(LOGO_URL, brandKit.logoUrl)
            .set(INTRO_TEMPLATE_URL, brandKit.introTemplateUrl)
            .set(OUTRO_TEMPLATE_URL, brandKit.outroTemplateUrl)
            .set(WATERMARK_URL, brandKit.watermarkUrl)
            .set(GUIDELINES, brandKit.guidelines)
            .set(IS_DEFAULT, brandKit.isDefault)
            .returning()
            .fetchOne()!!

        return record.toBrandKit()
    }

    override fun update(brandKit: BrandKit): BrandKit {
        val record = dsl.update(BRAND_KITS)
            .set(NAME, brandKit.name)
            .set(PRIMARY_COLOR, brandKit.primaryColor)
            .set(SECONDARY_COLOR, brandKit.secondaryColor)
            .set(ACCENT_COLOR, brandKit.accentColor)
            .set(FONT_FAMILY, brandKit.fontFamily)
            .set(LOGO_URL, brandKit.logoUrl)
            .set(INTRO_TEMPLATE_URL, brandKit.introTemplateUrl)
            .set(OUTRO_TEMPLATE_URL, brandKit.outroTemplateUrl)
            .set(WATERMARK_URL, brandKit.watermarkUrl)
            .set(GUIDELINES, brandKit.guidelines)
            .set(IS_DEFAULT, brandKit.isDefault)
            .set(UPDATED_AT, java.time.LocalDateTime.now())
            .where(ID.eq(brandKit.id))
            .returning()
            .fetchOne()!!

        return record.toBrandKit()
    }

    override fun delete(id: Long) {
        dsl.deleteFrom(BRAND_KITS)
            .where(ID.eq(id))
            .execute()
    }

    override fun clearDefault(userId: Long) {
        dsl.update(BRAND_KITS)
            .set(IS_DEFAULT, false)
            .where(USER_ID.eq(userId))
            .execute()
    }

    private fun Record.toBrandKit(): BrandKit = BrandKit(
        id = get(ID),
        userId = get(USER_ID),
        name = get(NAME),
        primaryColor = get(PRIMARY_COLOR) ?: "#7c3aed",
        secondaryColor = get(SECONDARY_COLOR) ?: "#1e40af",
        accentColor = get(ACCENT_COLOR) ?: "#059669",
        fontFamily = get(FONT_FAMILY) ?: "Pretendard",
        logoUrl = get(LOGO_URL),
        introTemplateUrl = get(INTRO_TEMPLATE_URL),
        outroTemplateUrl = get(OUTRO_TEMPLATE_URL),
        watermarkUrl = get(WATERMARK_URL),
        guidelines = get(GUIDELINES),
        isDefault = get(IS_DEFAULT) ?: false,
        createdAt = localDateTime(CREATED_AT),
        updatedAt = localDateTime(UPDATED_AT),
    )
}
