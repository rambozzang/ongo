package com.ongo.domain.channel

import com.ongo.common.enums.Platform
import java.time.LocalDateTime

/**
 * 연동된 외부 플랫폼 채널 정보를 담당하는 도메인 엔티티
 *
 * @property id 채널 식별자
 * @property userId 소유자(사용자) 식별자
 * @property platform 연동 플랫폼 (YOUTUBE, INSTAGRAM, TIKTOK 등)
 * @property platformChannelId 플랫폼 내 채널 고유 ID
 * @property channelName 채널명
 * @property channelUrl 채널 주소
 * @property subscriberCount 구독자 수 (또는 팔로워 수)
 * @property profileImageUrl 채널 프로필 이미지 URL
 * @property accessToken 플랫폼 API 접근용 토큰 (암호화되어 저장됨)
 * @property refreshToken 토큰 갱신용 리프레시 토큰 (암호화되어 저장됨)
 * @property tokenExpiresAt 액세스 토큰 만료 일시
 * @property status 채널 상태 (ACTIVE, DISCONNECTED 등)
 */
data class Channel(
    val id: Long? = null,
    val userId: Long,
    val platform: Platform,
    val platformChannelId: String,
    val channelName: String,
    val channelUrl: String? = null,
    val subscriberCount: Long = 0,
    val profileImageUrl: String? = null,
    val accessToken: String,
    val refreshToken: String? = null,
    val tokenExpiresAt: LocalDateTime? = null,
    val status: String = "ACTIVE",
    val connectedAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)

