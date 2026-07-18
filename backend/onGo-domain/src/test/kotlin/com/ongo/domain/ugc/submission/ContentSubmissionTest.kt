package com.ongo.domain.ugc.submission

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContentSubmissionTest {

    private fun submission(
        status: SubmissionStatus = SubmissionStatus.DRAFT,
        withAsset: Boolean = true,
    ) = ContentSubmission(
        id = 1,
        campaignId = 10,
        creatorId = 100,
        caption = "제출합니다",
        status = status,
        assets = if (withAsset) listOf(SubmissionAsset(assetType = "EXTERNAL", externalUrl = "https://x/y")) else emptyList(),
    )

    // ---- status machine ----

    @Test
    fun `draft submits, submitted can be reviewed`() {
        assertTrue(SubmissionStatus.DRAFT.canTransitionTo(SubmissionStatus.SUBMITTED))
        assertTrue(SubmissionStatus.SUBMITTED.canTransitionTo(SubmissionStatus.APPROVED))
        assertTrue(SubmissionStatus.SUBMITTED.canTransitionTo(SubmissionStatus.CHANGES_REQUESTED))
        assertTrue(SubmissionStatus.SUBMITTED.canTransitionTo(SubmissionStatus.REJECTED))
    }

    @Test
    fun `changes requested can resubmit`() {
        assertTrue(SubmissionStatus.CHANGES_REQUESTED.canTransitionTo(SubmissionStatus.SUBMITTED))
        assertTrue(SubmissionStatus.CHANGES_REQUESTED.isEditable())
        assertTrue(SubmissionStatus.DRAFT.isEditable())
        assertFalse(SubmissionStatus.SUBMITTED.isEditable())
        assertFalse(SubmissionStatus.APPROVED.isEditable())
    }

    @Test
    fun `rejected and published are terminal`() {
        for (target in SubmissionStatus.entries) {
            assertFalse(SubmissionStatus.REJECTED.canTransitionTo(target))
            assertFalse(SubmissionStatus.PUBLISHED.canTransitionTo(target))
        }
    }

    // ---- domain methods ----

    @Test
    fun `submit moves draft to submitted`() {
        assertEquals(SubmissionStatus.SUBMITTED, submission().submit().status)
    }

    @Test
    fun `submit without assets fails`() {
        assertFailsWith<IllegalStateException> { submission(withAsset = false).submit() }
    }

    @Test
    fun `approve requires submitted (cannot approve a draft)`() {
        assertFailsWith<IllegalStateException> { submission().approve() }
        assertEquals(SubmissionStatus.APPROVED, submission(status = SubmissionStatus.SUBMITTED).approve().status)
    }

    @Test
    fun `request changes then resubmit`() {
        val changes = submission(status = SubmissionStatus.SUBMITTED).requestChanges()
        assertEquals(SubmissionStatus.CHANGES_REQUESTED, changes.status)
        assertEquals(SubmissionStatus.SUBMITTED, changes.submit().status)
    }

    @Test
    fun `approved submission cannot be edited`() {
        assertFailsWith<IllegalStateException> { submission(status = SubmissionStatus.APPROVED).assertEditable() }
    }
}
