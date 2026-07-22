package com.ongo.domain.ugc.participation

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CampaignApplicationTest {

    private fun applied() = CampaignApplication(id = 1, campaignId = 10, creatorId = 100)

    @Test
    fun `APPLIED can transition to ACCEPTED REJECTED WITHDRAWN`() {
        assertTrue(ApplicationStatus.APPLIED.canTransitionTo(ApplicationStatus.ACCEPTED))
        assertTrue(ApplicationStatus.APPLIED.canTransitionTo(ApplicationStatus.REJECTED))
        assertTrue(ApplicationStatus.APPLIED.canTransitionTo(ApplicationStatus.WITHDRAWN))
    }

    @Test
    fun `terminal statuses allow no transition`() {
        for (from in listOf(ApplicationStatus.ACCEPTED, ApplicationStatus.REJECTED, ApplicationStatus.WITHDRAWN)) {
            assertTrue(from.isTerminal())
            for (to in ApplicationStatus.entries) {
                assertFalse(from.canTransitionTo(to))
            }
        }
    }

    @Test
    fun `accept sets status and decider`() {
        val accepted = applied().accept(deciderId = 7)
        assertEquals(ApplicationStatus.ACCEPTED, accepted.status)
        assertEquals(7, accepted.decidedBy)
    }

    @Test
    fun `reject sets status and decider`() {
        val rejected = applied().reject(deciderId = 7)
        assertEquals(ApplicationStatus.REJECTED, rejected.status)
        assertEquals(7, rejected.decidedBy)
    }

    @Test
    fun `accepting an already accepted application fails`() {
        val accepted = applied().accept(deciderId = 7)
        assertFailsWith<IllegalStateException> { accepted.accept(deciderId = 8) }
    }

    @Test
    fun `withdrawing a rejected application fails`() {
        val rejected = applied().reject(deciderId = 7)
        assertFailsWith<IllegalStateException> { rejected.withdraw() }
    }
}
