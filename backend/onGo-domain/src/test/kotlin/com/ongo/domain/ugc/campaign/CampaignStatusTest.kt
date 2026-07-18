package com.ongo.domain.ugc.campaign

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CampaignStatusTest {

    @Test
    fun `DRAFT can transition to RECRUITING and CANCELLED`() {
        assertTrue(CampaignStatus.DRAFT.canTransitionTo(CampaignStatus.RECRUITING))
        assertTrue(CampaignStatus.DRAFT.canTransitionTo(CampaignStatus.CANCELLED))
    }

    @Test
    fun `DRAFT cannot jump straight to ACTIVE or COMPLETED`() {
        assertFalse(CampaignStatus.DRAFT.canTransitionTo(CampaignStatus.ACTIVE))
        assertFalse(CampaignStatus.DRAFT.canTransitionTo(CampaignStatus.COMPLETED))
    }

    @Test
    fun `RECRUITING can pause, activate, complete and cancel`() {
        assertTrue(CampaignStatus.RECRUITING.canTransitionTo(CampaignStatus.PAUSED))
        assertTrue(CampaignStatus.RECRUITING.canTransitionTo(CampaignStatus.ACTIVE))
        assertTrue(CampaignStatus.RECRUITING.canTransitionTo(CampaignStatus.COMPLETED))
        assertTrue(CampaignStatus.RECRUITING.canTransitionTo(CampaignStatus.CANCELLED))
    }

    @Test
    fun `PAUSED can resume to RECRUITING or ACTIVE but cannot go straight to COMPLETED`() {
        assertTrue(CampaignStatus.PAUSED.canTransitionTo(CampaignStatus.RECRUITING))
        assertTrue(CampaignStatus.PAUSED.canTransitionTo(CampaignStatus.ACTIVE))
        assertFalse(CampaignStatus.PAUSED.canTransitionTo(CampaignStatus.COMPLETED))
    }

    @Test
    fun `terminal states allow no further transitions`() {
        for (target in CampaignStatus.entries) {
            assertFalse(CampaignStatus.COMPLETED.canTransitionTo(target))
            assertFalse(CampaignStatus.CANCELLED.canTransitionTo(target))
        }
        assertTrue(CampaignStatus.COMPLETED.isTerminal())
        assertTrue(CampaignStatus.CANCELLED.isTerminal())
        assertFalse(CampaignStatus.DRAFT.isTerminal())
    }
}
