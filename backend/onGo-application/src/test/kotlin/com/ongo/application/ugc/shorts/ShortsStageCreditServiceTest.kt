package com.ongo.application.ugc.shorts

import com.ongo.application.credit.CreditAllocation
import com.ongo.application.credit.CreditService
import com.ongo.domain.ai.PipelineCreditAllocation
import com.ongo.domain.ugc.shorts.PipelineStage
import com.ongo.domain.ugc.shorts.RunStage
import com.ongo.domain.ugc.shorts.RunStageRepository
import com.ongo.domain.ugc.shorts.RunStageStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 쇼츠 단계 크레딧의 **차감 원자성과 1회 정산**을 고정한다.
 *
 * ## 무엇을 막는가
 *
 * 오케스트레이터는 트랜잭션 없이 돌면서 외부 AI 를 호출한다. 예전에는 차감이 자기
 * 트랜잭션으로 혼자 커밋된 뒤 단계 행을 저장했다. 그래서
 *
 *  - 저장이 실패하면 되돌릴 근거 없이 돈만 빠졌고,
 *  - 커밋 직후 프로세스가 죽으면 인메모리 영수증이 사라져 **아무도 환불할 수 없었다.**
 *
 * 이제 차감·단계 행·분해가 한 커밋이고, 정산은 표식과 환불이 한 트랜잭션이라 정확히 한 번
 * 일어난다.
 */
class ShortsStageCreditServiceTest {

    private val creditService = mockk<CreditService>(relaxed = true)
    private val runStageRepository = mockk<RunStageRepository>()
    private val subject = ShortsStageCreditService(creditService, runStageRepository)

    private val userId = 1L
    private val runId = 7L

    private fun allocation(free: Int = 2, purchased: Map<Long, Int> = mapOf(11L to 3)) =
        CreditAllocation.restored(userId, "STT", free, purchased)

    private fun stageRow(
        id: Long = 100L,
        creditCost: Int = 5,
        alloc: PipelineCreditAllocation? = PipelineCreditAllocation(2, mapOf(11L to 3)),
        status: RunStageStatus = RunStageStatus.RUNNING,
    ) = RunStage(
        id = id, runId = runId, stage = PipelineStage.TRANSCRIBE, status = status,
        creditCost = creditCost, creditAllocation = alloc,
    )

    /* ── (2) 차감-저장 원자성 ─────────────────────────────────────────── */

    /**
     * **경계가 선언되어 있어야 원자성이 성립한다.**
     *
     * 오케스트레이터는 트랜잭션 없이 돈다. 여기서 `REQUIRES_NEW` 로 경계를 열지 않으면
     * `validateAndDeduct`(REQUIRED)가 자기 트랜잭션으로 혼자 커밋하고, 이어지는 단계 행
     * 저장이 실패해도 차감은 남는다.
     */
    @Test
    @DisplayName("차감·저장은 REQUIRES_NEW 경계에서 수행된다")
    fun chargeDeclaresItsOwnTransaction() {
        val method = ShortsStageCreditService::class.java
            .methods.single { it.name == "chargeAndOpenStage" }
        val tx = method.getAnnotation(Transactional::class.java)

        assertNotNull(tx, "차감·저장에 트랜잭션 경계가 없다")
        assertEquals(Propagation.REQUIRES_NEW, tx.propagation)
    }

    @Test
    @DisplayName("차감과 단계 행·분해가 함께 저장된다")
    fun chargeStoresAllocationWithTheStage() {
        every { creditService.validateAndDeduct(userId, 5, "STT") } returns allocation()
        val saved = slot<RunStage>()
        every { runStageRepository.save(capture(saved)) } answers { firstArg<RunStage>().copy(id = 100L) }

        val charged = subject.chargeAndOpenStage(runId, userId, PipelineStage.TRANSCRIBE, "STT", 5)

        assertEquals(RunStageStatus.RUNNING, saved.captured.status)
        // 청구액을 차감 시점에 남긴다. 예전에는 완료 시점에만 써서 진행 중 단계가 0 으로 보였다.
        assertEquals(5, saved.captured.creditCost)
        assertEquals(PipelineCreditAllocation(2, mapOf(11L to 3)), saved.captured.creditAllocation)
        assertEquals(100L, charged.runStage.id)
    }

    /**
     * **저장이 실패하면 차감도 롤백되어야 한다.**
     *
     * 단위 테스트에는 실제 트랜잭션이 없으므로 여기서는 예외가 **밖으로 전파되는지**를
     * 고정한다. 삼키면 스프링이 롤백할 기회를 잃는다. 실제 롤백은 통합 테스트가 확인한다.
     */
    @Test
    @DisplayName("단계 저장이 실패하면 예외를 삼키지 않는다")
    fun stageSaveFailurePropagates() {
        every { creditService.validateAndDeduct(userId, 5, "STT") } returns allocation()
        every { runStageRepository.save(any()) } throws IllegalStateException("저장 실패")

        assertThrows<IllegalStateException> {
            subject.chargeAndOpenStage(runId, userId, PipelineStage.TRANSCRIBE, "STT", 5)
        }
    }

    /** 무과금 단계(RENDER_SPEC·SCHEDULE)는 차감 없이 단계만 연다. */
    @Test
    @DisplayName("무과금 단계는 차감하지 않는다")
    fun freeStagesAreNotCharged() {
        val saved = slot<RunStage>()
        every { runStageRepository.save(capture(saved)) } answers { firstArg<RunStage>().copy(id = 101L) }

        subject.chargeAndOpenStage(runId, userId, PipelineStage.SCHEDULE, featureName = null, creditCost = 0)

        verify(exactly = 0) { creditService.validateAndDeduct(any(), any<Int>(), any()) }
        assertEquals(0, saved.captured.creditCost)
        // 분해가 없어야 미정산 판정에 걸리지 않는다.
        assertNull(saved.captured.creditAllocation)
    }

    /* ── (3) 정확히 한 번 환불 ────────────────────────────────────────── */

    /**
     * **경계는 `REQUIRED` 다.** 호출자(재실행·삭제)가 이미 트랜잭션을 열었으면 그것에 참여해
     * 환불과 삭제가 함께 커밋되거나 함께 롤백된다. `REQUIRES_NEW` 로 분리하면 환불만 커밋된
     * 뒤 삭제가 실패하는 상태가 생긴다.
     */
    @Test
    @DisplayName("정산 진입점은 호출자 트랜잭션에 참여한다")
    fun settlementEntryPointsJoinTheCallerTransaction() {
        for (name in listOf("settleStage", "settleBeforeDiscard")) {
            val tx = ShortsStageCreditService::class.java
                .methods.single { it.name == name }
                .getAnnotation(Transactional::class.java)

            assertNotNull(tx, "$name 에 트랜잭션 경계가 없다")
            assertEquals(Propagation.REQUIRED, tx.propagation, name)
        }
    }

    /**
     * **이 클래스 안에서 `@Transactional` 메서드끼리 부르면 안 된다.**
     *
     * 자기 호출은 프록시를 지나지 않아 애노테이션이 무시된다. 그러면 표식과 환불이 같은
     * 트랜잭션에 있다는 보장이 사라지고, 환불 실패가 표식을 되돌리지 못한다.
     */
    @Test
    @DisplayName("정산 헬퍼는 공개 트랜잭션 메서드를 자기 호출하지 않는다")
    fun noSelfInvocationOfTransactionalMethods() {
        val source = java.io.File(
            "src/main/kotlin/com/ongo/application/ugc/shorts/ShortsStageCreditService.kt",
        ).readText()
        val body = source.substringAfter("fun settleBeforeDiscard(")

        assertFalse(
            body.substringBefore("private fun settleOne").contains("settleStage("),
            "settleBeforeDiscard 가 @Transactional 메서드를 자기 호출한다 — 경계가 무시된다",
        )
    }

    @Test
    @DisplayName("미정산 단계를 저장된 분해 그대로 환불한다")
    fun settleRefundsUsingStoredAllocation() {
        every { runStageRepository.settleRefund(100L, 5, any()) } returns true
        val refunded = slot<CreditAllocation>()
        every { creditService.refundAllocation(capture(refunded)) } returns Unit

        assertTrue(subject.settleStage(userId, stageRow(), "중단됨"))

        // 구매 패키지별 출처가 보존되어야 한다. 총액만 맞추면 구매분이 무료분으로 바뀐다.
        assertEquals(2, refunded.captured.freeAmount)
        assertEquals(listOf(11L to 3), refunded.captured.purchasedPortions.map { it.purchasedCreditId to it.amount })
    }

    /**
     * **표식 경쟁에서 지면 환불하지 않는다.** 이것이 동시 정산이 한 번만 돌려주는 근거다.
     */
    @Test
    @DisplayName("이미 정산된 단계는 다시 환불하지 않는다")
    fun losingTheMarkerRaceSkipsRefund() {
        every { runStageRepository.settleRefund(100L, 5, any()) } returns false

        assertFalse(subject.settleStage(userId, stageRow(), "중단됨"))

        verify(exactly = 0) { creditService.refundAllocation(any()) }
    }

    /**
     * **환불이 실패하면 예외를 전파해 표식도 함께 롤백시킨다.**
     *
     * 삼키면 표식만 남아 "정산됐다"고 기록되고, 실제로는 한 푼도 돌려주지 않은 채 재시도
     * 대상에서 빠진다. 사용자는 영영 잃는다.
     */
    @Test
    @DisplayName("환불 실패는 전파되어 표식과 함께 롤백된다")
    fun refundFailureRollsBackTheMarker() {
        every { runStageRepository.settleRefund(100L, 5, any()) } returns true
        every { creditService.refundAllocation(any()) } throws IllegalStateException("환불 실패")

        assertThrows<IllegalStateException> {
            subject.settleStage(userId, stageRow(), "중단됨")
        }
    }

    /**
     * **롤백된 뒤에는 반드시 다시 집혀야 한다.** 이것이 "열어 두는" 정책의 목적 전부다.
     *
     * 환불이 실패해 트랜잭션이 롤백되면 단계는 `RUNNING` · `refunded_credits = 0` 으로
     * 돌아온다. 그 상태를 그대로 재현해, 다음 정산 시도(복구기 훑기·재실행·삭제)가 같은 행을
     * `findUnsettled` 로 찾아 이번에는 실제로 환불하는지 확인한다.
     *
     * 오케스트레이터가 실패 뒤에 단계를 `FAILED` 로 닫으면 `findUnsettled` 가 `RUNNING` 만
     * 보므로 이 두 번째 기회가 사라진다 — 그래서 닫지 않는다.
     */
    @Test
    @DisplayName("환불 실패로 롤백된 단계는 다음 정산이 다시 집어 환불한다")
    fun rolledBackStageIsPickedUpAgain() {
        val open = stageRow()
        every { runStageRepository.settleRefund(100L, 5, any()) } returns true
        every { creditService.refundAllocation(any()) } throws IllegalStateException("환불 실패")

        assertThrows<IllegalStateException> { subject.settleStage(userId, open, "중단됨") }

        // 실패한 첫 시도는 여기까지다. 두 번째 시도만 세기 위해 호출 기록을 비운다.
        io.mockk.clearMocks(creditService, answers = false)

        // 롤백 후의 DB 상태: 단계는 여전히 RUNNING·미정산이라 조회에 다시 걸린다.
        every { runStageRepository.findUnsettled(7L, 0) } returns listOf(open)
        every { creditService.refundAllocation(any()) } returns Unit

        subject.settleBeforeDiscard(runId = 7L, userId = userId, fromSortOrder = 0, reason = "재시도")

        verify(exactly = 1) { creditService.refundAllocation(any()) }
    }

    /* ── (1) 레거시 NULL 무시 ─────────────────────────────────────────── */

    /**
     * **분해가 없으면 환불하지 않는다.**
     *
     * 출처를 모르는 채 총액을 무료분으로 돌려주면 구매분이 유효기간 있는 것으로 바뀌거나
     * `free_monthly` 한도에 걸려 증발한다 — 이 컬럼이 막으려는 바로 그 손실이다.
     *
     * **표식도 세우지 않는다.** 한 푼도 돌려주지 않은 행이 "정산됨" 으로 남으면 수기 복구와
     * 감사가 근거를 잃는다. 자동으로 닫지 말고 사람에게 넘긴다.
     */
    @Test
    @DisplayName("V111 이전 행(분해 NULL)은 환불도 표식도 하지 않고 보류한다")
    fun legacyRowsAreHeldForManualReview() {
        val error = assertThrows<ShortsStageSettlementRequiresManualReviewException> {
            subject.settleStage(userId, stageRow(alloc = null), "중단됨")
        }

        assertEquals(100L, error.stageId)
        assertEquals(5, error.amount)
        verify(exactly = 0) { creditService.refundAllocation(any()) }
        /*
         * **표식을 세우지 않는다.** 한 푼도 돌려주지 않은 행이 "정산됨" 으로 남으면
         * 수기 복구와 감사가 근거를 잃는다.
         */
        verify(exactly = 0) { runStageRepository.settleRefund(any(), any(), any()) }
    }

    /** 레거시 행이 섞여 있으면 지우기도 중단되어야 한다 — 지우면 근거가 영영 사라진다. */
    @Test
    @DisplayName("레거시 행이 있으면 삭제·재실행을 중단한다")
    fun legacyRowsAbortTheDiscard() {
        every { runStageRepository.findUnsettled(runId, 0) } returns listOf(stageRow(alloc = null))

        assertThrows<ShortsStageSettlementRequiresManualReviewException> {
            subject.settleBeforeDiscard(runId, userId, fromSortOrder = 0, reason = "삭제")
        }

        verify(exactly = 0) { runStageRepository.settleRefund(any(), any(), any()) }
    }

    /* ── (4) 지우기 전 정산 ───────────────────────────────────────────── */

    @Test
    @DisplayName("지울 범위의 미정산 단계만 정산한다")
    fun settleBeforeDiscardOnlyTouchesUnsettled() {
        every { runStageRepository.findUnsettled(runId, 3) } returns listOf(stageRow(id = 100L))
        every { runStageRepository.settleRefund(100L, 5, any()) } returns true

        subject.settleBeforeDiscard(runId, userId, fromSortOrder = 3, reason = "재실행")

        verify(exactly = 1) { runStageRepository.findUnsettled(runId, 3) }
        verify(exactly = 1) { creditService.refundAllocation(any()) }
    }

    /**
     * **정산이 실패하면 예외를 올린다 — 삼키고 진행하면 안 된다.**
     *
     * 호출자(재실행·삭제)는 `@Transactional` 이므로 그 트랜잭션이 롤백되어 단계 행이 지워지지
     * 않는다. 지운 뒤에는 분해가 사라져 되돌릴 근거가 없으므로, 정산하지 못한 채 지우는 것은
     * 사용자의 크레딧을 조용히 없애는 일이다. 롤백되므로 다시 시도하면 같은 행을 다시 집는다.
     */
    @Test
    @DisplayName("정산이 실패하면 예외를 올려 삭제를 막는다")
    fun settlementFailureAbortsTheDiscard() {
        every { runStageRepository.findUnsettled(runId, 0) } returns
            listOf(stageRow(id = 100L), stageRow(id = 200L))
        every { runStageRepository.settleRefund(100L, 5, any()) } throws IllegalStateException("DB 오류")

        assertThrows<IllegalStateException> {
            subject.settleBeforeDiscard(runId, userId, fromSortOrder = 0, reason = "삭제")
        }

        // 뒤 단계로 넘어가지 않는다 — 앞 단계를 정산하지 못한 채 진행할 이유가 없다.
        verify(exactly = 0) { runStageRepository.settleRefund(200L, any(), any()) }
    }

    /** 환불 실패도 마찬가지로 올라가야 표식이 롤백되고 재시도가 가능하다. */
    @Test
    @DisplayName("환불 실패도 올려서 삭제를 막는다")
    fun refundFailureAbortsTheDiscard() {
        every { runStageRepository.findUnsettled(runId, 0) } returns listOf(stageRow())
        every { runStageRepository.settleRefund(100L, 5, any()) } returns true
        every { creditService.refundAllocation(any()) } throws IllegalStateException("환불 실패")

        assertThrows<IllegalStateException> {
            subject.settleBeforeDiscard(runId, userId, fromSortOrder = 0, reason = "삭제")
        }
    }

}
