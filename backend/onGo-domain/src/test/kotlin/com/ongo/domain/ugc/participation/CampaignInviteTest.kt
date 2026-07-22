package com.ongo.domain.ugc.participation

import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CampaignInviteTest {

    private val now = LocalDateTime.of(2026, 8, 10, 12, 0)

    private fun invite(
        expiresAt: LocalDateTime? = now.plusDays(7),
        maxUses: Int? = 10,
        usedCount: Int = 0,
        active: Boolean = true,
    ) = CampaignInvite(
        id = 1,
        campaignId = 10,
        tokenHash = "hash",
        expiresAt = expiresAt,
        maxUses = maxUses,
        usedCount = usedCount,
        active = active,
        createdBy = 100,
    )

    @Test
    fun `active unexpired invite under max uses is usable`() {
        assertTrue(invite().isUsable(now))
    }

    @Test
    fun `inactive invite is not usable`() {
        assertFalse(invite(active = false).isUsable(now))
    }

    @Test
    fun `expired invite is not usable`() {
        assertFalse(invite(expiresAt = now.minusMinutes(1)).isUsable(now))
    }

    @Test
    fun `invite at max uses is not usable`() {
        assertFalse(invite(maxUses = 5, usedCount = 5).isUsable(now))
    }

    @Test
    fun `invite with no expiry and no max uses is usable`() {
        assertTrue(invite(expiresAt = null, maxUses = null).isUsable(now))
    }
}
