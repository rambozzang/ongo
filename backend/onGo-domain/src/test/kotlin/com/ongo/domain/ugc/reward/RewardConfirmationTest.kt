package com.ongo.domain.ugc.reward

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RewardConfirmationTest {

    private fun reward(status: RewardStatus = RewardStatus.DRAFT) =
        RewardConfirmation(id = 1, participantId = 10, campaignId = 5, creatorId = 100, status = status)

    @Test
    fun `status transitions`() {
        assertTrue(RewardStatus.DRAFT.canTransitionTo(RewardStatus.CONFIRMED))
        assertTrue(RewardStatus.DRAFT.canTransitionTo(RewardStatus.CANCELLED))
        assertTrue(RewardStatus.CONFIRMED.canTransitionTo(RewardStatus.PAID_EXTERNALLY))
        assertFalse(RewardStatus.PAID_EXTERNALLY.canTransitionTo(RewardStatus.CONFIRMED))
        assertTrue(RewardStatus.CONFIRMED.isSettled())
        assertTrue(RewardStatus.PAID_EXTERNALLY.isSettled())
        assertFalse(RewardStatus.DRAFT.isSettled())
    }

    @Test
    fun `withAmounts computes total and requires draft`() {
        val updated = reward().withAmounts(base = 100_000, bonus = 20_000, note = "보너스")
        assertEquals(120_000, updated.totalAmount)
        assertFailsWith<IllegalStateException> {
            reward(status = RewardStatus.CONFIRMED).withAmounts(1, 1, null)
        }
    }

    @Test
    fun `confirm sets confirmer and blocks re-confirm`() {
        val confirmed = reward().confirm(confirmedBy = 7)
        assertEquals(RewardStatus.CONFIRMED, confirmed.status)
        assertEquals(7, confirmed.confirmedBy)
        assertFailsWith<IllegalStateException> { confirmed.confirm(8) }
    }

    @Test
    fun `mark paid requires confirmed`() {
        assertFailsWith<IllegalStateException> { reward().markPaid() }
        assertEquals(RewardStatus.PAID_EXTERNALLY, reward(status = RewardStatus.CONFIRMED).markPaid().status)
    }

    @Test
    fun `negative amount is rejected`() {
        assertFailsWith<IllegalArgumentException> { reward().copy(baseAmount = -1) }
    }
}
