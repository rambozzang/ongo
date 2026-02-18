package com.ongo.infrastructure.persistence.jooq

import com.ongo.common.enums.AiProvider
import com.ongo.common.enums.Platform
import com.ongo.common.enums.Visibility
import com.ongo.domain.settings.UserSettings
import com.ongo.domain.settings.UserSettingsRepository
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.DEFAULT_AI_PROVIDER
import com.ongo.infrastructure.persistence.jooq.Fields.DEFAULT_AI_TONE
import com.ongo.infrastructure.persistence.jooq.Fields.DEFAULT_VISIBILITY
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.NOTIFICATION_COMMENT
import com.ongo.infrastructure.persistence.jooq.Fields.NOTIFICATION_CREDIT_THRESHOLD
import com.ongo.infrastructure.persistence.jooq.Fields.NOTIFICATION_SCHEDULE_REMINDER
import com.ongo.infrastructure.persistence.jooq.Fields.NOTIFICATION_UPLOAD
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Tables.USER_SETTINGS
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.jooq.DSLContext
import org.jooq.JSONB
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository

@Repository
class UserSettingsJooqRepository(
    private val dsl: DSLContext,
    private val objectMapper: ObjectMapper,
) : UserSettingsRepository {

    override fun findByUserId(userId: Long): UserSettings? =
        dsl.select()
            .from(USER_SETTINGS)
            .where(USER_ID.eq(userId))
            .fetchOne()
            ?.toUserSettings()

    private fun findById(id: Long): UserSettings? =
        dsl.select()
            .from(USER_SETTINGS)
            .where(ID.eq(id))
            .fetchOne()
            ?.toUserSettings()

    override fun save(settings: UserSettings): UserSettings {
        val platformsJson = JSONB.jsonb(objectMapper.writeValueAsString(settings.defaultPlatforms.map { it.name }))

        val id = dsl.insertInto(USER_SETTINGS)
            .set(USER_ID, settings.userId)
            .set(DEFAULT_VISIBILITY, settings.defaultVisibility.name)
            .set(DSL.field("default_platforms", JSONB::class.java), platformsJson)
            .set(DEFAULT_AI_TONE, settings.defaultAiTone)
            .set(DEFAULT_AI_PROVIDER, settings.defaultAiProvider.name)
            .set(NOTIFICATION_UPLOAD, settings.notificationUpload)
            .set(NOTIFICATION_COMMENT, settings.notificationComment)
            .set(NOTIFICATION_CREDIT_THRESHOLD, settings.notificationCreditThreshold)
            .set(NOTIFICATION_SCHEDULE_REMINDER, settings.notificationScheduleReminder)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return findById(id)!!
    }

    override fun update(settings: UserSettings): UserSettings {
        val platformsJson = JSONB.jsonb(objectMapper.writeValueAsString(settings.defaultPlatforms.map { it.name }))

        dsl.update(USER_SETTINGS)
            .set(DEFAULT_VISIBILITY, settings.defaultVisibility.name)
            .set(DSL.field("default_platforms", JSONB::class.java), platformsJson)
            .set(DEFAULT_AI_TONE, settings.defaultAiTone)
            .set(DEFAULT_AI_PROVIDER, settings.defaultAiProvider.name)
            .set(NOTIFICATION_UPLOAD, settings.notificationUpload)
            .set(NOTIFICATION_COMMENT, settings.notificationComment)
            .set(NOTIFICATION_CREDIT_THRESHOLD, settings.notificationCreditThreshold)
            .set(NOTIFICATION_SCHEDULE_REMINDER, settings.notificationScheduleReminder)
            .where(ID.eq(settings.id))
            .execute()

        return findById(settings.id!!)!!
    }

    private fun Record.toUserSettings(): UserSettings {
        val platformsRaw = get("default_platforms")
        val defaultPlatforms: List<Platform> = when (platformsRaw) {
            is JSONB -> parsePlatforms(platformsRaw.data())
            is String -> parsePlatforms(platformsRaw)
            else -> emptyList()
        }

        val visibilityStr = get(DEFAULT_VISIBILITY) ?: "PUBLIC"
        return UserSettings(
            id = get(ID),
            userId = get(USER_ID),
            defaultVisibility = try { Visibility.valueOf(visibilityStr) } catch (_: Exception) { Visibility.PUBLIC },
            defaultPlatforms = defaultPlatforms,
            defaultAiTone = get(DEFAULT_AI_TONE),
            defaultAiProvider = AiProvider.fromString(get(DEFAULT_AI_PROVIDER)),
            notificationUpload = get(NOTIFICATION_UPLOAD),
            notificationComment = get(NOTIFICATION_COMMENT),
            notificationCreditThreshold = get(NOTIFICATION_CREDIT_THRESHOLD),
            notificationScheduleReminder = get(NOTIFICATION_SCHEDULE_REMINDER),
            createdAt = localDateTime(CREATED_AT),
            updatedAt = localDateTime(UPDATED_AT),
        )
    }

    private fun parsePlatforms(json: String): List<Platform> {
        if (json.isBlank() || json == "null") return emptyList()
        return try {
            val names: List<String> = objectMapper.readValue(json)
            names.map { Platform.valueOf(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
