package com.ongo.application.publicoauth

import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.publicoauth.PublicOAuthApp
import com.ongo.domain.publicoauth.PublicOAuthAppRepository
import com.ongo.domain.publicoauth.PublicOAuthAuthorizationCode
import com.ongo.domain.publicoauth.PublicOAuthAuthorizationCodeRepository
import com.ongo.domain.publicoauth.PublicOAuthToken
import com.ongo.domain.publicoauth.PublicOAuthTokenRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDateTime
import java.util.Base64

@Service
class PublicOAuthAppUseCase(
    private val appRepository: PublicOAuthAppRepository,
    private val codeRepository: PublicOAuthAuthorizationCodeRepository,
    private val tokenRepository: PublicOAuthTokenRepository,
) {
    companion object {
        private const val CLIENT_ID_PREFIX = "pca_"
        private const val CLIENT_SECRET_PREFIX = "pcs_"
        private const val ACCESS_TOKEN_PREFIX = "pos_"
        private const val AUTHORIZATION_CODE_MINUTES = 10L
        private val RANDOM = SecureRandom()
    }

    fun listApps(ownerId: Long): List<PublicOAuthAppResponse> =
        appRepository.findByOwnerId(ownerId).map { it.toResponse() }

    @Transactional
    fun createApp(ownerId: Long, request: CreatePublicOAuthAppRequest): PublicOAuthAppCreatedResponse {
        val name = request.name.trim()
        require(name.isNotBlank() && name.length <= 120) { "앱 이름은 1~120자여야 합니다" }
        val description = request.description?.trim()?.takeIf(String::isNotBlank)?.also {
            require(it.length <= 500) { "앱 설명은 500자 이하여야 합니다" }
        }
        val profilePictureUrl = request.profilePictureUrl?.trim()?.takeIf(String::isNotBlank)?.also(::validateOptionalUrl)
        val redirectUri = validateRedirectUri(request.redirectUri)
        val clientId = uniqueClientId()
        val clientSecret = CLIENT_SECRET_PREFIX + randomPart(36)
        val app = appRepository.save(
            PublicOAuthApp(
                ownerId = ownerId,
                clientId = clientId,
                clientSecretHash = sha256(clientSecret),
                name = name,
                description = description,
                profilePictureUrl = profilePictureUrl,
                redirectUri = redirectUri,
            )
        )
        return PublicOAuthAppCreatedResponse(app.toResponse(), clientSecret)
    }

    @Transactional
    fun rotateSecret(ownerId: Long, id: Long): PublicOAuthAppCreatedResponse {
        val app = ownedApp(ownerId, id)
        val clientSecret = CLIENT_SECRET_PREFIX + randomPart(36)
        check(appRepository.rotateSecret(id, sha256(clientSecret), LocalDateTime.now())) {
            "OAuth 앱 시크릿을 교체하지 못했습니다"
        }
        return PublicOAuthAppCreatedResponse(app.copy(updatedAt = LocalDateTime.now()).toResponse(), clientSecret)
    }

    @Transactional
    fun deleteApp(ownerId: Long, id: Long) {
        val app = ownedApp(ownerId, id)
        if (!appRepository.revoke(id, ownerId, LocalDateTime.now())) {
            throw NotFoundException("OAuth 앱", id)
        }
        tokenRepository.revokeByApp(requireNotNull(app.id), LocalDateTime.now())
    }

    fun authorizationRequest(clientId: String, responseType: String): PublicOAuthAuthorizationRequest {
        require(responseType == "code") { "response_type은 code만 지원합니다" }
        val app = activeApp(clientId)
        return PublicOAuthAuthorizationRequest(
            clientId = app.clientId,
            name = app.name,
            description = app.description,
            profilePictureUrl = app.profilePictureUrl,
            redirectUri = app.redirectUri,
        )
    }

    /** Creates a code or a safe denial redirect after the logged-in user decides. */
    @Transactional
    fun decideAuthorization(
        userId: Long,
        clientId: String,
        responseType: String,
        state: String?,
        approved: Boolean,
    ): String {
        val request = authorizationRequest(clientId, responseType)
        if (!approved) return redirect(request.redirectUri, mapOf("error" to "access_denied", "state" to state))

        val code = randomPart(36)
        codeRepository.save(
            PublicOAuthAuthorizationCode(
                appId = requireNotNull(activeApp(clientId).id),
                userId = userId,
                codeHash = sha256(code),
                redirectUri = request.redirectUri,
                state = state?.take(512),
                expiresAt = LocalDateTime.now().plusMinutes(AUTHORIZATION_CODE_MINUTES),
            )
        )
        return redirect(request.redirectUri, mapOf("code" to code, "state" to state))
    }

    /** Implements Postiz's JSON authorization_code token exchange. */
    @Transactional
    fun exchangeToken(request: PublicOAuthTokenRequest): PublicOAuthTokenResponse {
        require(request.grantType == "authorization_code") { "unsupported_grant_type" }
        val app = activeApp(request.clientId)
        require(secretMatches(request.clientSecret, app.clientSecretHash)) { "invalid_client" }
        require(request.code.isNotBlank()) { "invalid_grant" }

        val code = codeRepository.consume(sha256(request.code), LocalDateTime.now())
            ?: throw IllegalArgumentException("invalid_grant")
        require(code.appId == app.id && code.redirectUri == app.redirectUri) { "invalid_grant" }

        val token = ACCESS_TOKEN_PREFIX + randomPart(48)
        val saved = tokenRepository.save(
            PublicOAuthToken(
                appId = requireNotNull(app.id),
                userId = code.userId,
                tokenPrefix = token.take(20),
                tokenHash = sha256(token),
            )
        )
        return PublicOAuthTokenResponse(
            id = code.userId.toString(),
            cus = null,
            accessToken = token,
            tokenType = "bearer",
            tokenId = saved.id,
        )
    }

    fun listTokens(userId: Long): List<PublicOAuthTokenResponseSummary> =
        tokenRepository.findByUserId(userId).map {
            PublicOAuthTokenResponseSummary(
                id = requireNotNull(it.id),
                appId = it.appId,
                tokenPrefix = it.tokenPrefix,
                createdAt = it.createdAt,
                revokedAt = it.revokedAt,
            )
        }

    @Transactional
    fun revokeToken(userId: Long, id: Long) {
        if (!tokenRepository.revokeByIdAndUser(id, userId, LocalDateTime.now())) {
            throw NotFoundException("OAuth 토큰", id)
        }
    }

    private fun activeApp(clientId: String): PublicOAuthApp =
        appRepository.findByClientId(clientId.trim())
            ?.takeIf { it.revokedAt == null }
            ?: throw IllegalArgumentException("invalid_client")

    private fun ownedApp(ownerId: Long, id: Long): PublicOAuthApp =
        appRepository.findById(id)?.also {
            if (it.ownerId != ownerId) throw ForbiddenException("해당 OAuth 앱에 대한 권한이 없습니다")
        } ?: throw NotFoundException("OAuth 앱", id)

    private fun uniqueClientId(): String {
        repeat(5) {
            val id = CLIENT_ID_PREFIX + randomPart(24)
            if (appRepository.findByClientId(id) == null) return id
        }
        throw IllegalStateException("OAuth client id를 생성하지 못했습니다")
    }

    private fun validateRedirectUri(value: String): String {
        val uri = runCatching { URI(value.trim()) }.getOrElse { throw IllegalArgumentException("redirect_uri가 올바르지 않습니다") }
        require(uri.scheme == "https" || (uri.scheme == "http" && uri.host == "localhost")) {
            "redirect_uri는 HTTPS여야 합니다(로컬 개발은 localhost HTTP 허용)"
        }
        require(!uri.host.isNullOrBlank() && uri.fragment == null && uri.userInfo == null) {
            "redirect_uri에는 호스트가 필요하며 fragment/userinfo를 사용할 수 없습니다"
        }
        return uri.toString()
    }

    private fun validateOptionalUrl(value: String) {
        val uri = runCatching { URI(value) }.getOrElse { throw IllegalArgumentException("profile_picture_url이 올바르지 않습니다") }
        require(uri.scheme == "https" && !uri.host.isNullOrBlank()) { "profile_picture_url은 HTTPS URL이어야 합니다" }
    }

    private fun redirect(base: String, values: Map<String, String?>): String {
        val separator = if (base.contains('?')) '&' else '?'
        val query = values.filterValues { !it.isNullOrBlank() }.entries.joinToString("&") {
            "${urlEncode(it.key)}=${urlEncode(it.value!!)}"
        }
        return if (query.isBlank()) base else base + separator + query
    }

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")

    private fun randomPart(bytes: Int): String {
        val value = ByteArray(bytes)
        RANDOM.nextBytes(value)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value)
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun secretMatches(raw: String, expectedHash: String): Boolean {
        val actual = sha256(raw).toByteArray(StandardCharsets.US_ASCII)
        val expected = expectedHash.toByteArray(StandardCharsets.US_ASCII)
        return MessageDigest.isEqual(actual, expected)
    }

    private fun PublicOAuthApp.toResponse() = PublicOAuthAppResponse(
        id = requireNotNull(id),
        clientId = clientId,
        name = name,
        description = description,
        profilePictureUrl = profilePictureUrl,
        redirectUri = redirectUri,
        revokedAt = revokedAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

data class CreatePublicOAuthAppRequest(
    val name: String,
    val description: String? = null,
    val profilePictureUrl: String? = null,
    val redirectUri: String,
)

data class PublicOAuthAppResponse(
    val id: Long,
    val clientId: String,
    val name: String,
    val description: String?,
    val profilePictureUrl: String?,
    val redirectUri: String,
    val revokedAt: LocalDateTime?,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
)

data class PublicOAuthAppCreatedResponse(
    val app: PublicOAuthAppResponse,
    /** Shown only on create/rotate, matching the Postiz developer-app contract. */
    val clientSecret: String,
)

data class PublicOAuthAuthorizationRequest(
    val clientId: String,
    val name: String,
    val description: String?,
    val profilePictureUrl: String?,
    val redirectUri: String,
)

data class PublicOAuthTokenRequest(
    val grantType: String,
    val code: String,
    val clientId: String,
    val clientSecret: String,
)

data class PublicOAuthTokenResponse(
    val id: String,
    val cus: String?,
    val accessToken: String,
    val tokenType: String,
    val tokenId: Long?,
)

data class PublicOAuthTokenResponseSummary(
    val id: Long,
    val appId: Long,
    val tokenPrefix: String,
    val createdAt: LocalDateTime?,
    val revokedAt: LocalDateTime?,
)
