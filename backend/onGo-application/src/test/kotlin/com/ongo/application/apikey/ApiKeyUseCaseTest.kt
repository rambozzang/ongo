package com.ongo.application.apikey

import com.ongo.application.apikey.dto.CreateApiKeyRequest
import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.ForbiddenException
import com.ongo.domain.apikey.ApiKey
import com.ongo.domain.apikey.ApiKeyRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class ApiKeyUseCaseTest {
    private val repository = mockk<ApiKeyRepository>()
    private val useCase = ApiKeyUseCase(repository)

    @Test
    fun `create returns raw secret once and persists only its digest`() {
        every { repository.countActiveByUserId(10L) } returns 0
        every { repository.save(any()) } answers { (args[0] as ApiKey).copy(id = 42L, createdAt = LocalDateTime.now()) }

        val response = useCase.create(10L, CreateApiKeyRequest("Zapier publishing"))

        assertTrue(response.token!!.startsWith("og_live_"))
        assertNotEquals(response.token, response.keyPrefix)
        verify { repository.save(match { it.keyHash != response.token && it.keyHash.length == 64 && it.userId == 10L }) }
    }

    @Test
    fun `expiry must be in the future`() {
        every { repository.countActiveByUserId(10L) } returns 0

        assertThrows(BusinessException::class.java) {
            useCase.create(10L, CreateApiKeyRequest("expired", LocalDateTime.now().minusMinutes(1)))
        }
    }

    @Test
    fun `revoke is owner scoped`() {
        every { repository.findById(42L) } returns ApiKey(id = 42L, userId = 99L, name = "other", keyPrefix = "og_live_x", keyHash = "hash")

        assertThrows(ForbiddenException::class.java) { useCase.revoke(10L, 42L) }
        verify(exactly = 0) { repository.revoke(any(), any()) }
    }

    @Test
    fun `already revoked key cannot be revoked twice`() {
        every { repository.findById(42L) } returns ApiKey(id = 42L, userId = 10L, name = "key", keyPrefix = "og_live_x", keyHash = "hash")
        every { repository.revoke(42L, any()) } returns false

        assertThrows(BusinessException::class.java) { useCase.revoke(10L, 42L) }
    }
}
