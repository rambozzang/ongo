package com.ongo.application.ai

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.slf4j.LoggerFactory
import com.ongo.application.credit.CreditAllocation
import com.ongo.application.credit.CreditService
import com.ongo.domain.ai.AiPipeline
import com.ongo.domain.ai.AiPipelineRepository
import com.ongo.domain.ai.AiPipelineStep
import com.ongo.domain.ai.PipelineStatus
import com.ongo.domain.ai.PipelineStepStatus
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import com.ongo.application.subscription.DummyTransactionManagerForTest
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AiPipelineUseCaseTest {
    private val stt = mockk<SttUseCase>(relaxed = true)
    private val analysis = mockk<AnalyzeScriptUseCase>(relaxed = true)
    private val meta = mockk<GenerateMetaUseCase>(relaxed = true)
    private val hashtags = mockk<GenerateHashtagsUseCase>(relaxed = true)
    private val schedule = mockk<SuggestScheduleUseCase>(relaxed = true)
    private val credits = mockk<CreditService>(relaxed = true)
    private val videos = mockk<VideoRepository>()
    private val repository = mockk<AiPipelineRepository>()

    @Test
    fun `start persists pipeline with channel before asynchronous execution`() {
        every { videos.findById(42L) } returns Video(id = 42L, userId = 7L, title = "테스트 영상")
        every { repository.save(any()) } answers { firstArg() }
        every { repository.claimForExecution(any(), any(), any()) } returns null

        val pipeline = useCase().startPipeline(
            userId = 7L,
            videoId = 42L,
            stepNames = listOf(AiPipelineStep.SUGGEST_SCHEDULE.name),
            channelId = 99L,
        )

        assertEquals(PipelineStatus.PENDING, pipeline.status)
        assertEquals(99L, pipeline.channelId)
        verify { repository.save(match { it.id == pipeline.id && it.channelId == 99L }) }
    }

    @Test
    fun `status lookup reads durable repository instead of process memory`() {
        val persisted = AiPipeline(
            id = "persisted",
            userId = 7L,
            videoId = 42L,
            steps = listOf(AiPipelineStep.GENERATE_META),
            status = PipelineStatus.RUNNING,
        )
        every { repository.findById("persisted") } returns persisted

        assertSame(persisted, useCase().getPipelineStatus(7L, "persisted"))
    }

    /* ---- 정산 ---- */

    private val allSteps = AiPipelineStep.entries.toList()

    /**
     * @param creditAllocation 차감 출처 분해. 기본은 무료 2 + 패키지 11 에서 나머지다.
     *   `null` 은 **V108 마이그레이션 이전 행**이며 자동 환불 대상이 아니다.
     */
    private fun runningPipeline(
        statuses: Map<AiPipelineStep, PipelineStepStatus> = allSteps.associateWith { PipelineStepStatus.PENDING },
        creditAllocation: com.ongo.domain.ai.PipelineCreditAllocation? = defaultAllocation(),
    ): AiPipeline {
        val pipeline = AiPipeline(
            id = "p-1",
            userId = 7L,
            videoId = 42L,
            steps = allSteps,
            status = PipelineStatus.RUNNING,
            totalCreditsCharged = AiPipelineStep.calculateTotalCost(allSteps),
            creditAllocation = creditAllocation,
        )
        statuses.forEach { (step, status) -> pipeline.stepStatuses[step] = status }
        return pipeline
    }

    private fun defaultAllocation() = com.ongo.domain.ai.PipelineCreditAllocation(
        freeAmount = 2,
        purchasedAmounts = mapOf(11L to AiPipelineStep.calculateTotalCost(allSteps) - 2),
    )

    /**
     * 취소는 미사용 스텝분을 돌려준다. 금액은 raw cost 비례 배분 합계여야 한다 —
     * 실행분에 할인을 다시 적용하면 사용자가 낸 단가보다 비싸게 계산된다.
     */
    @Test
    fun `취소는 미사용 스텝분을 비례 배분으로 환불한다`() {
        val pipeline = runningPipeline(
            mapOf(
                AiPipelineStep.STT to PipelineStepStatus.COMPLETED,
                AiPipelineStep.ANALYZE_SCRIPT to PipelineStepStatus.PENDING,
                AiPipelineStep.GENERATE_META to PipelineStepStatus.PENDING,
                AiPipelineStep.GENERATE_HASHTAGS to PipelineStepStatus.PENDING,
                AiPipelineStep.SUGGEST_SCHEDULE to PipelineStepStatus.PENDING,
            ),
        )
        every { repository.findById("p-1") } returns pipeline
        every { repository.save(any()) } answers { firstArg() }
        every { repository.settleRefund("p-1", any(), any(), any()) } returns true

        useCase().cancelPipeline(7L, "p-1")

        val shares = com.ongo.domain.ai.AiPipelineSettlement.distribute(allSteps, pipeline.totalCreditsCharged)
        val expected = pipeline.totalCreditsCharged - shares.getValue(AiPipelineStep.STT)
        // 저장된 출처 분해로 환불한다. 금액만 넘기면 구매분이 무료분으로 바뀐다.
        val refunded = slot<CreditAllocation>()
        verify(exactly = 1) { credits.refundAllocation(capture(refunded), expected) }
        assertEquals(2, refunded.captured.freeAmount)
        assertEquals(
            listOf(CreditAllocation.PurchasedPortion(11L, pipeline.totalCreditsCharged - 2)),
            refunded.captured.purchasedPortions,
            "구매 패키지 몫이 보존되지 않았다",
        )
    }

    /**
     * **정산 표식이 이기지 못하면 환불하지 않는다.**
     *
     * 예전에는 환불이 먼저였고 상태 저장이 나중이라, 저장이 실패하면 "이미 취소됨" 가드를
     * 다시 통과해 두 번 환불됐다. 이제 DB 의 조건부 갱신이 승자를 정하고 이긴 쪽만 돈을
     * 돌려준다.
     */
    @Test
    fun `이미 정산된 파이프라인은 다시 환불하지 않는다`() {
        val pipeline = runningPipeline()
        every { repository.findById("p-1") } returns pipeline
        every { repository.save(any()) } answers { firstArg() }
        every { repository.settleRefund("p-1", any(), any(), any()) } returns false

        useCase().cancelPipeline(7L, "p-1")

        verify(exactly = 0) { credits.refundAllocation(any(), any()) }
    }

    /**
     * **환불 실패는 정산 표식과 함께 롤백돼야 한다.**
     *
     * 예전에는 표식을 먼저 커밋하고 환불을 따로 불렀다. 환불이 실패하면 표식만 남아
     * `refunded_credits = 0` 조건을 다시 통과하지 못하고, **자동 재시도가 영구히 막혔다** —
     * 크레딧은 사라지고 복구 경로도 닫힌다.
     *
     * 이제 둘이 한 트랜잭션이다. 환불이 던지면 트랜잭션이 롤백을 요청하고 예외가 그대로
     * 올라간다. 사용자는 취소가 실패한 것을 보고 다시 시도할 수 있다.
     */
    @Test
    fun `환불이 실패하면 정산 트랜잭션을 롤백하고 예외를 올린다`() {
        val pipeline = runningPipeline()
        every { repository.findById("p-1") } returns pipeline
        every { repository.save(any()) } answers { firstArg() }
        every { repository.settleRefund("p-1", any(), any(), any()) } returns true
        every { credits.refundAllocation(any(), any()) } throws IllegalStateException("환불 저장 실패")
        val manager = RecordingTransactionManager()

        assertFailsWith<IllegalStateException> {
            useCase(manager).cancelPipeline(7L, "p-1")
        }

        assertTrue(manager.events.contains("rollback"), "롤백을 요청하지 않았다: ${manager.events}")
        assertTrue(!manager.events.contains("commit"), "환불이 실패했는데 커밋했다: ${manager.events}")
    }

    // ── 레거시 행: 출처를 모르면 자동 환불하지 않는다 ────────────────────────

    /**
     * **V108 마이그레이션 이전에 만들어진 파이프라인**은 차감 분해가 없다.
     *
     * 그 상태에서 금액만 들고 환불하면 구매분에서 나간 크레딧이 **월말에 사라지는
     * 무료분으로 바뀌거나** `free_monthly` 한도에 걸려 증발한다. 그것이 이 스냅샷이
     * 막으려는 손실이므로, 출처를 모르면 자동 환불을 하지 않는다.
     */
    @Test
    fun `차감 출처가 없으면 자동 환불하지 않는다`() {
        val pipeline = runningPipeline(creditAllocation = null)
        every { repository.findById("p-1") } returns pipeline
        every { repository.save(any()) } answers { firstArg() }
        every { repository.settleRefund("p-1", any(), any(), any()) } returns true

        useCase().cancelPipeline(7L, "p-1")

        verify(exactly = 0) { credits.refundAllocation(any(), any()) }
        verify(exactly = 0) { credits.refundAllocation(any()) }
        // 예전 출처 불명 API 로 되돌아가는 것은 컴파일 자체가 안 된다
        // (CreditService.refundCredit 은 DeprecationLevel.ERROR 다).
    }

    /**
     * 레거시 행도 **상태는 확정한다.** 미정산으로 남겨 두면 복구 tick 이 영원히 같은
     * 행을 다시 집는다. 대신 수기 정산에 필요한 값을 CRITICAL 로그로 남긴다 —
     * 그 로그가 유일한 복구 근거다.
     */
    @Test
    fun `차감 출처가 없어도 정산 상태는 확정하고 수기 복구 로그를 남긴다`() {
        val pipeline = runningPipeline(creditAllocation = null)
        every { repository.findById("p-1") } returns pipeline
        every { repository.save(any()) } answers { firstArg() }
        every { repository.settleRefund("p-1", any(), any(), any()) } returns true

        val logger = LoggerFactory.getLogger(AiPipelineUseCase::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
        try {
            useCase().cancelPipeline(7L, "p-1")
        } finally {
            logger.detachAppender(appender)
        }

        verify(exactly = 1) { repository.settleRefund("p-1", any(), PipelineStatus.CANCELLED, any()) }
        val critical = appender.list.filter { it.level == Level.ERROR }.map { it.formattedMessage }
        assertTrue(
            critical.any { it.contains("CRITICAL") && it.contains("p-1") && it.contains("7") },
            "수기 정산에 필요한 CRITICAL 로그가 없다: $critical",
        )
    }

    /**
     * 정산이 이기지 못하면 레거시 경로도 아무것도 하지 않는다. 로그만 쌓이면 운영이
     * 실제 미정산 건과 이미 처리된 건을 구분하지 못한다.
     */
    @Test
    fun `이미 정산된 레거시 행은 경고도 남기지 않는다`() {
        val pipeline = runningPipeline(creditAllocation = null)
        every { repository.findById("p-1") } returns pipeline
        every { repository.save(any()) } answers { firstArg() }
        every { repository.settleRefund("p-1", any(), any(), any()) } returns false

        val logger = LoggerFactory.getLogger(AiPipelineUseCase::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
        try {
            useCase().cancelPipeline(7L, "p-1")
        } finally {
            logger.detachAppender(appender)
        }

        assertTrue(appender.list.none { it.level == Level.ERROR }, "이미 정산된 건에 CRITICAL 을 남겼다")
    }

    // ── 저장 시 스냅샷이 지워지지 않는다 ─────────────────────────────────────

    /**
     * `save` 는 실행 중 상태를 자주 덮어쓴다. 그때 스냅샷이 지워지면 정산이 출처를 잃고
     * fail-closed 로 떨어져 **고객이 환불을 못 받는다.** `refunded_credits` 와 같은 이유로
     * 저장소는 이 컬럼을 생성 시점에만 쓴다.
     */
    @Test
    fun `정산 후 저장이 차감 출처 스냅샷을 지우지 않는다`() {
        val pipeline = runningPipeline()
        val saved = mutableListOf<AiPipeline>()
        every { repository.findById("p-1") } returns pipeline
        every { repository.save(any()) } answers { saved += firstArg<AiPipeline>(); firstArg() }
        every { repository.settleRefund("p-1", any(), any(), any()) } returns true

        useCase().cancelPipeline(7L, "p-1")

        assertTrue(saved.isNotEmpty(), "저장이 일어나지 않았다")
        assertTrue(
            saved.all { it.creditAllocation != null },
            "저장 경로가 차감 출처를 지웠다 — 정산이 출처를 잃는다",
        )
    }

    /** 표식과 환불이 같은 경계 안에서 일어나야 롤백이 의미를 갖는다. */
    @Test
    fun `정산은 표식과 환불을 한 트랜잭션에서 처리한다`() {
        val pipeline = runningPipeline()
        every { repository.findById("p-1") } returns pipeline
        every { repository.save(any()) } answers { firstArg() }
        every { repository.settleRefund("p-1", any(), any(), any()) } answers {
            manager.events += "marker"
            true
        }
        every { credits.refundAllocation(any(), any()) } answers { manager.events += "refund" }

        useCase(manager).cancelPipeline(7L, "p-1")

        assertEquals(listOf("begin", "marker", "refund", "commit"), manager.events)
        assertEquals(
            TransactionDefinition.PROPAGATION_REQUIRES_NEW,
            manager.propagations.single(),
        )
    }

    private val manager = RecordingTransactionManager()

    /** 경계 요청과 커밋·롤백 순서를 기록한다. Dummy 는 commit/rollback 을 무시해 못 잡는다. */
    private class RecordingTransactionManager : PlatformTransactionManager {
        val events = mutableListOf<String>()
        val propagations = mutableListOf<Int>()

        override fun getTransaction(definition: TransactionDefinition?): TransactionStatus {
            propagations += definition?.propagationBehavior ?: -1
            events += "begin"
            return SimpleTransactionStatus()
        }

        override fun commit(status: TransactionStatus) {
            events += "commit"
        }

        override fun rollback(status: TransactionStatus) {
            events += "rollback"
        }
    }

    /* ---- 중복 스텝 ---- */

    /**
     * 선차감은 리스트를 그대로 합산해 중복을 두 번 청구하는데, 상태·결과·정산은 스텝을
     * 키로 하는 맵이라 하나로 합쳐진다. **두 번 받고 한 번만 돌려주는** 구조라 입력에서
     * 막는다.
     */
    @Test
    fun `같은 스텝을 두 번 선택하면 거절한다`() {
        every { videos.findById(42L) } returns Video(id = 42L, userId = 7L, title = "테스트 영상")

        val error = assertFailsWith<com.ongo.common.exception.BusinessException> {
            useCase().startPipeline(
                userId = 7L,
                videoId = 42L,
                stepNames = listOf(AiPipelineStep.STT.name, AiPipelineStep.STT.name),
                channelId = null,
            )
        }

        assertEquals("DUPLICATE_STEP", error.code)
        // 차감도 저장도 하지 않는다 — 거절은 외부 상태를 남기지 않아야 한다.
        verify(exactly = 0) { credits.validateAndDeduct(any(), any(), any()) }
        verify(exactly = 0) { repository.save(any()) }
    }

    /** 전 스텝이 소비됐으면 돌려줄 것이 없다. 0원 환불을 부르면 원장에 잡음이 남는다. */
    @Test
    fun `전 스텝이 소비됐으면 환불을 호출하지 않는다`() {
        val pipeline = runningPipeline(allSteps.associateWith { PipelineStepStatus.COMPLETED })
        every { repository.findById("p-1") } returns pipeline
        every { repository.save(any()) } answers { firstArg() }
        every { repository.settleRefund("p-1", 0, any(), any()) } returns true

        useCase().cancelPipeline(7L, "p-1")

        verify(exactly = 0) { credits.refundAllocation(any()) }
    }

    /** RUNNING 은 외부 호출이 이미 나갔을 수 있다. 취소와 자연 실패가 같은 기준을 쓴다. */
    @Test
    fun `RUNNING 스텝은 소비로 보고 환불에서 제외한다`() {
        val pipeline = runningPipeline(
            mapOf(
                AiPipelineStep.STT to PipelineStepStatus.RUNNING,
                AiPipelineStep.ANALYZE_SCRIPT to PipelineStepStatus.PENDING,
                AiPipelineStep.GENERATE_META to PipelineStepStatus.PENDING,
                AiPipelineStep.GENERATE_HASHTAGS to PipelineStepStatus.PENDING,
                AiPipelineStep.SUGGEST_SCHEDULE to PipelineStepStatus.PENDING,
            ),
        )
        every { repository.findById("p-1") } returns pipeline
        every { repository.save(any()) } answers { firstArg() }
        every { repository.settleRefund("p-1", any(), any(), any()) } returns true

        useCase().cancelPipeline(7L, "p-1")

        val shares = com.ongo.domain.ai.AiPipelineSettlement.distribute(allSteps, pipeline.totalCreditsCharged)
        val expected = pipeline.totalCreditsCharged - shares.getValue(AiPipelineStep.STT)
        verify(exactly = 1) { credits.refundAllocation(any(), expected) }
    }

    /** 정산은 상태 저장 뒤다. 정산이 실패해도 어떤 스텝이 취소됐는지는 남아야 한다. */
    @Test
    fun `취소는 스텝 상태를 먼저 저장한다`() {
        val pipeline = runningPipeline()
        every { repository.findById("p-1") } returns pipeline
        every { repository.save(any()) } answers { firstArg() }
        every { repository.settleRefund("p-1", any(), any(), any()) } returns true

        useCase().cancelPipeline(7L, "p-1")

        verify {
            repository.save(
                match<AiPipeline> { saved ->
                    saved.stepStatuses.values.all { it == PipelineStepStatus.SKIPPED }
                },
            )
        }
    }

    private fun useCase(
        transactionManager: PlatformTransactionManager = DummyTransactionManagerForTest(),
    ) = AiPipelineUseCase(
        sttUseCase = stt,
        analyzeScriptUseCase = analysis,
        generateMetaUseCase = meta,
        generateHashtagsUseCase = hashtags,
        suggestScheduleUseCase = schedule,
        creditService = credits,
        videoRepository = videos,
        pipelineRepository = repository,
        transactionManager = transactionManager,
    )
}
