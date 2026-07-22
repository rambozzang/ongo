package com.ongo.domain.ugc.campaign

import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CampaignTest {

    private val start = LocalDateTime.of(2026, 8, 1, 0, 0)
    private val end = LocalDateTime.of(2026, 8, 31, 0, 0)

    private fun draft(
        status: CampaignStatus = CampaignStatus.DRAFT,
        totalBudget: Long = 1_000_000,
        fixedReward: Long = 100_000,
        startAt: LocalDateTime? = start,
        endAt: LocalDateTime? = end,
    ) = Campaign(
        id = 1,
        workspaceId = 10,
        name = "여름 UGC 캠페인",
        status = status,
        objective = "AWARENESS",
        totalBudget = totalBudget,
        fixedRewardPerCreator = fixedReward,
        startAt = startAt,
        endAt = endAt,
        createdBy = 100,
    )

    // ---- 생성 불변식 ----

    @Test
    fun `negative budget is rejected`() {
        assertFailsWith<IllegalArgumentException> { draft(totalBudget = -1) }
    }

    @Test
    fun `negative reward is rejected`() {
        assertFailsWith<IllegalArgumentException> { draft(fixedReward = -1) }
    }

    @Test
    fun `end before start is rejected`() {
        assertFailsWith<IllegalArgumentException> { draft(startAt = end, endAt = start) }
    }

    @Test
    fun `blank name is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            Campaign(workspaceId = 10, name = " ", createdBy = 100)
        }
    }

    // ---- publish ----

    @Test
    fun `publish moves DRAFT to RECRUITING when playbook and period are set`() {
        val published = draft().publish(hasActivePlaybook = true)
        assertEquals(CampaignStatus.RECRUITING, published.status)
    }

    @Test
    fun `publish without playbook fails`() {
        assertFailsWith<IllegalStateException> { draft().publish(hasActivePlaybook = false) }
    }

    @Test
    fun `publish without period fails`() {
        assertFailsWith<IllegalStateException> {
            draft(startAt = null, endAt = null).publish(hasActivePlaybook = true)
        }
    }

    @Test
    fun `publish from non-draft fails`() {
        assertFailsWith<IllegalStateException> {
            draft(status = CampaignStatus.RECRUITING).publish(hasActivePlaybook = true)
        }
    }

    // ---- pause / complete / cancel ----

    @Test
    fun `pause moves RECRUITING to PAUSED`() {
        assertEquals(CampaignStatus.PAUSED, draft(status = CampaignStatus.RECRUITING).pause().status)
    }

    @Test
    fun `complete moves ACTIVE to COMPLETED`() {
        assertEquals(CampaignStatus.COMPLETED, draft(status = CampaignStatus.ACTIVE).complete().status)
    }

    @Test
    fun `complete from DRAFT fails`() {
        assertFailsWith<IllegalStateException> { draft().complete() }
    }

    @Test
    fun `cancel from DRAFT succeeds`() {
        assertEquals(CampaignStatus.CANCELLED, draft().cancel().status)
    }

    // ---- assertEditable ----

    @Test
    fun `draft is editable`() {
        draft().assertEditable() // no throw
    }

    @Test
    fun `published campaign is not editable`() {
        assertFailsWith<IllegalStateException> { draft(status = CampaignStatus.RECRUITING).assertEditable() }
    }
}
