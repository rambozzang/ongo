package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.ugc.shorts.ClipHook
import com.ongo.domain.ugc.shorts.ClipHookRepository
import com.ongo.domain.ugc.shorts.HookVariant
import com.ongo.infrastructure.persistence.jooq.Fields.CLIP_ID
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.SELECTED
import com.ongo.infrastructure.persistence.jooq.Fields.TEXT
import com.ongo.infrastructure.persistence.jooq.Fields.VARIANT
import com.ongo.infrastructure.persistence.jooq.Tables.UGC_SHORTS_CLIP_HOOKS
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository
import java.time.ZoneOffset

@Repository
class ShortsClipHookJooqRepository(
    private val dsl: DSLContext,
) : ClipHookRepository {

    override fun saveAll(hooks: List<ClipHook>): List<ClipHook> =
        hooks.map { hook ->
            val id = dsl.insertInto(UGC_SHORTS_CLIP_HOOKS)
                .set(CLIP_ID, hook.clipId)
                .set(VARIANT, hook.variant.name)
                .set(TEXT, hook.text)
                .set(SELECTED, hook.selected)
                .returningResult(ID)
                .fetchOne()!!
                .get(ID)
            findById(id)!!
        }

    override fun findByClipIds(clipIds: List<Long>): List<ClipHook> {
        if (clipIds.isEmpty()) return emptyList()
        return dsl.select()
            .from(UGC_SHORTS_CLIP_HOOKS)
            .where(CLIP_ID.`in`(clipIds))
            .orderBy(ID.asc())
            .fetch()
            .map { it.toClipHook() }
    }

    override fun clearSelection(clipId: Long) {
        dsl.update(UGC_SHORTS_CLIP_HOOKS)
            .set(SELECTED, false)
            .where(CLIP_ID.eq(clipId))
            .execute()
    }

    override fun markSelected(clipId: Long, variant: HookVariant, text: String): ClipHook {
        // (clip_id, variant) 행이 있으면 갱신, 없으면(CUSTOM 최초 선택) 새로 넣는다.
        val existingId = dsl.select(ID)
            .from(UGC_SHORTS_CLIP_HOOKS)
            .where(CLIP_ID.eq(clipId))
            .and(VARIANT.eq(variant.name))
            .fetchOne()
            ?.get(ID)

        val id = if (existingId != null) {
            dsl.update(UGC_SHORTS_CLIP_HOOKS)
                .set(TEXT, text)
                .set(SELECTED, true)
                .where(ID.eq(existingId))
                .execute()
            existingId
        } else {
            dsl.insertInto(UGC_SHORTS_CLIP_HOOKS)
                .set(CLIP_ID, clipId)
                .set(VARIANT, variant.name)
                .set(TEXT, text)
                .set(SELECTED, true)
                .returningResult(ID)
                .fetchOne()!!
                .get(ID)
        }
        return findById(id)!!
    }

    override fun deleteByClipIds(clipIds: List<Long>): Int {
        if (clipIds.isEmpty()) return 0
        return dsl.deleteFrom(UGC_SHORTS_CLIP_HOOKS)
            .where(CLIP_ID.`in`(clipIds))
            .execute()
    }

    private fun findById(id: Long): ClipHook? =
        dsl.select()
            .from(UGC_SHORTS_CLIP_HOOKS)
            .where(ID.eq(id))
            .fetchOne()
            ?.toClipHook()

    private fun Record.toClipHook(): ClipHook = ClipHook(
        id = get(ID),
        clipId = get(CLIP_ID),
        variant = HookVariant.valueOf(get(VARIANT)),
        text = get(TEXT),
        selected = get(SELECTED),
        createdAt = localDateTime(CREATED_AT)!!.atZone(ZoneOffset.UTC).toInstant(),
    )
}
