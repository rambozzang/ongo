package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.keyword.KeywordResearch
import com.ongo.domain.keyword.KeywordResearchRepository
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.KEYWORD
import com.ongo.infrastructure.persistence.jooq.Fields.PLATFORMS_LIST
import com.ongo.infrastructure.persistence.jooq.Fields.RESULT_JSON
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Tables.KEYWORD_RESEARCH_HISTORY
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository

@Repository
class KeywordResearchJooqRepository(
    private val dsl: DSLContext,
) : KeywordResearchRepository {

    override fun save(research: KeywordResearch): KeywordResearch {
        val id = dsl.insertInto(KEYWORD_RESEARCH_HISTORY)
            .set(USER_ID, research.userId)
            .set(KEYWORD, research.keyword)
            .set(PLATFORMS_LIST, research.platforms)
            .set(RESULT_JSON, research.resultJson)
            .returningResult(ID)
            .fetchOne()!!.get(ID)!!
        return dsl.select().from(KEYWORD_RESEARCH_HISTORY)
            .where(ID.eq(id))
            .fetchOne()!!.toKeywordResearch()
    }

    override fun findByUserId(userId: Long, page: Int, size: Int): List<KeywordResearch> =
        dsl.select().from(KEYWORD_RESEARCH_HISTORY)
            .where(USER_ID.eq(userId))
            .orderBy(CREATED_AT.desc())
            .limit(size)
            .offset((page - 1) * size)
            .fetch()
            .map { it.toKeywordResearch() }

    override fun countByUserId(userId: Long): Int =
        dsl.selectCount().from(KEYWORD_RESEARCH_HISTORY)
            .where(USER_ID.eq(userId))
            .fetchOne(0, Int::class.java) ?: 0

    private fun Record.toKeywordResearch() = KeywordResearch(
        id = get(ID),
        userId = get(USER_ID),
        keyword = get(KEYWORD),
        platforms = get(PLATFORMS_LIST),
        resultJson = get(RESULT_JSON),
        createdAt = localDateTime(CREATED_AT),
    )
}
