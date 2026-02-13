package com.ongo.domain.settings

interface UserSettingsRepository {
    fun findByUserId(userId: Long): UserSettings?
    fun save(settings: UserSettings): UserSettings
    fun update(settings: UserSettings): UserSettings
}
