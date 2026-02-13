package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.auth.RefreshTokenInfo
import com.ongo.domain.auth.RefreshTokenPort
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.EXPIRES_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.TOKEN
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Tables.REFRESH_TOKENS
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class RefreshTokenJooqRepository(
    private val dsl: DSLContext,
) : RefreshTokenPort {

    override fun save(userId: Long, token: String, expiresAt: LocalDateTime) {
        dsl.insertInto(REFRESH_TOKENS)
            .set(USER_ID, userId)
            .set(TOKEN, token)
            .set(EXPIRES_AT, expiresAt)
            .execute()
    }

    override fun findByToken(token: String): RefreshTokenInfo? =
        dsl.select()
            .from(REFRESH_TOKENS)
            .where(TOKEN.eq(token))
            .fetchOne()
            ?.toRefreshTokenInfo()

    override fun deleteByToken(token: String) {
        dsl.deleteFrom(REFRESH_TOKENS)
            .where(TOKEN.eq(token))
            .execute()
    }

    override fun deleteByUserId(userId: Long) {
        dsl.deleteFrom(REFRESH_TOKENS)
            .where(USER_ID.eq(userId))
            .execute()
    }

    override fun deleteExpired() {
        dsl.deleteFrom(REFRESH_TOKENS)
            .where(EXPIRES_AT.lessThan(LocalDateTime.now()))
            .execute()
    }

    private fun Record.toRefreshTokenInfo(): RefreshTokenInfo = RefreshTokenInfo(
        id = get(ID),
        userId = get(USER_ID),
        token = get(TOKEN),
        expiresAt = localDateTime(EXPIRES_AT)!!,
        createdAt = localDateTime(CREATED_AT)!!,
    )
}

