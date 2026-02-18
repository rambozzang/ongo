package com.ongo.application.settings.dto

data class SettingsResponse(
    val defaultVisibility: String,
    val defaultPlatforms: List<String>,
    val defaultAiTone: String,
    val defaultAiProvider: String,
    val notificationUpload: Boolean,
    val notificationComment: String,
    val notificationCreditThreshold: Int,
    val notificationScheduleReminder: Int,
)

data class UpdateSettingsRequest(
    val defaultVisibility: String? = null,
    val defaultPlatforms: List<String>? = null,
    val defaultAiTone: String? = null,
    val defaultAiProvider: String? = null,
    val notificationUpload: Boolean? = null,
    val notificationComment: String? = null,
    val notificationCreditThreshold: Int? = null,
    val notificationScheduleReminder: Int? = null,
)

data class UpdateNotificationsRequest(
    val uploadEmail: Boolean = true,
    val uploadPush: Boolean = true,
    val commentFrequency: String = "realtime",
    val creditThreshold: Int = 20,
    val scheduleReminder1h: Boolean = true,
    val scheduleReminder30m: Boolean = false,
)

data class UpdateDefaultsRequest(
    val visibility: String = "PUBLIC",
    val platforms: List<String> = listOf("YOUTUBE"),
    val aiTone: String = "FRIENDLY",
    val aiProvider: String = "CLAUDE",
)
