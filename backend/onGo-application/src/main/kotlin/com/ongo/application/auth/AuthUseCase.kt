package com.ongo.application.auth

import com.ongo.application.auth.dto.*
import com.ongo.common.enums.PlanType
import com.ongo.common.enums.SubscriptionStatus
import com.ongo.common.exception.AccountDeletionBlockedException
import com.ongo.common.exception.NotFoundException
import com.ongo.common.exception.TokenExpiredException
import com.ongo.common.exception.UnauthorizedException
import com.ongo.domain.accountdeletion.AccountDeletionJobRepository
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
import java.util.UUID

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
    private val accountDeletionJobRepository: AccountDeletionJobRepository? = null,
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

    /**
     * 삭제 요청의 현재 상태를 반환한다.
     *
     * 삭제 job은 users 행이 삭제된 뒤에도 남아야 하므로, 게이트와 job을 함께 조회하되
     * users가 이미 사라진 완료 상태도 `DELETED`로 표현한다. 조회만 수행하며 동결된 계정의
     * 상태 확인을 막지 않는다.
     */
    @Transactional(readOnly = true)
    fun getAccountDeletionStatus(userId: Long): AccountDeletionStatusResponse {
        val repository = accountDeletionJobRepository
        val job = repository?.findLatestByUserId(userId)
        val state = repository?.findDeletionState(userId)?.name
            ?: if (job?.status?.name == "COMPLETED") "DELETED" else "ACTIVE"

        return AccountDeletionStatusResponse(
            state = state,
            status = job?.status?.name,
            jobId = job?.id,
            requestedAt = job?.requestedAt,
            updatedAt = job?.updatedAt,
            completedAt = job?.completedAt,
            lastErrorCode = job?.lastErrorCode,
            supportReference = job?.supportReference,
            retryable = job?.status?.name == "FAILED" || job?.status?.name == "BLOCKED_POLICY",
        )
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
     * 계정 삭제 요청.
     *
     * 예전 구현은 refresh token 을 지우고 `userRepository.delete` 를 바로 불렀다.
     * `users` 를 `ON DELETE CASCADE` 로 참조하는 17개 테이블이 함께 사라지는데
     * 거기에 `payments`, `subscriptions`, `ai_credit_transactions` 가 들어 있었다.
     * 실제 PostgreSQL 로 재현해 결제 레코드가 함께 사라지는 것을 확인했다.
     *
     * 반대 방향으로도 깨져 있었다. S3/MinIO 파일과 플랫폼 OAuth 연동은 남아서,
     * 사용자가 삭제를 요청했는데 데이터가 남았다.
     *
     * 그래서 정책 엔진을 통과한 계정만 durable job 으로 접수한다. 실제 DB 삭제는 워커가
     * 별도 트랜잭션에서 수행한다. 정책 차단·설정 미완·결제 중인 계정은 여전히 아무것도
     * 변경하지 않고 차단한다.
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
            recordBlockedDeletion(userId, AccountDeletionBlockedException.CODE_PREFLIGHT_FAILED, "preflight-error:${e.javaClass.simpleName}")
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
                recordBlockedDeletion(userId, AccountDeletionBlockedException.CODE_UNCLASSIFIED, "unclassified:${result.unclassified.size}")
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
                recordBlockedDeletion(userId, AccountDeletionBlockedException.CODE_POLICY_REVIEW, "review-block:${result.blocking.size}")
                throw AccountDeletionBlockedException(
                    code = AccountDeletionBlockedException.CODE_POLICY_REVIEW,
                    message = "보관 정책 확인이 필요한 데이터가 있어 계정 삭제를 바로 진행할 수 없습니다. 고객지원에 문의해 주세요.",
                    supportReference = "review-block:${result.blocking.joinToString { it.key.constraintName }}",
                )
            }

            is AccountDeletionPreflight.Result.Proceed -> {
                if (subscriptionRequiresReview(userId)) {
                    val reference = "subscription:active-or-billing"
                    log.warn("계정 삭제 사용자별 차단. 결제·구독 정리가 필요하다. userId={}", userId)
                    recordBlockedDeletion(userId, AccountDeletionBlockedException.CODE_POLICY_REVIEW, reference)
                    throw AccountDeletionBlockedException(
                        code = AccountDeletionBlockedException.CODE_POLICY_REVIEW,
                        message = "진행 중인 구독 또는 결제 정보가 있어 계정 삭제를 바로 진행할 수 없습니다. 고객지원에 문의해 주세요.",
                        supportReference = reference,
                    )
                }

                val repository = accountDeletionJobRepository
                if (repository == null) {
                    // 애플리케이션 테스트/구형 실행 환경에서 job 저장소가 빠진 경우
                    // 실제 삭제를 시도하지 않는다. 운영에서는 Spring bean이 항상 주입된다.
                    val reference = "not-ready:deletion-job-repository"
                    log.error("계정 삭제 job 저장소가 없어 접수할 수 없다. userId={}", userId)
                    recordBlockedDeletion(userId, AccountDeletionBlockedException.CODE_NOT_READY, reference)
                    throw AccountDeletionBlockedException(
                        code = AccountDeletionBlockedException.CODE_NOT_READY,
                        message = "계정 삭제 처리를 준비 중입니다. 고객지원에 문의해 주세요.",
                        supportReference = reference,
                    )
                }

                val job = repository.requestDeletion(
                    userId = userId,
                    idempotencyKey = "account-deletion-request:$userId:${UUID.randomUUID()}",
                )
                log.info("계정 삭제 요청 접수: userId={} jobId={} 대상={}건", userId, job.id, result.deletable.size)
            }
        }
    }

    /**
     * 무료 계정의 기본 구독 row 는 개인 상태라 삭제할 수 있다. 반면 유료·시험·일시정지
     * 상태나 Paddle 식별자가 남은 row 는 결제 취소/환불·보존 정책 확인 전까지 차단한다.
     * 결제 이력 자체는 별도 FK 정책이 계속 검사한다.
     */
    private fun subscriptionRequiresReview(userId: Long): Boolean {
        val subscription = subscriptionRepository.findByUserId(userId) ?: return false
        return subscription.planType != PlanType.FREE ||
            subscription.status != SubscriptionStatus.FREE ||
            !subscription.paddleSubscriptionId.isNullOrBlank() ||
            !subscription.paddleCustomerId.isNullOrBlank()
    }

    /**
     * 삭제 워커가 완성되기 전에도 차단 판정을 내구성 있게 남긴다.
     *
     * 이 기록은 삭제 동결을 켜지 않는다. 정책 차단은 사용자를 잠그는 작업이 아니라
     * 재시도·지원·감사를 위한 결과이기 때문이다. 기록 저장소 장애가 원래의 차단 사유를
     * 덮어쓰지 않도록 best-effort 로만 수행한다.
     */
    private fun recordBlockedDeletion(userId: Long, errorCode: String, supportReference: String) {
        val repository = accountDeletionJobRepository ?: return
        runCatching {
            repository.recordBlocked(
                userId = userId,
                idempotencyKey = "account-deletion:$userId:${UUID.randomUUID()}",
                errorCode = errorCode,
                supportReference = supportReference,
            )
        }.onFailure { error ->
            log.error("계정 삭제 차단 결과를 저장하지 못했습니다. userId={} code={}", userId, errorCode, error)
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
