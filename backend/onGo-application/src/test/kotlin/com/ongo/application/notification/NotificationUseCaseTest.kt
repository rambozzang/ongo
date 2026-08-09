package com.ongo.application.notification

import com.ongo.domain.notification.NotificationRepository
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class NotificationUseCaseTest {

    private val notificationRepository = mockk<NotificationRepository>()
    private lateinit var useCase: NotificationUseCase

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        useCase = NotificationUseCase(notificationRepository)
    }

    @Test
    fun `deleteAllNotifications delegates the authenticated user id to the repository`() {
        every { notificationRepository.deleteAllByUserId(42L) } just runs

        useCase.deleteAllNotifications(42L)

        verify(exactly = 1) { notificationRepository.deleteAllByUserId(42L) }
        verify(exactly = 0) { notificationRepository.deleteAllByUserId(7L) }
    }
}
