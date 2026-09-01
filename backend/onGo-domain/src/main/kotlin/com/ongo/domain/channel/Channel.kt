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
 * @property status 채널 상태 (ACTIVE, INACTIVE, EXPIRED, REVOKED)
 */
data class Channel(
    val id: Long? = null,
    val userId: Long,
    /** Postiz customer/group scope. Null is retained for legacy rows. */
    val workspaceId: Long? = null,
    val platform: Platform,
    val platformChannelId: String,
    val channelName: String,
    val channelUrl: String? = null,
    /**
     * 구독자(팔로워) 수. **재지 않았으면 `null`** — `0` 은 실제로 0 명이라는 관측이다.
     *
     * `channels.subscriber_count` 는 `NOT NULL` 이 아니고 기본값만 `0` 이다
     * (`V1__init_schema.sql:67`). 예전에는 어댑터가 `?: 0` 으로 채우고 저장소가 읽을 때
     * 다시 `?: 0` 을 붙여, **묻지 않아서 비어 있는 자리**와 구독자가 정말 없는 채널을
     * 구분할 수 없었다.
     */
    val subscriberCount: Long? = null,
    val profileImageUrl: String? = null,
    val accessToken: EncryptedToken,
    val refreshToken: EncryptedToken? = null,
    val tokenExpiresAt: LocalDateTime? = null,
    val status: ChannelStatus = ChannelStatus.ACTIVE,
    val connectedAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)
