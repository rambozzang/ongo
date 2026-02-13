package com.ongo.application.channel

import com.ongo.common.enums.Platform
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.channel.PlatformClientPort
import com.ongo.domain.channel.TokenEncryptionPort
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * 플랫폼 채널 정보 및 토큰 상태를 주기적으로 관리하는 스케줄러
 */
@Component
class ChannelScheduler(
    private val channelRepository: ChannelRepository,
    private val platformClientPort: PlatformClientPort,
    private val tokenEncryptionPort: TokenEncryptionPort
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 24시간마다 모든 활성 채널의 정보(구독자 수, 프로필 등)를 갱신합니다.
     */
    @Scheduled(fixedRate = 86400000) // 24시간마다
    fun refreshAllChannels() {
        log.info("채널 정보 자동 갱신 시작")
        // 모든 활성 채널의 구독자 수, 프로필 이미지 갱신
        val channels = channelRepository.findAllActive()
        channels.forEach { channel ->
            try {
                val decryptedToken = tokenEncryptionPort.decrypt(channel.accessToken)
                val info = platformClientPort.getChannelInfo(channel.platform, decryptedToken)
                channelRepository.update(channel.copy(
                    channelName = info.channelName,
                    subscriberCount = info.subscriberCount,
                    profileImageUrl = info.profileImageUrl,
                    updatedAt = LocalDateTime.now()
                ))
            } catch (e: Exception) {
                log.error("채널 갱신 실패 [${channel.platform}:${channel.id}]: ${e.message}")
            }
        }
        log.info("채널 정보 자동 갱신 완료")
    }

    /**
     * 1시간마다 만료 임박한 토큰을 확인하고 자동으로 갱신합니다.
     */
    @Scheduled(fixedRate = 3600000) // 1시간마다
    fun checkExpiringTokens() {
        log.info("토큰 만료 확인 시작")
        val channels = channelRepository.findAllActive()
        val expiringSoon = channels.filter { channel ->
            val expiresAt = channel.tokenExpiresAt
            expiresAt != null &&
            expiresAt.isBefore(LocalDateTime.now().plusHours(6))
        }

        expiringSoon.forEach { channel ->
            try {
                if (channel.platform == Platform.INSTAGRAM) {
                    val decryptedAccessToken = tokenEncryptionPort.decrypt(channel.accessToken)
                    val newTokens = platformClientPort.refreshToken(channel.platform, decryptedAccessToken)
                    channelRepository.update(channel.copy(
                        accessToken = tokenEncryptionPort.encrypt(newTokens.accessToken),
                        tokenExpiresAt = LocalDateTime.now().plusSeconds(newTokens.expiresIn),
                        updatedAt = LocalDateTime.now()
                    ))
                    log.info("토큰 갱신 성공 [${channel.platform}:${channel.id}]")
                } else {
                    val decryptedRefreshToken = channel.refreshToken?.let { tokenEncryptionPort.decrypt(it) }
                    if (decryptedRefreshToken != null) {
                        val newTokens = platformClientPort.refreshToken(channel.platform, decryptedRefreshToken)
                        channelRepository.update(channel.copy(
                            accessToken = tokenEncryptionPort.encrypt(newTokens.accessToken),
                            refreshToken = newTokens.refreshToken?.let { tokenEncryptionPort.encrypt(it) }
                                ?: channel.refreshToken,
                            tokenExpiresAt = LocalDateTime.now().plusSeconds(newTokens.expiresIn),
                            updatedAt = LocalDateTime.now()
                        ))
                        log.info("토큰 갱신 성공 [${channel.platform}:${channel.id}]")
                    }
                }
            } catch (e: Exception) {
                when {
                    isTransientError(e) -> {
                        log.warn("토큰 갱신 일시적 실패 [${channel.platform}:${channel.id}]: ${e.message} - 다음 주기에 재시도")
                    }
                    else -> {
                        log.error("토큰 갱신 실패 [${channel.platform}:${channel.id}]: ${e.message}")
                        channelRepository.update(channel.copy(status = "EXPIRED", updatedAt = LocalDateTime.now()))
                    }
                }
            }
        }
    }

    private fun isTransientError(e: Exception): Boolean {
        return e is java.net.SocketTimeoutException ||
               e is java.net.ConnectException ||
               e is java.io.IOException ||
               e.cause is java.net.SocketTimeoutException ||
               e.cause is java.net.ConnectException
    }
}
