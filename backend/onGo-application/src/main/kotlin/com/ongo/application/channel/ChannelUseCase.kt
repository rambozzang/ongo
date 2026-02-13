package com.ongo.application.channel

import com.ongo.application.channel.dto.*
import com.ongo.common.enums.Platform
import com.ongo.common.enums.PlanType
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.common.exception.PlanLimitExceededException
import com.ongo.common.util.safeValueOfOrThrow
import com.ongo.domain.channel.Channel
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.channel.PlatformClientPort
import com.ongo.domain.channel.PlatformOAuth2Port
import com.ongo.domain.channel.TokenEncryptionPort
import com.ongo.domain.user.UserRepository
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
    private val tokenEncryptionPort: TokenEncryptionPort
) {

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

        // 플랜 제한 확인
        val currentCount = channelRepository.countByUserId(userId)
        if (currentCount >= user.planType.maxPlatforms) {
            throw PlanLimitExceededException("연동 플랫폼", user.planType.maxPlatforms)
        }

        // 이미 연동된 플랫폼 확인
        val existing = channelRepository.findByUserIdAndPlatform(userId, platform)
        if (existing != null) {
            throw com.ongo.common.exception.DuplicateResourceException("채널", platform.name)
        }

        // OAuth 토큰 교환
        val tokenResult = platformOAuth2Port.exchangeCodeForTokens(platform, request.authorizationCode, request.redirectUri)

        // 플랫폼에서 채널 정보 조회
        val channelInfo = platformClientPort.getChannelInfo(platform, tokenResult.accessToken)

        // 토큰 암호화 후 저장
        val channel = Channel(
            userId = userId,
            platform = platform,
            platformChannelId = channelInfo.channelId,
            channelName = channelInfo.channelName,
            channelUrl = channelInfo.channelUrl,
            subscriberCount = channelInfo.subscriberCount,
            profileImageUrl = channelInfo.profileImageUrl,
            accessToken = tokenEncryptionPort.encrypt(tokenResult.accessToken),
            refreshToken = tokenResult.refreshToken?.let { rt: String -> tokenEncryptionPort.encrypt(rt) },
            tokenExpiresAt = LocalDateTime.now().plusSeconds(tokenResult.expiresIn),
            status = "ACTIVE",
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
            status == "DISCONNECTED" -> "DISCONNECTED"
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
            connectedAt = connectedAt
        )
    }
}
