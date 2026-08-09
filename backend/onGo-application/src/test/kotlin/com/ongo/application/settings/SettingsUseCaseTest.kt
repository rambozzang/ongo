package com.ongo.application.settings

import com.ongo.application.settings.dto.UpdateNotificationsRequest
import com.ongo.domain.settings.UserSettings
import com.ongo.domain.settings.UserSettingsRepository
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SettingsUseCaseTest {

    private val repository = mockk<UserSettingsRepository>()
    private lateinit var useCase: SettingsUseCase

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        useCase = SettingsUseCase(repository)
    }

    @Test
    fun `notification partial update preserves settings that are not in the request`() {
        val existing = UserSettings(
            id = 3L,
            userId = 42L,
            notificationUpload = false,
            notificationComment = "none",
            notificationCreditThreshold = 7,
            notificationScheduleReminder = 0,
        )
        every { repository.findByUserId(42L) } returns existing
        every { repository.update(any()) } answers { firstArg() }

        val response = useCase.updateNotifications(
            42L,
            UpdateNotificationsRequest(commentFrequency = "realtime"),
        )

        assertEquals("realtime", response.notificationComment)
        verify {
            repository.update(match {
                it.notificationUpload == existing.notificationUpload &&
                    it.notificationCreditThreshold == existing.notificationCreditThreshold &&
                    it.notificationScheduleReminder == existing.notificationScheduleReminder
            })
        }
    }
}
