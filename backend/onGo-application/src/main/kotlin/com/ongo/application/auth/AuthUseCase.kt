package com.ongo.application.auth

import com.ongo.application.auth.dto.AuthResult
import com.ongo.application.auth.dto.UserResult
import com.ongo.common.enums.PlanType
import com.ongo.common.exception.NotFoundException
import com.ongo.common.exception.TokenExpiredException
import com.ongo.common.exception.UnauthorizedException
import com.ongo.domain.auth.AuthTokenPort
import com.ongo.domain.auth.OAuth2Port
import com.ongo.domain.auth.RefreshTokenPort
import com.ongo.domain.credit.AiCredit
import com.ongo.domain.credit.CreditRepository
import com.ongo.domain.settings.UserSettings
import com.ongo.domain.settings.UserSettingsRepository
import com.ongo.domain.subscription.Subscription
import com.ongo.domain.subscription.SubscriptionRepository
import com.ongo.domain.user.User
import com.ongo.domain.user.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 인증 및 회원 관리 비즈니스 로직을 담당하는 UseCase
 */
@Service
class AuthUseCase(
    private val userRepository: UserRepository,
    private val creditRepository: CreditRepository,
    private val userSettingsRepository: UserSettingsRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val authTokenPort: AuthTokenPort,
    private val oAuth2Port: OAuth2Port,
    private val refreshTokenPort: RefreshTokenPort,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 소셜 로그인을 진행합니다.
     * 첫 로그인 시 자동으로 회원가입이 진행됩니다.
     *
     * @param providerName 제공자 명 (google, kakao 등)
     * @param code OAuth 인가 코드
     * @param redirectUri 콜백 URI
     * @return 액세스/리프레시 토큰 및 사용자 정보를 포함한 결과
     */
    @Transactional
    fun socialLogin(providerName: String, code: String, redirectUri: String): AuthResult {

        val provider = oAuth2Port.resolveProvider(providerName)
        val oAuth2UserInfo = oAuth2Port.getUserInfo(provider, code, redirectUri)

        var isNewUser = false
        val user = userRepository.findByProviderAndProviderId(provider, oAuth2UserInfo.providerId)
            ?: run {
                isNewUser = true
                val newUser = userRepository.save(
                    User(
                        email = oAuth2UserInfo.email,
                        name = oAuth2UserInfo.name,
                        profileImageUrl = oAuth2UserInfo.profileImageUrl,
                        provider = provider,
                        providerId = oAuth2UserInfo.providerId,
                    )
                )
                initializeNewUser(newUser)
                log.info("새 사용자 가입: userId={}, provider={}", newUser.id, provider)
                newUser
            }

        val userId = user.id!!
        val accessToken = authTokenPort.generateAccessToken(userId)
        val refreshToken = authTokenPort.generateRefreshToken(userId)

        // Refresh Token DB 저장 (Token Rotation)
        refreshTokenPort.deleteByUserId(userId)
        val expiresAt = LocalDateTime.now().plusNanos(Duration.ofMillis(authTokenPort.getRefreshTokenExpiryMillis()).toNanos())
        refreshTokenPort.save(userId, refreshToken, expiresAt)

        log.info("로그인 성공: userId={}, isNewUser={}", user.id, isNewUser)

        return AuthResult(
            accessToken = accessToken,
            refreshToken = refreshToken,
            user = toUserResult(user),
            isNewUser = isNewUser,
        )
    }

    /**
     * 리프레시 토큰을 사용하여 액세스 토큰을 갱신합니다.
     * Token Rotation 방식이 적용되어 리프레시 토큰도 함께 갱신됩니다.
     */
    @Transactional
    fun refreshToken(refreshToken: String): AuthResult {
        if (!authTokenPort.validateToken(refreshToken)) {
            throw TokenExpiredException("리프레시 토큰이 만료되었습니다")
        }

        val tokenType = authTokenPort.getTokenType(refreshToken)
        if (tokenType != "refresh") {
            throw UnauthorizedException("유효하지 않은 리프레시 토큰입니다")
        }

        // DB에서 Refresh Token 존재 여부 검증
        val storedToken = refreshTokenPort.findByToken(refreshToken)
            ?: throw UnauthorizedException("이미 사용되었거나 무효화된 리프레시 토큰입니다")

        val userId = authTokenPort.getUserIdFromToken(refreshToken)
        val user = userRepository.findById(userId)
            ?: throw NotFoundException("사용자", userId)

        // Token Rotation: 기존 토큰 삭제 → 새 토큰 발급 → 저장
        refreshTokenPort.deleteByToken(refreshToken)

        val existingUserId = user.id!!
        val newAccessToken = authTokenPort.generateAccessToken(existingUserId)
        val newRefreshToken = authTokenPort.generateRefreshToken(existingUserId)

        val expiresAt = LocalDateTime.now().plusNanos(Duration.ofMillis(authTokenPort.getRefreshTokenExpiryMillis()).toNanos())
        refreshTokenPort.save(existingUserId, newRefreshToken, expiresAt)

        return AuthResult(
            accessToken = newAccessToken,
            refreshToken = newRefreshToken,
            user = toUserResult(user),
            isNewUser = false,
        )
    }

    /**
     * 로그아웃을 처리합니다.
     * 해당 사용자의 모든 리프레시 토큰을 무효화합니다.
     */
    @Transactional
    fun logout(userId: Long) {
        refreshTokenPort.deleteByUserId(userId)
        log.info("로그아웃: userId={}, refresh tokens revoked", userId)
    }

    @Transactional
    fun devLogin(): AuthResult {
        val adminEmail = "admin@ongo.kr"
        val user = userRepository.findByEmail(adminEmail)
            ?: run {
                val newUser = userRepository.save(
                    User(
                        email = adminEmail,
                        name = "Admin",
                        nickname = "관리자",
                        provider = com.ongo.common.enums.AuthProvider.GOOGLE,
                        providerId = "dev-admin-001",
                        planType = PlanType.BUSINESS,
                        onboardingCompleted = true,
                        role = "ADMIN",
                    )
                )
                initializeNewUser(newUser)
                log.info("Admin 계정 생성: userId={}", newUser.id)
                newUser
            }

        val userId = user.id!!
        val accessToken = authTokenPort.generateAccessToken(userId)
        val refreshToken = authTokenPort.generateRefreshToken(userId)

        refreshTokenPort.deleteByUserId(userId)
        val expiresAt = LocalDateTime.now().plusNanos(Duration.ofMillis(authTokenPort.getRefreshTokenExpiryMillis()).toNanos())
        refreshTokenPort.save(userId, refreshToken, expiresAt)

        log.info("Admin 로그인: userId={}", userId)

        return AuthResult(
            accessToken = accessToken,
            refreshToken = refreshToken,
            user = toUserResult(user),
            isNewUser = false,
        )
    }

    fun getProfile(userId: Long): UserResult {
        val user = userRepository.findById(userId) ?: throw NotFoundException("사용자", userId)
        return toUserResult(user)
    }

    @Transactional
    fun updateProfile(userId: Long, nickname: String?, category: String?): UserResult {
        val user = userRepository.findById(userId) ?: throw NotFoundException("사용자", userId)
        val updated = userRepository.update(
            user.copy(
                nickname = nickname ?: user.nickname,
                category = category ?: user.category,
            )
        )
        return toUserResult(updated)
    }

    @Transactional
    fun completeOnboarding(userId: Long) {
        val user = userRepository.findById(userId) ?: throw NotFoundException("사용자", userId)
        userRepository.update(user.copy(onboardingCompleted = true))
        log.info("온보딩 완료: userId={}", userId)
    }

    @Transactional
    fun deleteAccount(userId: Long) {
        val user = userRepository.findById(userId) ?: throw NotFoundException("사용자", userId)
        refreshTokenPort.deleteByUserId(userId)
        userRepository.delete(userId)
        log.info("계정 삭제: userId={}", userId)
    }

    private fun initializeNewUser(user: User) {
        val newUserId = user.id!!

        // AI 크레딧 초기화 (Free 플랜 기본 30 크레딧)
        creditRepository.save(
            AiCredit(
                userId = newUserId,
                balance = 0,
                freeMonthly = PlanType.FREE.freeCredits,
                freeRemaining = PlanType.FREE.freeCredits,
                freeResetDate = LocalDate.now().withDayOfMonth(1).plusMonths(1),
            )
        )

        // 사용자 설정 초기화
        userSettingsRepository.save(
            UserSettings(userId = newUserId)
        )

        // 구독 초기화 (Free 플랜)
        subscriptionRepository.save(
            Subscription(
                userId = newUserId,
                planType = PlanType.FREE,
            )
        )
    }

    private fun toUserResult(user: User): UserResult {
        return UserResult(
            id = user.id!!,
            email = user.email,
            name = user.name,
            nickname = user.nickname,
            profileImageUrl = user.profileImageUrl,
            planType = user.planType.name,
            onboardingCompleted = user.onboardingCompleted,
        )
    }
}
