package com.ongo.infrastructure.persistence.jooq

import com.fasterxml.jackson.databind.ObjectMapper
import com.ongo.domain.translation.TranslationCreditAllocation
import com.ongo.domain.translation.TranslationRepository
import com.ongo.domain.translation.VideoTranslation
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.DESCRIPTION
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.LANGUAGE
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.SUBTITLE_CONTENT
import com.ongo.infrastructure.persistence.jooq.Fields.TAGS_JSONB
import com.ongo.infrastructure.persistence.jooq.Fields.TITLE
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.VIDEO_ID
import com.ongo.infrastructure.persistence.jooq.Tables.VIDEO_TRANSLATIONS
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.JSONB
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class TranslationJooqRepository(
    private val dsl: DSLContext,
    private val objectMapper: ObjectMapper,
) : TranslationRepository {

    private val log = org.slf4j.LoggerFactory.getLogger(javaClass)

    private companion object {
        val CREDIT_ALLOCATION: Field<JSONB> = DSL.field(DSL.name("credit_allocation"), JSONB::class.java)
        val CLAIMED_AT: Field<LocalDateTime> = DSL.field(DSL.name("claimed_at"), LocalDateTime::class.java)
        val ATTEMPTS: Field<Int> = DSL.field(DSL.name("attempts"), Int::class.java)
        const val TRANSLATING = "TRANSLATING"
    }

    override fun findByVideoId(videoId: Long): List<VideoTranslation> =
        dsl.select().from(VIDEO_TRANSLATIONS).where(VIDEO_ID.eq(videoId))
            .orderBy(CREATED_AT.desc()).fetch().map { it.toTranslation() }

    override fun findById(id: Long): VideoTranslation? =
        dsl.select().from(VIDEO_TRANSLATIONS).where(ID.eq(id)).fetchOne()?.toTranslation()

    override fun findByVideoIdAndLanguage(videoId: Long, language: String): VideoTranslation? =
        dsl.select().from(VIDEO_TRANSLATIONS)
            .where(VIDEO_ID.eq(videoId)).and(LANGUAGE.eq(language))
            .fetchOne()?.toTranslation()

    override fun save(translation: VideoTranslation): VideoTranslation {
        val id = dsl.insertInto(VIDEO_TRANSLATIONS)
            .set(VIDEO_ID, translation.videoId)
            .set(LANGUAGE, translation.language)
            .set(TITLE, translation.title)
            .set(DESCRIPTION, translation.description)
            .set(TAGS_JSONB, translation.tags)
            .set(SUBTITLE_CONTENT, translation.subtitleContent)
            .set(STATUS, translation.status)
            .set(CREDIT_ALLOCATION, translation.creditAllocation?.let { json(it) })
            .returningResult(ID)
            .fetchOne()!!.get(ID)
        return findById(id)!!
    }

    override fun update(id: Long, title: String?, description: String?, tags: String?, subtitleContent: String?, status: String?) {
        val sets = mutableMapOf<org.jooq.Field<*>, Any?>()
        if (title != null) sets[TITLE] = title
        if (description != null) sets[DESCRIPTION] = description
        if (tags != null) sets[TAGS_JSONB] = tags
        if (subtitleContent != null) sets[SUBTITLE_CONTENT] = subtitleContent
        if (status != null) sets[STATUS] = status
        sets[UPDATED_AT] = java.time.LocalDateTime.now()
        if (sets.size <= 1) return
        dsl.update(VIDEO_TRANSLATIONS).set(sets).where(ID.eq(id)).execute()
    }

    /**
     * 조건부 UPDATE 로 DB 가 승자를 정한다. 읽고-판단하고-쓰면 복구 tick 두 개가 같은
     * 행을 통과해 LLM 을 두 번 태운다.
     *
     * `claimed_at IS NULL` 은 아직 아무도 안 집은 행, `claimed_at < staleBefore` 는 죽은
     * 워커가 잡고 있던 행이다. 살아 있는 워커가 잡은 행은 어느 조건에도 걸리지 않는다.
     */
    override fun claimForTranslation(
        id: Long,
        now: LocalDateTime,
        staleBefore: LocalDateTime,
    ): VideoTranslation? {
        val affected = dsl.update(VIDEO_TRANSLATIONS)
            .set(CLAIMED_AT, now)
            .set(ATTEMPTS, ATTEMPTS.plus(1))
            .set(UPDATED_AT, now)
            .where(ID.eq(id))
            .and(STATUS.eq(TRANSLATING))
            .and(CLAIMED_AT.isNull.or(CLAIMED_AT.lt(staleBefore)))
            .execute()
        return if (affected == 1) findById(id) else null
    }

    override fun findStalled(staleBefore: LocalDateTime, limit: Int): List<VideoTranslation> =
        dsl.select()
            .from(VIDEO_TRANSLATIONS)
            .where(STATUS.eq(TRANSLATING))
            .and(CLAIMED_AT.isNull.or(CLAIMED_AT.lt(staleBefore)))
            .orderBy(UPDATED_AT.asc())
            .limit(limit.coerceIn(1, 200))
            .fetch()
            .map { it.toTranslation() }

    /**
     * 환불의 멱등 판정. `status = 'TRANSLATING'` 을 WHERE 에 두어 DB 가 승자를 정한다.
     * 진 쪽은 false 를 받아 크레딧을 돌려주지 않는다.
     */
    override fun settleFailure(id: Long, status: String): Boolean =
        dsl.update(VIDEO_TRANSLATIONS)
            .set(STATUS, status)
            .set(UPDATED_AT, LocalDateTime.now())
            .where(ID.eq(id))
            .and(STATUS.eq(TRANSLATING))
            .execute() == 1

    override fun replaceCreditAllocation(id: Long, allocation: TranslationCreditAllocation?) {
        dsl.update(VIDEO_TRANSLATIONS)
            .set(CREDIT_ALLOCATION, allocation?.let { json(it) })
            // 재시도는 새 실행이다. 이전 시도의 claim 과 횟수를 물려받으면 즉시 상한에 걸린다.
            .set(CLAIMED_AT, null as LocalDateTime?)
            .set(ATTEMPTS, 0)
            .set(UPDATED_AT, LocalDateTime.now())
            .where(ID.eq(id))
            .execute()
    }

    override fun delete(id: Long) {
        dsl.deleteFrom(VIDEO_TRANSLATIONS).where(ID.eq(id)).execute()
    }

    private fun json(value: Any): JSONB = JSONB.jsonb(objectMapper.writeValueAsString(value))

    /**
     * 읽지 못하면 `null` — 없는 것과 같이 다룬다. 깨진 JSON 에 기본값을 채우면
     * "출처를 모른다"가 "무료분에서 0 을 썼다"로 둔갑해 구매분이 조용히 사라진다.
     */
    private fun readAllocation(raw: Any?): TranslationCreditAllocation? {
        val text = when (raw) {
            null -> return null
            is JSONB -> raw.data()
            else -> raw.toString()
        }
        if (text.isBlank() || text == "null") return null
        return runCatching { objectMapper.readValue(text, TranslationCreditAllocation::class.java) }
            .onFailure { log.error("번역 차감 분해를 읽지 못했다. 수기 정산 대상이다: {}", text, it) }
            .getOrNull()
    }

    private fun Record.toTranslation() = VideoTranslation(
        id = get(ID),
        videoId = get(VIDEO_ID),
        language = get(LANGUAGE),
        title = get(TITLE),
        description = get(DESCRIPTION),
        tags = get(TAGS_JSONB),
        subtitleContent = get(SUBTITLE_CONTENT),
        status = get(STATUS),
        creditAllocation = readAllocation(get(CREDIT_ALLOCATION)),
        claimedAt = localDateTime(CLAIMED_AT),
        attempts = get(ATTEMPTS) ?: 0,
        createdAt = localDateTime(CREATED_AT),
        updatedAt = localDateTime(UPDATED_AT),
    )
}
