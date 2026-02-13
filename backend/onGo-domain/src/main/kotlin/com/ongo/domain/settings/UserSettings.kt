package com.ongo.domain.settings

import com.ongo.common.enums.Platform
import com.ongo.common.enums.Visibility
import java.time.LocalDateTime

data class UserSettings(
    val id: Long? = null,
    val userId: Long,
    val defaultVisibility: Visibility = Visibility.PUBLIC,
    val defaultPlatforms: List<Platform> = emptyList(),
    val defaultAiTone: String = "friendly",
    val notificationUpload: Boolean = true,
    val notificationComment: String = "realtime",
    val notificationCreditThreshold: Int = 20,
    val notificationScheduleReminder: Int = 60,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)
