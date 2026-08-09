package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.publicoauth.PublicOAuthApp
import com.ongo.domain.publicoauth.PublicOAuthAppRepository
import com.ongo.domain.publicoauth.PublicOAuthAuthorizationCode
import com.ongo.domain.publicoauth.PublicOAuthAuthorizationCodeRepository
import com.ongo.domain.publicoauth.PublicOAuthToken
import com.ongo.domain.publicoauth.PublicOAuthTokenRepository
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

private object PublicOAuthFields {
    val ID = DSL.field("id", Long::class.java)
    val OWNER_ID = DSL.field("owner_id", Long::class.java)
    val APP_ID = DSL.field("app_id", Long::class.java)
    val USER_ID = DSL.field("user_id", Long::class.java)
    val CLIENT_ID = DSL.field("client_id", String::class.java)
    val CLIENT_SECRET_HASH = DSL.field("client_secret_hash", String::class.java)
    val APP_NAME = DSL.field("name", String::class.java)
    val DESCRIPTION = DSL.field("description", String::class.java)
    val PROFILE_PICTURE_URL = DSL.field("profile_picture_url", String::class.java)
    val REDIRECT_URI = DSL.field("redirect_uri", String::class.java)
    val CODE_HASH = DSL.field("code_hash", String::class.java)
    val STATE = DSL.field("state", String::class.java)
    val EXPIRES_AT = DSL.field("expires_at", LocalDateTime::class.java)
    val CONSUMED_AT = DSL.field("consumed_at", LocalDateTime::class.java)
    val TOKEN_PREFIX = DSL.field("token_prefix", String::class.java)
    val TOKEN_HASH = DSL.field("token_hash", String::class.java)
    val REVOKED_AT = DSL.field("revoked_at", LocalDateTime::class.java)
    val CREATED_AT = DSL.field("created_at", LocalDateTime::class.java)
    val UPDATED_AT = DSL.field("updated_at", LocalDateTime::class.java)
}

@Repository
class PublicOAuthAppJooqRepository(
    private val dsl: DSLContext,
) : PublicOAuthAppRepository {
    private val table = Tables.PUBLIC_OAUTH_APPS

    override fun findById(id: Long): PublicOAuthApp? =
        dsl.select().from(table).where(PublicOAuthFields.ID.eq(id)).fetchOne()?.toApp()

    override fun findByClientId(clientId: String): PublicOAuthApp? =
        dsl.select().from(table).where(PublicOAuthFields.CLIENT_ID.eq(clientId)).fetchOne()?.toApp()

    override fun findByOwnerId(ownerId: Long): List<PublicOAuthApp> =
        dsl.select().from(table)
            .where(PublicOAuthFields.OWNER_ID.eq(ownerId))
            .orderBy(PublicOAuthFields.CREATED_AT.desc())
            .fetch().map { it.toApp() }

    override fun save(app: PublicOAuthApp): PublicOAuthApp {
        val id = dsl.insertInto(table)
            .set(PublicOAuthFields.OWNER_ID, app.ownerId)
            .set(PublicOAuthFields.CLIENT_ID, app.clientId)
            .set(PublicOAuthFields.CLIENT_SECRET_HASH, app.clientSecretHash)
            .set(PublicOAuthFields.APP_NAME, app.name)
            .set(PublicOAuthFields.DESCRIPTION, app.description)
            .set(PublicOAuthFields.PROFILE_PICTURE_URL, app.profilePictureUrl)
            .set(PublicOAuthFields.REDIRECT_URI, app.redirectUri)
            .returningResult(PublicOAuthFields.ID)
            .fetchOne()!!.get(PublicOAuthFields.ID)
        return findById(id)!!
    }

    override fun update(app: PublicOAuthApp): PublicOAuthApp {
        dsl.update(table)
            .set(PublicOAuthFields.APP_NAME, app.name)
            .set(PublicOAuthFields.DESCRIPTION, app.description)
            .set(PublicOAuthFields.PROFILE_PICTURE_URL, app.profilePictureUrl)
            .set(PublicOAuthFields.REDIRECT_URI, app.redirectUri)
            .set(PublicOAuthFields.UPDATED_AT, LocalDateTime.now())
            .where(PublicOAuthFields.ID.eq(app.id))
            .execute()
        return findById(requireNotNull(app.id))!!
    }

    override fun rotateSecret(id: Long, secretHash: String, updatedAt: LocalDateTime): Boolean =
        dsl.update(table)
            .set(PublicOAuthFields.CLIENT_SECRET_HASH, secretHash)
            .set(PublicOAuthFields.UPDATED_AT, updatedAt)
            .where(PublicOAuthFields.ID.eq(id))
            .and(PublicOAuthFields.REVOKED_AT.isNull)
            .execute() == 1

    override fun revoke(id: Long, ownerId: Long, revokedAt: LocalDateTime): Boolean =
        dsl.update(table)
            .set(PublicOAuthFields.REVOKED_AT, revokedAt)
            .set(PublicOAuthFields.UPDATED_AT, revokedAt)
            .where(PublicOAuthFields.ID.eq(id))
            .and(PublicOAuthFields.OWNER_ID.eq(ownerId))
            .and(PublicOAuthFields.REVOKED_AT.isNull)
            .execute() == 1

    private fun Record.toApp() = PublicOAuthApp(
        id = get(PublicOAuthFields.ID),
        ownerId = get(PublicOAuthFields.OWNER_ID),
        clientId = get(PublicOAuthFields.CLIENT_ID),
        clientSecretHash = get(PublicOAuthFields.CLIENT_SECRET_HASH),
        name = get(PublicOAuthFields.APP_NAME),
        description = get(PublicOAuthFields.DESCRIPTION),
        profilePictureUrl = get(PublicOAuthFields.PROFILE_PICTURE_URL),
        redirectUri = get(PublicOAuthFields.REDIRECT_URI),
        revokedAt = localDateTime(PublicOAuthFields.REVOKED_AT),
        createdAt = localDateTime(PublicOAuthFields.CREATED_AT),
        updatedAt = localDateTime(PublicOAuthFields.UPDATED_AT),
    )
}

@Repository
class PublicOAuthAuthorizationCodeJooqRepository(
    private val dsl: DSLContext,
) : PublicOAuthAuthorizationCodeRepository {
    private val table = Tables.PUBLIC_OAUTH_AUTHORIZATION_CODES

    override fun save(code: PublicOAuthAuthorizationCode): PublicOAuthAuthorizationCode {
        val id = dsl.insertInto(table)
            .set(PublicOAuthFields.APP_ID, code.appId)
            .set(PublicOAuthFields.USER_ID, code.userId)
            .set(PublicOAuthFields.CODE_HASH, code.codeHash)
            .set(PublicOAuthFields.REDIRECT_URI, code.redirectUri)
            .set(PublicOAuthFields.STATE, code.state)
            .set(PublicOAuthFields.EXPIRES_AT, code.expiresAt)
            .returningResult(PublicOAuthFields.ID)
            .fetchOne()!!.get(PublicOAuthFields.ID)
        return dsl.select().from(table).where(PublicOAuthFields.ID.eq(id)).fetchOne()!!.toCode()
    }

    override fun consume(codeHash: String, now: LocalDateTime): PublicOAuthAuthorizationCode? {
        // The use case is transactional; FOR UPDATE makes a concurrent exchange
        // observe the consumed marker and prevents two access tokens for one code.
        val record = dsl.select().from(table)
            .where(PublicOAuthFields.CODE_HASH.eq(codeHash))
            .and(PublicOAuthFields.CONSUMED_AT.isNull)
            .and(PublicOAuthFields.EXPIRES_AT.gt(now))
            .forUpdate()
            .fetchOne() ?: return null
        val updated = dsl.update(table)
            .set(PublicOAuthFields.CONSUMED_AT, now)
            .where(PublicOAuthFields.ID.eq(record.get(PublicOAuthFields.ID)))
            .and(PublicOAuthFields.CONSUMED_AT.isNull)
            .execute()
        return if (updated == 1) record.toCode().copy(consumedAt = now) else null
    }

    private fun Record.toCode() = PublicOAuthAuthorizationCode(
        id = get(PublicOAuthFields.ID),
        appId = get(PublicOAuthFields.APP_ID),
        userId = get(PublicOAuthFields.USER_ID),
        codeHash = get(PublicOAuthFields.CODE_HASH),
        redirectUri = get(PublicOAuthFields.REDIRECT_URI),
        state = get(PublicOAuthFields.STATE),
        expiresAt = get(PublicOAuthFields.EXPIRES_AT),
        consumedAt = localDateTime(PublicOAuthFields.CONSUMED_AT),
        createdAt = localDateTime(PublicOAuthFields.CREATED_AT),
    )
}

@Repository
class PublicOAuthTokenJooqRepository(
    private val dsl: DSLContext,
) : PublicOAuthTokenRepository {
    private val table = Tables.PUBLIC_OAUTH_TOKENS

    override fun findActiveByHash(tokenHash: String): PublicOAuthToken? =
        dsl.select().from(table)
            .where(PublicOAuthFields.TOKEN_HASH.eq(tokenHash))
            .and(PublicOAuthFields.REVOKED_AT.isNull)
            .fetchOne()?.toToken()

    override fun save(token: PublicOAuthToken): PublicOAuthToken {
        val id = dsl.insertInto(table)
            .set(PublicOAuthFields.APP_ID, token.appId)
            .set(PublicOAuthFields.USER_ID, token.userId)
            .set(PublicOAuthFields.TOKEN_PREFIX, token.tokenPrefix)
            .set(PublicOAuthFields.TOKEN_HASH, token.tokenHash)
            .returningResult(PublicOAuthFields.ID)
            .fetchOne()!!.get(PublicOAuthFields.ID)
        return dsl.select().from(table).where(PublicOAuthFields.ID.eq(id)).fetchOne()!!.toToken()
    }

    override fun revokeByApp(appId: Long, revokedAt: LocalDateTime): Int =
        dsl.update(table)
            .set(PublicOAuthFields.REVOKED_AT, revokedAt)
            .where(PublicOAuthFields.APP_ID.eq(appId))
            .and(PublicOAuthFields.REVOKED_AT.isNull)
            .execute()

    override fun revokeByIdAndUser(id: Long, userId: Long, revokedAt: LocalDateTime): Boolean =
        dsl.update(table)
            .set(PublicOAuthFields.REVOKED_AT, revokedAt)
            .where(PublicOAuthFields.ID.eq(id))
            .and(PublicOAuthFields.USER_ID.eq(userId))
            .and(PublicOAuthFields.REVOKED_AT.isNull)
            .execute() == 1

    override fun findByUserId(userId: Long): List<PublicOAuthToken> =
        dsl.select().from(table)
            .where(PublicOAuthFields.USER_ID.eq(userId))
            .orderBy(PublicOAuthFields.CREATED_AT.desc())
            .fetch().map { it.toToken() }

    private fun Record.toToken() = PublicOAuthToken(
        id = get(PublicOAuthFields.ID),
        appId = get(PublicOAuthFields.APP_ID),
        userId = get(PublicOAuthFields.USER_ID),
        tokenPrefix = get(PublicOAuthFields.TOKEN_PREFIX),
        tokenHash = get(PublicOAuthFields.TOKEN_HASH),
        revokedAt = localDateTime(PublicOAuthFields.REVOKED_AT),
        createdAt = localDateTime(PublicOAuthFields.CREATED_AT),
    )
}
