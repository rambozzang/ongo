package com.ongo.domain.ugc.participation

import java.time.LocalDateTime

/**
 * 캠페인 초대 링크. 토큰 원문은 저장하지 않고 SHA-256 해시(`tokenHash`)만 보관한다.
 */
data class CampaignInvite(
    val id: Long? = null,
    val campaignId: Long,
    val tokenHash: String,
    val expiresAt: LocalDateTime? = null,
    val maxUses: Int? = null,
    val usedCount: Int = 0,
    val active: Boolean = true,
    val createdBy: Long,
    val createdAt: LocalDateTime? = null,
) {
    /** 주어진 시각 기준으로 이 초대가 사용 가능한지(활성·미만료·사용횟수 이내) 판단한다. */
    fun isUsable(now: LocalDateTime): Boolean {
        if (!active) return false
        if (expiresAt != null && now.isAfter(expiresAt)) return false
        if (maxUses != null && usedCount >= maxUses) return false
        return true
    }
}
