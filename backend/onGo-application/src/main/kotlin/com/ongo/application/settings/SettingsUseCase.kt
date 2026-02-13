package com.ongo.application.settings

import com.ongo.application.settings.dto.*
import com.ongo.common.enums.Platform
import com.ongo.common.enums.Visibility
import com.ongo.domain.settings.UserSettings
import com.ongo.domain.settings.UserSettingsRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SettingsUseCase(
    private val userSettingsRepository: UserSettingsRepository,
) {

    fun getSettings(userId: Long): SettingsResponse {
        val settings = userSettingsRepository.findByUserId(userId)
            ?: createDefaultSettings(userId)
        return settings.toResponse()
    }

    @Transactional
    fun updateSettings(userId: Long, request: UpdateSettingsRequest): SettingsResponse {
        val existing = userSettingsRepository.findByUserId(userId)
        val updated = if (existing != null) {
            userSettingsRepository.update(
                existing.copy(
                    defaultVisibility = request.defaultVisibility?.let { Visibility.valueOf(it) } ?: existing.defaultVisibility,
                    defaultPlatforms = request.defaultPlatforms?.map { Platform.valueOf(it) } ?: existing.defaultPlatforms,
                    defaultAiTone = request.defaultAiTone ?: existing.defaultAiTone,
                    notificationUpload = request.notificationUpload ?: existing.notificationUpload,
                    notificationComment = request.notificationComment ?: existing.notificationComment,
                    notificationCreditThreshold = request.notificationCreditThreshold ?: existing.notificationCreditThreshold,
                    notificationScheduleReminder = request.notificationScheduleReminder ?: existing.notificationScheduleReminder,
                )
            )
        } else {
            val settings = UserSettings(
                userId = userId,
                defaultVisibility = request.defaultVisibility?.let { Visibility.valueOf(it) } ?: Visibility.PUBLIC,
                defaultPlatforms = request.defaultPlatforms?.map { Platform.valueOf(it) } ?: emptyList(),
                defaultAiTone = request.defaultAiTone ?: "friendly",
                notificationUpload = request.notificationUpload ?: true,
                notificationComment = request.notificationComment ?: "realtime",
                notificationCreditThreshold = request.notificationCreditThreshold ?: 20,
                notificationScheduleReminder = request.notificationScheduleReminder ?: 60,
            )
            userSettingsRepository.save(settings)
        }
        return updated.toResponse()
    }

    @Transactional
    fun updateNotifications(userId: Long, request: UpdateNotificationsRequest): SettingsResponse {
        val existing = userSettingsRepository.findByUserId(userId)
        val updated = if (existing != null) {
            userSettingsRepository.update(
                existing.copy(
                    notificationUpload = request.uploadEmail,
                    notificationComment = request.commentFrequency,
                    notificationCreditThreshold = request.creditThreshold,
                    notificationScheduleReminder = if (request.scheduleReminder1h) 60 else if (request.scheduleReminder30m) 30 else 0,
                )
            )
        } else {
            val settings = UserSettings(
                userId = userId,
                notificationUpload = request.uploadEmail,
                notificationComment = request.commentFrequency,
                notificationCreditThreshold = request.creditThreshold,
                notificationScheduleReminder = if (request.scheduleReminder1h) 60 else if (request.scheduleReminder30m) 30 else 0,
            )
            userSettingsRepository.save(settings)
        }
        return updated.toResponse()
    }

    @Transactional
    fun updateDefaults(userId: Long, request: UpdateDefaultsRequest): SettingsResponse {
        val existing = userSettingsRepository.findByUserId(userId)
        val updated = if (existing != null) {
            userSettingsRepository.update(
                existing.copy(
                    defaultVisibility = Visibility.valueOf(request.visibility),
                    defaultPlatforms = request.platforms.map { Platform.valueOf(it) },
                    defaultAiTone = request.aiTone,
                )
            )
        } else {
            val settings = UserSettings(
                userId = userId,
                defaultVisibility = Visibility.valueOf(request.visibility),
                defaultPlatforms = request.platforms.map { Platform.valueOf(it) },
                defaultAiTone = request.aiTone,
            )
            userSettingsRepository.save(settings)
        }
        return updated.toResponse()
    }

    private fun createDefaultSettings(userId: Long): UserSettings {
        return userSettingsRepository.save(UserSettings(userId = userId))
    }

    private fun UserSettings.toResponse(): SettingsResponse = SettingsResponse(
        defaultVisibility = defaultVisibility.name,
        defaultPlatforms = defaultPlatforms.map { it.name },
        defaultAiTone = defaultAiTone,
        notificationUpload = notificationUpload,
        notificationComment = notificationComment,
        notificationCreditThreshold = notificationCreditThreshold,
        notificationScheduleReminder = notificationScheduleReminder,
    )
}
