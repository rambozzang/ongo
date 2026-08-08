package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.apikey.ApiKey
import com.ongo.domain.apikey.ApiKeyRepository
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.KEY_EXPIRES_AT
import com.ongo.infrastructure.persistence.jooq.Fields.KEY_HASH
import com.ongo.infrastructure.persistence.jooq.Fields.KEY_NAME
import com.ongo.infrastructure.persistence.jooq.Fields.KEY_PREFIX
import com.ongo.infrastructure.persistence.jooq.Fields.API_KEY_LAST_USED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.REVOKED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Tables.API_KEYS
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class ApiKeyJooqRepository(
    private val dsl: DSLContext,
) : ApiKeyRepository {

    override fun findById(id: Long): ApiKey? =
        dsl.select().from(API_KEYS).where(ID.eq(id)).fetchOne()?.toApiKey()

    override fun findByUserId(userId: Long): List<ApiKey> =
        dsl.select().from(API_KEYS)
            .where(USER_ID.eq(userId))
            .orderBy(com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT.desc())
            .fetch().map { it.toApiKey() }

    override fun findActiveByHash(keyHash: String, now: LocalDateTime): ApiKey? =
        dsl.select().from(API_KEYS)
            .where(KEY_HASH.eq(keyHash))
            .and(REVOKED_AT.isNull)
            .and(KEY_EXPIRES_AT.isNull.or(KEY_EXPIRES_AT.gt(now)))
            .fetchOne()?.toApiKey()

    override fun countActiveByUserId(userId: Long): Int =
        dsl.selectCount().from(API_KEYS)
            .where(USER_ID.eq(userId))
            .and(REVOKED_AT.isNull)
            .and(KEY_EXPIRES_AT.isNull.or(KEY_EXPIRES_AT.gt(LocalDateTime.now())))
            .fetchOne(0, Int::class.java) ?: 0

    override fun save(apiKey: ApiKey): ApiKey {
        val id = dsl.insertInto(API_KEYS)
            .set(USER_ID, apiKey.userId)
            .set(KEY_NAME, apiKey.name)
            .set(KEY_PREFIX, apiKey.keyPrefix)
            .set(KEY_HASH, apiKey.keyHash)
            .set(API_KEY_LAST_USED_AT, apiKey.lastUsedAt)
            .set(KEY_EXPIRES_AT, apiKey.expiresAt)
            .set(REVOKED_AT, apiKey.revokedAt)
            .returningResult(ID)
            .fetchOne()!!.get(ID)
        return findById(id)!!
    }

    override fun revoke(id: Long, revokedAt: LocalDateTime): Boolean =
        dsl.update(API_KEYS)
            .set(REVOKED_AT, revokedAt)
            .where(ID.eq(id))
            .and(REVOKED_AT.isNull)
            .execute() == 1

    override fun touchLastUsed(id: Long, usedAt: LocalDateTime) {
        val threshold = usedAt.minusMinutes(5)
        dsl.update(API_KEYS)
            .set(API_KEY_LAST_USED_AT, usedAt)
            .where(ID.eq(id))
            .and(REVOKED_AT.isNull)
            .and(API_KEY_LAST_USED_AT.isNull.or(API_KEY_LAST_USED_AT.lt(threshold)))
            .execute()
    }

    private fun Record.toApiKey(): ApiKey = ApiKey(
        id = get(ID),
        userId = get(USER_ID),
        name = get(KEY_NAME),
        keyPrefix = get(KEY_PREFIX),
        keyHash = get(KEY_HASH),
        lastUsedAt = localDateTime(API_KEY_LAST_USED_AT),
        expiresAt = localDateTime(KEY_EXPIRES_AT),
        revokedAt = localDateTime(REVOKED_AT),
        createdAt = localDateTime(com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT),
    )
}
