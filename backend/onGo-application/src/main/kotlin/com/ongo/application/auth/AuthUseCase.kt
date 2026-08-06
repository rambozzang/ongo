package com.ongo.application.auth

import com.ongo.application.auth.dto.AuthResult
import com.ongo.application.auth.dto.UserResult
import com.ongo.common.enums.PlanType
import com.ongo.common.exception.AccountDeletionBlockedException
import com.ongo.common.exception.NotFoundException
import com.ongo.common.exception.TokenExpiredException
import com.ongo.common.exception.UnauthorizedException
import com.ongo.domain.accountdeletion.AccountDeletionPreflight
import com.ongo.domain.accountdeletion.UserFkScanner
import com.ongo.domain.auth.AuthTokenPort
import com.ongo.domain.auth.OAuth2Port
import com.ongo.domain.auth.RefreshTokenPort
import com.ongo.domain.auth.TokenBlacklistPort
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
    private val tokenBlacklistPort: TokenBlacklistPort,
    private val userFkScanner: UserFkScanner,
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
        val accessToken = authTokenPort.generateAccessToken(userId, user.role)
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
        val newAccessToken = authTokenPort.generateAccessToken(existingUserId, user.role)
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
     * 해당 사용자의 Access Token을 블랙리스트에 추가하고 모든 리프레시 토큰을 무효화합니다.
     */
    @Transactional
    fun logout(userId: Long, accessToken: String? = null) {
        accessToken?.let { token ->
            val jti = authTokenPort.getTokenJti(token)
                ?: token.hashCode().toString()
            val ttl = authTokenPort.getTokenRemainingExpiryMillis(token)
            tokenBlacklistPort.blacklist(jti, ttl)
        }
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
        val accessToken = authTokenPort.generateAccessToken(userId, user.role)
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

    /**
     * 계정 삭제 요청. **현재는 어떤 데이터도 지우지 않는다.**
     *
     * 예전 구현은 refresh token 을 지우고 `userRepository.delete` 를 바로 불렀다.
     * `users` 를 `ON DELETE CASCADE` 로 참조하는 17개 테이블이 함께 사라지는데
     * 거기에 `payments`, `subscriptions`, `ai_credit_transactions` 가 들어 있었다.
     * 실제 PostgreSQL 로 재현해 결제 레코드가 함께 사라지는 것을 확인했다.
     *
     * 반대 방향으로도 깨져 있었다. S3/MinIO 파일과 플랫폼 OAuth 연동은 남아서,
     * 사용자가 삭제를 요청했는데 데이터가 남았다.
     *
     * 그래서 정책 엔진이 완성될 때까지 이 경로를 닫는다. 사전 점검만 하고
     * **항상 [AccountDeletionBlockedException] 을 던진다.** 삭제·토큰 정리·상태 변경을
     * 하나도 실행하지 않으므로 부분 반영이 남지 않는다.
     *
     * 근거와 설계: `docs/plans/account-deletion-policy-table.md`
     */
    fun deleteAccount(userId: Long) {
        userRepository.findById(userId) ?: throw NotFoundException("사용자", userId)

        // 사전 점검이 실패하면 fail-closed 다. 판정을 못 했으면 지우지 않는다.
        val result = try {
            AccountDeletionPreflight.evaluate(
                actualFks = userFkScanner.actualUserFks(),
                userRowCounter = { key -> userFkScanner.countRowsFor(key, userId) },
            )
        } catch (e: Exception) {
            log.error("계정 삭제 사전 점검 실패. 삭제를 진행하지 않는다. userId={}", userId, e)
            throw AccountDeletionBlockedException(
                code = AccountDeletionBlockedException.CODE_PREFLIGHT_FAILED,
                message = "계정 삭제 요청을 처리하지 못했습니다. 잠시 후 다시 시도하거나 고객지원에 문의해 주세요.",
                supportReference = "preflight-error:${e.javaClass.simpleName}",
            )
        }

        // 사용자 응답에는 테이블·컬럼 이름을 넣지 않는다. 스키마 구조 노출이고,
        // 이름이 바뀌면 클라이언트가 깨진다. 진단 정보는 로그와 supportReference 로만 남긴다.
        when (result) {
            is AccountDeletionPreflight.Result.BlockedGlobally -> {
                log.error(
                    "계정 삭제 전역 차단. 분류되지 않은 외래키 {}건: {}. userId={}",
                    result.unclassified.size,
                    result.unclassified.joinToString { it.constraintName },
                    userId,
                )
                throw AccountDeletionBlockedException(
                    code = AccountDeletionBlockedException.CODE_UNCLASSIFIED,
                    message = "계정 삭제를 준비 중입니다. 고객지원에 문의해 주세요.",
                    supportReference = "unclassified:${result.unclassified.size}",
                )
            }

            is AccountDeletionPreflight.Result.BlockedForUser -> {
                log.warn(
                    "계정 삭제 사용자별 차단. 판단 미완 데이터 {}건: {}. userId={}",
                    result.blocking.size,
                    result.blocking.joinToString { it.key.constraintName },
                    userId,
                )
                throw AccountDeletionBlockedException(
                    code = AccountDeletionBlockedException.CODE_POLICY_REVIEW,
                    message = "보관 정책 확인이 필요한 데이터가 있어 계정 삭제를 바로 진행할 수 없습니다. 고객지원에 문의해 주세요.",
                    supportReference = "review-block:${result.blocking.joinToString { it.key.constraintName }}",
                )
            }

            is AccountDeletionPreflight.Result.Proceed -> {
                // 정책상 지울 수 있지만 실제로 지우는 절차가 아직 없다.
                // 여기서 202 REQUESTED 를 돌려주지 않는다. 처리할 job 이 없는데 접수됐다고 하면 거짓말이다.
                log.info("계정 삭제 가능하나 처리 절차 미구현. userId={} 대상={}건", userId, result.deletable.size)
                throw AccountDeletionBlockedException(
                    code = AccountDeletionBlockedException.CODE_NOT_READY,
                    message = "계정 삭제 처리를 준비 중입니다. 고객지원에 문의해 주세요.",
                    supportReference = "not-ready:deletable=${result.deletable.size}",
                )
            }
        }
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
            role = user.role,
            onboardingCompleted = user.onboardingCompleted,
        )
    }
}
