package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.ugc.shorts.ShortsTemplate
import com.ongo.domain.ugc.shorts.ShortsTemplateRepository
import com.ongo.infrastructure.persistence.jooq.Fields.ASPECT_RATIO
import com.ongo.infrastructure.persistence.jooq.Fields.BACKGROUND_STYLE
import com.ongo.infrastructure.persistence.jooq.Fields.CAPTION_FONT_COLOR
import com.ongo.infrastructure.persistence.jooq.Fields.CAPTION_FONT_FAMILY
import com.ongo.infrastructure.persistence.jooq.Fields.CAPTION_FONT_SIZE
import com.ongo.infrastructure.persistence.jooq.Fields.CAPTION_POSITION
import com.ongo.infrastructure.persistence.jooq.Fields.CAPTION_STROKE_COLOR
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_BY
import com.ongo.infrastructure.persistence.jooq.Fields.DESCRIPTION
import com.ongo.infrastructure.persistence.jooq.Fields.HEIGHT
import com.ongo.infrastructure.persistence.jooq.Fields.HOOK_FONT_COLOR
import com.ongo.infrastructure.persistence.jooq.Fields.HOOK_FONT_FAMILY
import com.ongo.infrastructure.persistence.jooq.Fields.HOOK_FONT_SIZE
import com.ongo.infrastructure.persistence.jooq.Fields.HOOK_POSITION
import com.ongo.infrastructure.persistence.jooq.Fields.HOOK_STROKE_COLOR
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.IS_DEFAULT
import com.ongo.infrastructure.persistence.jooq.Fields.NAME
import com.ongo.infrastructure.persistence.jooq.Fields.REFERENCE_IMAGE_URL
import com.ongo.infrastructure.persistence.jooq.Fields.SAFE_AREA_BOTTOM
import com.ongo.infrastructure.persistence.jooq.Fields.SAFE_AREA_TOP
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.VERSION
import com.ongo.infrastructure.persistence.jooq.Fields.WIDTH
import com.ongo.infrastructure.persistence.jooq.Fields.WORKSPACE_ID
import com.ongo.infrastructure.persistence.jooq.Tables.UGC_SHORTS_TEMPLATES
import org.jooq.DSLContext
import org.jooq.JSONB
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.time.ZoneOffset

@Repository
class ShortsTemplateJooqRepository(
    private val dsl: DSLContext,
) : ShortsTemplateRepository {

    override fun findByWorkspace(workspaceId: Long): List<ShortsTemplate> =
        dsl.select()
            .from(UGC_SHORTS_TEMPLATES)
            .where(WORKSPACE_ID.eq(workspaceId))
            .orderBy(CREATED_AT.desc())
            .fetch()
            .map { it.toShortsTemplate() }

    override fun findById(id: Long): ShortsTemplate? =
        dsl.select()
            .from(UGC_SHORTS_TEMPLATES)
            .where(ID.eq(id))
            .fetchOne()
            ?.toShortsTemplate()

    override fun save(template: ShortsTemplate): ShortsTemplate {
        val id = dsl.insertInto(UGC_SHORTS_TEMPLATES)
            .set(WORKSPACE_ID, template.workspaceId)
            .set(NAME, template.name)
            .set(DESCRIPTION, template.description)
            .set(ASPECT_RATIO, template.aspectRatio)
            .set(WIDTH, template.width)
            .set(HEIGHT, template.height)
            .set(BACKGROUND_STYLE, template.backgroundStyle)
            .set(HOOK_FONT_FAMILY, template.hookFontFamily)
            .set(HOOK_FONT_SIZE, template.hookFontSize)
            .set(HOOK_FONT_COLOR, template.hookFontColor)
            .set(HOOK_STROKE_COLOR, template.hookStrokeColor)
            .set(HOOK_POSITION, template.hookPosition)
            .set(CAPTION_FONT_FAMILY, template.captionFontFamily)
            .set(CAPTION_FONT_SIZE, template.captionFontSize)
            .set(CAPTION_FONT_COLOR, template.captionFontColor)
            .set(CAPTION_STROKE_COLOR, template.captionStrokeColor)
            .set(CAPTION_POSITION, template.captionPosition)
            .set(SAFE_AREA_TOP, template.safeAreaTop)
            .set(SAFE_AREA_BOTTOM, template.safeAreaBottom)
            .set(REFERENCE_IMAGE_URL, template.referenceImageUrl)
            .set(EXTRA_SPEC_JSONB, template.extraSpec?.let { JSONB.jsonb(it) })
            .set(IS_DEFAULT, template.isDefault)
            .set(CREATED_BY, template.createdBy)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return findById(id)!!
    }

    override fun update(template: ShortsTemplate): ShortsTemplate {
        // 낙관적 락: 로드 시점 version과 일치할 때만 갱신하고 version을 증가시킨다.
        val affected = dsl.update(UGC_SHORTS_TEMPLATES)
            .set(NAME, template.name)
            .set(DESCRIPTION, template.description)
            .set(ASPECT_RATIO, template.aspectRatio)
            .set(WIDTH, template.width)
            .set(HEIGHT, template.height)
            .set(BACKGROUND_STYLE, template.backgroundStyle)
            .set(HOOK_FONT_FAMILY, template.hookFontFamily)
            .set(HOOK_FONT_SIZE, template.hookFontSize)
            .set(HOOK_FONT_COLOR, template.hookFontColor)
            .set(HOOK_STROKE_COLOR, template.hookStrokeColor)
            .set(HOOK_POSITION, template.hookPosition)
            .set(CAPTION_FONT_FAMILY, template.captionFontFamily)
            .set(CAPTION_FONT_SIZE, template.captionFontSize)
            .set(CAPTION_FONT_COLOR, template.captionFontColor)
            .set(CAPTION_STROKE_COLOR, template.captionStrokeColor)
            .set(CAPTION_POSITION, template.captionPosition)
            .set(SAFE_AREA_TOP, template.safeAreaTop)
            .set(SAFE_AREA_BOTTOM, template.safeAreaBottom)
            .set(REFERENCE_IMAGE_URL, template.referenceImageUrl)
            .set(EXTRA_SPEC_JSONB, template.extraSpec?.let { JSONB.jsonb(it) })
            .set(IS_DEFAULT, template.isDefault)
            .set(VERSION, template.version + 1)
            .set(UPDATED_AT, LocalDateTime.now())
            .where(ID.eq(template.id))
            .and(VERSION.eq(template.version))
            .execute()

        if (affected == 0) {
            throw IllegalStateException("템플릿이 다른 곳에서 수정되었습니다. 새로고침 후 다시 시도해 주세요")
        }
        return findById(template.id)!!
    }

    override fun delete(id: Long): Boolean =
        dsl.deleteFrom(UGC_SHORTS_TEMPLATES)
            .where(ID.eq(id))
            .execute() > 0

    override fun clearDefault(workspaceId: Long) {
        dsl.update(UGC_SHORTS_TEMPLATES)
            .set(IS_DEFAULT, false)
            .set(UPDATED_AT, LocalDateTime.now())
            .where(WORKSPACE_ID.eq(workspaceId))
            .and(IS_DEFAULT.isTrue)
            .execute()
    }

    private fun Record.toShortsTemplate(): ShortsTemplate = ShortsTemplate(
        id = get(ID),
        workspaceId = get(WORKSPACE_ID),
        name = get(NAME),
        description = get(DESCRIPTION),
        aspectRatio = get(ASPECT_RATIO),
        width = get(WIDTH),
        height = get(HEIGHT),
        backgroundStyle = get(BACKGROUND_STYLE),
        hookFontFamily = get(HOOK_FONT_FAMILY),
        hookFontSize = get(HOOK_FONT_SIZE),
        hookFontColor = get(HOOK_FONT_COLOR),
        hookStrokeColor = get(HOOK_STROKE_COLOR),
        hookPosition = get(HOOK_POSITION),
        captionFontFamily = get(CAPTION_FONT_FAMILY),
        captionFontSize = get(CAPTION_FONT_SIZE),
        captionFontColor = get(CAPTION_FONT_COLOR),
        captionStrokeColor = get(CAPTION_STROKE_COLOR),
        captionPosition = get(CAPTION_POSITION),
        safeAreaTop = get(SAFE_AREA_TOP),
        safeAreaBottom = get(SAFE_AREA_BOTTOM),
        referenceImageUrl = get(REFERENCE_IMAGE_URL),
        extraSpec = jsonbString("extra_spec"),
        isDefault = get(IS_DEFAULT),
        createdBy = get(CREATED_BY),
        createdAt = localDateTime(CREATED_AT)!!.atZone(ZoneOffset.UTC).toInstant(),
        updatedAt = localDateTime(UPDATED_AT)!!.atZone(ZoneOffset.UTC).toInstant(),
        version = get(VERSION),
    )

    companion object {
        // extra_spec은 JSONB 컬럼이라 String 바인딩으로 쓸 수 없어 JSONB 타입 필드로만 쓴다.
        private val EXTRA_SPEC_JSONB = DSL.field("extra_spec", JSONB::class.java)

        /** jsonb 컬럼 값을 JSON 문자열로 꺼낸다. 드라이버 반환 타입(JSONB/PGobject/String)을 모두 수용한다. */
        private fun Record.jsonbString(column: String): String? = when (val raw = get(column)) {
            null -> null
            is JSONB -> raw.data()
            is String -> raw
            else -> raw.toString()
        }
    }
}
