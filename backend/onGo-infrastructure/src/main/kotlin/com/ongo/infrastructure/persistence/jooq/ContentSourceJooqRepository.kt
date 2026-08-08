package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.contentsource.ContentSource
import com.ongo.domain.contentsource.ContentSourceRepository
import com.ongo.domain.contentsource.ContentSourceStatus
import com.ongo.domain.contentsource.ContentSourceType
import com.ongo.infrastructure.persistence.jooq.Fields.ACCESS_TOKEN
import com.ongo.infrastructure.persistence.jooq.Fields.ACCOUNT_DISPLAY_NAME
import com.ongo.infrastructure.persistence.jooq.Fields.ACCOUNT_EMAIL
import com.ongo.infrastructure.persistence.jooq.Fields.CONNECTED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.EXTERNAL_ACCOUNT_ID
import com.ongo.infrastructure.persistence.jooq.Fields.GRANTED_SCOPES
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.LAST_ERROR
import com.ongo.infrastructure.persistence.jooq.Fields.CONTENT_SOURCE_LAST_USED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.REFRESH_TOKEN
import com.ongo.infrastructure.persistence.jooq.Fields.SOURCE_TYPE
import com.ongo.infrastructure.persistence.jooq.Fields.SOURCE_TYPE_TEXT
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.TOKEN_EXPIRES_AT
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Tables.USER_CONTENT_SOURCES
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

@Repository
class ContentSourceJooqRepository(
    private val dsl: DSLContext,
) : ContentSourceRepository {

    override fun findById(id: Long): ContentSource? =
        dsl.select().from(USER_CONTENT_SOURCES).where(ID.eq(id))
            .fetchOne()?.toContentSource()

    override fun findByUserAndType(userId: Long, type: ContentSourceType): ContentSource? =
        dsl.select().from(USER_CONTENT_SOURCES)
            .where(USER_ID.eq(userId))
            .and(SOURCE_TYPE_TEXT.eq(type.name))
            .fetchOne()?.toContentSource()

    override fun findAllByUser(userId: Long): List<ContentSource> =
        dsl.select().from(USER_CONTENT_SOURCES)
            .where(USER_ID.eq(userId))
            .orderBy(CONNECTED_AT.desc())
            .fetch().map { it.toContentSource() }

    override fun save(source: ContentSource): ContentSource {
        val now = LocalDateTime.now()
        val expiresAtLdt = source.tokenExpiresAt?.atZone(ZoneOffset.UTC)?.toLocalDateTime()
        val id = if (source.id == 0L) {
            dsl.insertInto(USER_CONTENT_SOURCES)
                .set(USER_ID, source.userId)
                .set(SOURCE_TYPE, source.sourceType.name)
                .set(EXTERNAL_ACCOUNT_ID, source.externalAccountId)
                .set(ACCOUNT_EMAIL, source.accountEmail)
                .set(ACCOUNT_DISPLAY_NAME, source.accountDisplayName)
                .set(ACCESS_TOKEN, source.accessTokenEncrypted)
                .set(REFRESH_TOKEN, source.refreshTokenEncrypted)
                .set(TOKEN_EXPIRES_AT, expiresAtLdt)
                .set(GRANTED_SCOPES, source.grantedScopes)
                .set(STATUS, source.status.name)
                .set(UPDATED_AT, now)
                .returningResult(ID).fetchOne()!!.get(ID)
        } else {
            dsl.update(USER_CONTENT_SOURCES)
                .set(ACCOUNT_EMAIL, source.accountEmail)
                .set(ACCOUNT_DISPLAY_NAME, source.accountDisplayName)
                .set(EXTERNAL_ACCOUNT_ID, source.externalAccountId)
                .set(ACCESS_TOKEN, source.accessTokenEncrypted)
                .set(REFRESH_TOKEN, source.refreshTokenEncrypted)
                .set(TOKEN_EXPIRES_AT, expiresAtLdt)
                .set(GRANTED_SCOPES, source.grantedScopes)
                .set(STATUS, source.status.name)
                .set(LAST_ERROR, source.lastError)
                .set(UPDATED_AT, now)
                .where(ID.eq(source.id)).execute()
            source.id
        }
        return findById(id)!!
    }

    override fun updateStatus(id: Long, status: ContentSourceStatus, lastError: String?) {
        dsl.update(USER_CONTENT_SOURCES)
            .set(STATUS, status.name)
            .set(LAST_ERROR, lastError)
            .set(UPDATED_AT, LocalDateTime.now())
            .where(ID.eq(id)).execute()
    }

    override fun updateTokens(id: Long, accessTokenEncrypted: String, refreshTokenEncrypted: String?, expiresAt: Instant?) {
        dsl.update(USER_CONTENT_SOURCES)
            .set(ACCESS_TOKEN, accessTokenEncrypted)
            .set(REFRESH_TOKEN, refreshTokenEncrypted)
            .set(TOKEN_EXPIRES_AT, expiresAt?.atZone(ZoneOffset.UTC)?.toLocalDateTime())
            .set(STATUS, ContentSourceStatus.ACTIVE.name)
            .set(LAST_ERROR, null as String?)
            .set(UPDATED_AT, LocalDateTime.now())
            .where(ID.eq(id)).execute()
    }

    override fun markUsed(id: Long) {
        val now = LocalDateTime.now()
        dsl.update(USER_CONTENT_SOURCES)
            .set(CONTENT_SOURCE_LAST_USED_AT, now)
            .set(UPDATED_AT, now)
            .where(ID.eq(id)).execute()
    }

    override fun delete(id: Long) {
        dsl.deleteFrom(USER_CONTENT_SOURCES).where(ID.eq(id)).execute()
    }

    private fun Record.toContentSource(): ContentSource {
        val sourceTypeStr = get(SOURCE_TYPE) ?: "GOOGLE_DRIVE"
        val statusStr = get(STATUS) ?: "ACTIVE"
        return ContentSource(
            id = get(ID),
            userId = get(USER_ID),
            sourceType = try { ContentSourceType.valueOf(sourceTypeStr) } catch (_: Exception) { ContentSourceType.GOOGLE_DRIVE },
            externalAccountId = get(EXTERNAL_ACCOUNT_ID),
            accountEmail = get(ACCOUNT_EMAIL),
            accountDisplayName = get(ACCOUNT_DISPLAY_NAME),
            accessTokenEncrypted = get(ACCESS_TOKEN),
            refreshTokenEncrypted = get(REFRESH_TOKEN),
            tokenExpiresAt = localDateTime(TOKEN_EXPIRES_AT)?.atZone(ZoneOffset.UTC)?.toInstant(),
            grantedScopes = get(GRANTED_SCOPES),
            status = try { ContentSourceStatus.valueOf(statusStr) } catch (_: Exception) { ContentSourceStatus.ACTIVE },
            lastError = get(LAST_ERROR),
            connectedAt = localDateTime(CONNECTED_AT)!!.atZone(ZoneOffset.UTC).toInstant(),
            lastUsedAt = localDateTime(CONTENT_SOURCE_LAST_USED_AT)?.atZone(ZoneOffset.UTC)?.toInstant(),
            updatedAt = localDateTime(UPDATED_AT)!!.atZone(ZoneOffset.UTC).toInstant(),
        )
    }
}
