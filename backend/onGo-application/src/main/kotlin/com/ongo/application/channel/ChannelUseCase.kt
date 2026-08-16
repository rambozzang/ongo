package com.ongo.application.channel

import com.ongo.application.channel.dto.*
import com.ongo.common.enums.Platform
import com.ongo.common.enums.PlanType
import com.ongo.common.enums.UploadStatus
import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.common.exception.PlanLimitExceededException
import com.ongo.common.util.safeValueOfOrThrow
import com.ongo.domain.channel.Channel
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.channel.ChannelStatus
import com.ongo.domain.channel.PlatformClientPort
import com.ongo.domain.channel.PlatformOAuth2Port
import com.ongo.domain.channel.TokenEncryptionPort
import com.ongo.domain.channel.PlainToken
import com.ongo.domain.user.UserRepository
import com.ongo.domain.video.VideoUploadRepository
import com.ongo.domain.workspace.WorkspaceRepository
import com.ongo.application.platform.PlatformConfigurationPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 플랫폼 채널 연동 및 관리를 담당하는 UseCase
 */
@Service
class ChannelUseCase(
    private val channelRepository: ChannelRepository,
    private val userRepository: UserRepository,
    private val platformOAuth2Port: PlatformOAuth2Port,
    private val platformClientPort: PlatformClientPort,
    private val tokenEncryptionPort: TokenEncryptionPort,
    private val videoUploadRepository: VideoUploadRepository,
    private val workspaceRepository: WorkspaceRepository,
    private val platformConfigurationPort: PlatformConfigurationPort? = null,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 사용자가 연동한 채널 목록을 조회합니다.
     */
    fun listChannels(userId: Long): ChannelListResponse {
        val user = userRepository.findById(userId) ?: throw NotFoundException("사용자", userId)
        val channels = channelRepository.findByUserId(userId)
        val maxAllowed = user.planType.maxPlatforms

        return ChannelListResponse(
            channels = channels.map { it.toResponse() },
            maxAllowed = maxAllowed,
            currentCount = channels.size
        )
    }

    /**
     * 새로운 플랫폼 채널을 연동합니다.
     * OAuth 인가 코드를 사용하여 토큰을 발급받고, 채널 정보를 조회하여 저장합니다.
     */
    @Transactional
    fun connectChannel(userId: Long, platformStr: String, request: ConnectChannelRequest): ConnectChannelResponse {
        val user = userRepository.findById(userId) ?: throw NotFoundException("사용자", userId)
        val platform = safeValueOfOrThrow<Platform>(platformStr)

        platformConfigurationPort?.status(platform)?.takeUnless { it.configured }?.let { status ->
            throw BusinessException(
                "PLATFORM_NOT_CONFIGURED",
                status.reason ?: "${platform.name} 플랫폼 연동 설정이 없어 연결할 수 없습니다.",
            )
        }

        // 기본은 기존 플랫폼 계정 재연결이다. addAsNew=true면 OAuth 후
        // 외부 채널 ID가 같은 경우만 갱신하고, 다른 계정은 새 integration으로 저장한다.
        val existingPlatform = channelRepository.findByUserIdAndPlatform(userId, platform)

        // 플랜 제한은 '새로' 채널이 늘어날 때만 확인한다.
        //
        // 예전에는 이 검사가 existing 조회보다 앞에 있었고 countByUserId 가 status 를 가리지
        // 않아, 토큰이 만료된 채널을 되살리려 할 때도 한도에 걸렸다. FREE(한도 1) 사용자는
        // 채널이 한 번 끊기면 "플랜 한도 초과"라는 엉뚱한 메시지만 보고 복구할 방법이 없었다.
        // OAuth 토큰 교환 (Twitter는 PKCE code_verifier 필요)
        val tokenResult = platformOAuth2Port.exchangeCodeForTokens(platform, request.authorizationCode, request.redirectUri, request.codeVerifier)

        // 플랫폼에서 채널 정보 조회
        val channelInfo = platformClientPort.getChannelInfo(platform, PlainToken(tokenResult.accessToken))

        // 토큰 암호화
        val encryptedToken = tokenEncryptionPort.encrypt(PlainToken(tokenResult.accessToken))
        val encryptedRefresh = tokenResult.refreshToken?.let { rt: String -> tokenEncryptionPort.encrypt(PlainToken(rt)) }
        val expiresAt = LocalDateTime.now().plusSeconds(tokenResult.expiresIn)

        val existing = channelRepository.findByUserIdAndPlatformChannelId(userId, platform, channelInfo.channelId)
            ?: existingPlatform?.takeUnless { request.addAsNew }

        if (existing == null) {
            val currentCount = channelRepository.countByUserId(userId)
            if (currentCount >= user.planType.maxPlatforms) {
                throw PlanLimitExceededException("연동 플랫폼", user.planType.maxPlatforms)
            }
        }

        // 같은 외부 계정 재연결은 갱신하고, addAsNew로 다른 계정을 연결하면 신규 저장한다.
        if (existing != null) {
            val updated = existing.copy(
                platformChannelId = channelInfo.channelId,
                channelName = channelInfo.channelName,
                channelUrl = channelInfo.channelUrl,
                subscriberCount = channelInfo.subscriberCount,
                profileImageUrl = channelInfo.profileImageUrl,
                accessToken = encryptedToken,
                refreshToken = encryptedRefresh,
                tokenExpiresAt = expiresAt,
                status = ChannelStatus.ACTIVE,
                updatedAt = LocalDateTime.now()
            )
            channelRepository.update(updated)
            return ConnectChannelResponse(channel = updated.toResponse())
        }

        // 신규 채널 저장
        val channel = Channel(
            userId = userId,
            workspaceId = workspaceRepository.findAccessibleByUserId(userId).firstOrNull()?.id,
            platform = platform,
            platformChannelId = channelInfo.channelId,
            channelName = channelInfo.channelName,
            channelUrl = channelInfo.channelUrl,
            subscriberCount = channelInfo.subscriberCount,
            profileImageUrl = channelInfo.profileImageUrl,
            accessToken = encryptedToken,
            refreshToken = encryptedRefresh,
            tokenExpiresAt = expiresAt,
            status = ChannelStatus.ACTIVE,
            connectedAt = LocalDateTime.now()
        )

        val saved = channelRepository.save(channel)
        return ConnectChannelResponse(channel = saved.toResponse())
    }

    /**
     * 채널 연동을 해제합니다.
     */
    @Transactional
    fun disconnectChannel(userId: Long, channelId: Long) {
        val channel = channelRepository.findById(channelId) ?: throw NotFoundException("채널", channelId)
        if (channel.userId != userId) throw ForbiddenException("해당 채널에 대한 권한이 없습니다")

        // Postiz의 채널 삭제 계약과 동일하게, 아직 외부 전송을 시작하지 않은
        // 예약 작업은 채널 삭제와 함께 durable queue에서도 취소한다. 이미 전송된
        // 작업은 결과 확인을 위해 보존한다.
        val now = LocalDateTime.now()
        val cancellableUploadCount = videoUploadRepository.findByUserId(userId)
            .count { it.channelId == channelId && it.status == UploadStatus.UPLOADING && it.scheduledAt != null }
        val cancelledUploadCount = videoUploadRepository.cancelScheduledUploadsByChannelId(channelId, now)
        if (cancelledUploadCount != cancellableUploadCount) {
            throw BusinessException(
                "CHANNEL_DISCONNECT_CONFLICT",
                "예약 게시가 이미 실행 중이라 채널을 해제할 수 없습니다. 현재 게시 상태를 확인해주세요.",
            )
        }

        // 플랫폼 OAuth 토큰 폐기
        try {
            val decryptedToken = tokenEncryptionPort.decrypt(channel.accessToken)
            val revoked = platformClientPort.revokeToken(channel.platform, decryptedToken)
            if (revoked) {
                log.info("플랫폼 토큰 폐기 완료: platform={}, channelId={}", channel.platform, channelId)
            } else {
                log.info("플랫폼이 토큰 폐기를 지원하지 않아 로컬 연결만 해제합니다: platform={}, channelId={}", channel.platform, channelId)
            }
        } catch (e: Exception) {
            log.warn("플랫폼 토큰 폐기 실패 (계속 진행): platform={}, error={}", channel.platform, e.message)
        }

        channelRepository.delete(channelId)
    }

    /**
     * 채널 정보를 플랫폼 API로부터 최신 상태로 갱신합니다.
     */
    @Transactional
    fun refreshChannelInfo(userId: Long, channelId: Long): ChannelResponse {
        val channel = channelRepository.findById(channelId) ?: throw NotFoundException("채널", channelId)
        if (channel.userId != userId) throw ForbiddenException("해당 채널에 대한 권한이 없습니다")

        val decryptedToken = tokenEncryptionPort.decrypt(channel.accessToken)
        val info = platformClientPort.getChannelInfo(channel.platform, decryptedToken)

        val updated = channel.copy(
            channelName = info.channelName,
            subscriberCount = info.subscriberCount,
            profileImageUrl = info.profileImageUrl,
            updatedAt = LocalDateTime.now()
        )
        channelRepository.update(updated)
        return updated.toResponse()
    }

    private fun Channel.toResponse(): ChannelResponse {
        val expiresAt = tokenExpiresAt
        val tokenStatus = when {
            status == ChannelStatus.REVOKED -> "DISCONNECTED"
            expiresAt != null && expiresAt.isBefore(LocalDateTime.now()) -> "EXPIRED"
            expiresAt != null && expiresAt.isBefore(LocalDateTime.now().plusDays(3)) -> "EXPIRING_SOON"
            else -> "ACTIVE"
        }
        return ChannelResponse(
            id = id!!,
            platform = platform,
            channelName = channelName,
            channelUrl = channelUrl,
            subscriberCount = subscriberCount,
            profileImageUrl = profileImageUrl,
            status = status,
            tokenStatus = tokenStatus,
            connectedAt = connectedAt,
            lastSyncedAt = updatedAt ?: connectedAt,
            tokenExpiresAt = tokenExpiresAt,
        )
    }
}
