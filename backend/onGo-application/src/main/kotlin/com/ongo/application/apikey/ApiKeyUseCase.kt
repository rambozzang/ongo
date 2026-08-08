package com.ongo.application.apikey

import com.ongo.application.apikey.dto.ApiKeyResponse
import com.ongo.application.apikey.dto.CreateApiKeyRequest
import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.apikey.ApiKey
import com.ongo.domain.apikey.ApiKeyRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDateTime
import java.util.Base64

@Service
class ApiKeyUseCase(
    private val apiKeyRepository: ApiKeyRepository,
) {
    companion object {
        private const val PREFIX = "og_live_"
        private const val MAX_ACTIVE_KEYS = 20
        private val RANDOM = SecureRandom()
    }

    fun list(userId: Long): List<ApiKeyResponse> =
        apiKeyRepository.findByUserId(userId).map { it.toResponse() }

    @Transactional
    fun create(userId: Long, request: CreateApiKeyRequest): ApiKeyResponse {
        val name = request.name.trim()
        if (name.isBlank() || name.length > 80) {
            throw BusinessException("INVALID_API_KEY_NAME", "API 키 이름은 1~80자여야 합니다.")
        }
        if (apiKeyRepository.countActiveByUserId(userId) >= MAX_ACTIVE_KEYS) {
            throw BusinessException("API_KEY_LIMIT", "활성 API 키는 최대 ${MAX_ACTIVE_KEYS}개까지 만들 수 있습니다.")
        }
        val now = LocalDateTime.now()
        if (request.expiresAt != null && !request.expiresAt.isAfter(now)) {
            throw BusinessException("INVALID_API_KEY_EXPIRY", "만료일은 현재 시각 이후여야 합니다.")
        }

        val secret = PREFIX + randomPart()
        val saved = apiKeyRepository.save(
            ApiKey(
                userId = userId,
                name = name,
                keyPrefix = secret.take(PREFIX.length + 8),
                keyHash = sha256(secret),
                expiresAt = request.expiresAt,
            )
        )
        return saved.toResponse(token = secret)
    }

    @Transactional
    fun revoke(userId: Long, id: Long) {
        val apiKey = apiKeyRepository.findById(id) ?: throw NotFoundException("API 키", id)
        if (apiKey.userId != userId) throw ForbiddenException("해당 API 키에 대한 권한이 없습니다")
        if (!apiKeyRepository.revoke(id, LocalDateTime.now())) {
            throw BusinessException("API_KEY_ALREADY_REVOKED", "이미 폐기된 API 키입니다.")
        }
    }

    fun hash(rawKey: String): String = sha256(rawKey)

    private fun randomPart(): String {
        val bytes = ByteArray(32)
        RANDOM.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun ApiKey.toResponse(token: String? = null) = ApiKeyResponse(
        id = id!!,
        name = name,
        keyPrefix = keyPrefix,
        token = token,
        lastUsedAt = lastUsedAt,
        expiresAt = expiresAt,
        revokedAt = revokedAt,
        createdAt = createdAt,
    )
}
