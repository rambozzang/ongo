package com.ongo.application.paddle

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.ongo.application.credit.CreditService
import com.ongo.common.enums.AuthProvider
import com.ongo.common.enums.CreditPackage
import com.ongo.common.enums.PaymentStatus
import com.ongo.common.enums.PaymentType
import com.ongo.common.enums.PlanType
import com.ongo.common.enums.SubscriptionStatus
import com.ongo.common.exception.UnauthorizedException
import com.ongo.domain.payment.Payment
import com.ongo.domain.payment.PaymentRepository
import com.ongo.domain.subscription.Subscription
import com.ongo.domain.subscription.SubscriptionRepository
import com.ongo.domain.user.User
import com.ongo.domain.user.UserRepository
import com.ongo.domain.webhook.WebhookEvent
import com.ongo.domain.webhook.WebhookEventRepository
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import com.ongo.application.subscription.DummyTransactionManagerForTest
import com.ongo.application.webhook.WebhookEventRecorder
import com.ongo.application.webhook.WebhookInboundGuard
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class PaddleWebhookServiceTest {

    @MockK
    private lateinit var paddleGateway: PaddleGateway

    @MockK
    private lateinit var subscriptionRepository: SubscriptionRepository

    @MockK
    private lateinit var paymentRepository: PaymentRepository

    @MockK
    private lateinit var userRepository: UserRepository

    @MockK
    private lateinit var creditService: CreditService

    @MockK
    private lateinit var webhookEventRepository: WebhookEventRepository

    private val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())

    private lateinit var paddleWebhookService: PaddleWebhookService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        paddleWebhookService = PaddleWebhookService(
            paddleGateway,
            subscriptionRepository,
            paymentRepository,
            userRepository,
            creditService,
            objectMapper,
            webhookEventRepository,
            // 실패 기록은 업무 트랜잭션과 분리돼야 한다. 경계 자체는 통합 테스트 몫이라
            // 여기서는 콜백을 그대로 실행하는 매니저를 쓴다.
            WebhookInboundGuard(
                webhookEventRepository,
                WebhookEventRecorder(webhookEventRepository, DummyTransactionManagerForTest()),
                DummyTransactionManagerForTest(),
            ),
        )
        every { creditService.applyPlanEntitlement(any(), any(), any()) } returns Unit
        /*
         * 동시 전달 직렬화를 위해 업무 처리 전에 행을 잠근다. 단위 테스트에는 경합 상대가
         * 없으므로 잠금 조회는 비어 있고, 서비스는 방금 기록한 이벤트를 그대로 쓴다.
         * 경합 자체는 `PaddleWebhookConcurrencyTest` 가 고정한다.
         */
        every { webhookEventRepository.findByEventIdForUpdate(any()) } returns null
        every { webhookEventRepository.updateIfNotProcessed(any()) } returns true
        /*
         * 수신 기록은 원자적 삽입(`ON CONFLICT DO NOTHING`)이다. 검사-후-삽입으로 하면
         * 동시에 들어온 두 전달이 모두 조회를 통과해 유니크 위반으로 트랜잭션이 abort 된다.
         * 삽입은 영향 행수만 돌려주므로 id 가 없고, 이후 상태 갱신은 전부 `event_id` 로 한다.
         */
        every { webhookEventRepository.saveIfAbsent(any()) } returns true
        every { webhookEventRepository.markProcessed(any(), any()) } returns true
    }

    private fun createUser(id: Long, planType: PlanType = PlanType.FREE): User = User(
        id = id,
        email = "user$id@test.com",
        name = "TestUser$id",
        provider = AuthProvider.GOOGLE,
        providerId = "google_$id",
        planType = planType,
    )

    private fun currentTimestamp(): Long = System.currentTimeMillis() / 1000

    private fun createSignature(ts: Long = currentTimestamp(), hash: String = "valid_hash"): String =
        "ts=$ts;h1=$hash"

    /**
     * 기본값은 **실제 Paddle `subscription.created` 이벤트의 모양**이다.
     *
     * 예전 픽스처에는 `next_billed_at` 이 없었다. 실 이벤트에는 활성 구독이면 항상 들어
     * 있는 값이고, 그것이 빠진 상태를 성공 케이스의 기준으로 두면 "기간 없는 ACTIVE 유료
     * 구독"이 정상처럼 보인다. 그래서 픽스처를 실제 모양으로 **완성**했다 — 검증을 낮춘
     * 것이 아니라 기준을 실제에 맞춘 것이다.
     *
     * 각 시각은 `null` 로 넘겨 **필드를 통째로 빼거나**, 잘못된 문자열을 넘겨 **형식 오류**를
     * 만들 수 있다. [includeBillingPeriod] 는 `current_billing_period` 블록 자체를 없앤다.
     */
    private fun buildSubscriptionCreatedPayload(
        eventId: String = "evt_123",
        paddleSubId: String = "sub_paddle_123",
        customerId: String = "ctm_123",
        userId: Long = 1L,
        priceId: String = "pri_starter",
        startsAt: String? = "2024-01-01T00:00:00Z",
        endsAt: String? = "2024-02-01T00:00:00Z",
        nextBilledAt: String? = "2024-02-01T00:00:00Z",
        includeBillingPeriod: Boolean = true,
    ): String {
        val fields = buildList {
            add(""""id": "$paddleSubId"""")
            add(""""customer_id": "$customerId"""")
            add(""""custom_data": { "user_id": $userId }""")
            add(""""items": [{ "price": { "id": "$priceId" } }]""")
            add(""""status": "active"""")
            if (includeBillingPeriod) {
                val period = listOfNotNull(
                    startsAt?.let { """"starts_at": "$it"""" },
                    endsAt?.let { """"ends_at": "$it"""" },
                ).joinToString(", ")
                add(""""current_billing_period": { $period }""")
            }
            nextBilledAt?.let { add(""""next_billed_at": "$it"""") }
        }
        return """
            {
              "event_type": "subscription.created",
              "event_id": "$eventId",
              "data": { ${fields.joinToString(", ")} }
            }
        """.trimIndent()
    }

    /**
     * `subscription.updated` 이벤트. [status] 로 전환 대상 상태를, 각 시각으로 기간 유무를
     * 만든다. 기간을 전부 `null` 로 두면 "이벤트가 기간을 싣지 않은" 갱신이 된다.
     */
    private fun buildSubscriptionUpdatedPayload(
        eventId: String = "evt_upd_1",
        paddleSubId: String = "sub_paddle_123",
        priceId: String = "pri_starter",
        status: String = "active",
        startsAt: String? = null,
        endsAt: String? = null,
        nextBilledAt: String? = null,
    ): String {
        val fields = buildList {
            add(""""id": "$paddleSubId"""")
            add(""""items": [{ "price": { "id": "$priceId" } }]""")
            add(""""status": "$status"""")
            val period = listOfNotNull(
                startsAt?.let { """"starts_at": "$it"""" },
                endsAt?.let { """"ends_at": "$it"""" },
            ).joinToString(", ")
            if (period.isNotEmpty()) add(""""current_billing_period": { $period }""")
            nextBilledAt?.let { add(""""next_billed_at": "$it"""") }
        }
        return """
            {
              "event_type": "subscription.updated",
              "event_id": "$eventId",
              "data": { ${fields.joinToString(", ")} }
            }
        """.trimIndent()
    }

    /** 요금제 판별은 월간·연간 가격 ID 를 모두 대조하므로 결제 주기를 가리지 않고 stub 한다. */
    private fun stubPriceIds() {
        every { paddleGateway.getPriceIdForPlan("FREE", any()) } returns null
        every { paddleGateway.getPriceIdForPlan("STARTER", any()) } returns "pri_starter"
        every { paddleGateway.getPriceIdForPlan("PRO", any()) } returns "pri_pro"
        every { paddleGateway.getPriceIdForPlan("BUSINESS", any()) } returns "pri_business"
    }

    /**
     * 저장도 크레딧 지급도 **일어나지 않았음**을 고정한다.
     *
     * 예외만 확인하면 부족하다. 실패 경로가 이미 구독을 저장한 뒤에 터지면 기간 없는
     * ACTIVE 행이 남고, 재시도는 그 행을 다시 덮어쓸 뿐이다.
     */
    private fun verifyNothingPersisted() {
        verify(exactly = 0) { subscriptionRepository.save(any()) }
        verify(exactly = 0) { subscriptionRepository.update(any()) }
        verify(exactly = 0) { creditService.applyPlanEntitlement(any(), any(), any()) }
        verify(exactly = 0) { userRepository.update(any()) }
    }

    private fun buildSubscriptionCanceledPayload(
        eventId: String = "evt_cancel_1",
        paddleSubId: String = "sub_paddle_123",
    ): String = """
        {
          "event_type": "subscription.canceled",
          "event_id": "$eventId",
          "data": {
            "id": "$paddleSubId",
            "customer_id": "ctm_123",
            "status": "canceled"
          }
        }
    """.trimIndent()

    private fun buildTransactionCompletedPayload(
        eventId: String = "evt_txn_1",
        transactionId: String = "txn_123",
        userId: Long = 1L,
        subscriptionId: String? = "sub_paddle_123",
        totalAmount: String = "9900",
        currencyCode: String = "KRW",
    ): String {
        val subscriptionField = if (subscriptionId != null) {
            """"subscription_id": "$subscriptionId","""
        } else {
            ""
        }
        return """
            {
              "event_type": "transaction.completed",
              "event_id": "$eventId",
              "data": {
                "id": "$transactionId",
                "custom_data": { "user_id": $userId },
                $subscriptionField
                "details": {
                  "totals": {
                    "total": "$totalAmount",
                    "currency_code": "$currencyCode"
                  }
                },
                "payments": [
                  {
                    "method_details": {
                      "type": "card"
                    }
                  }
                ]
              }
            }
        """.trimIndent()
    }

    private fun buildTransactionRefundedPayload(
        eventId: String = "evt_refund_1",
        transactionId: String = "txn_123",
    ): String = """
        {
          "event_type": "transaction.refunded",
          "event_id": "$eventId",
          "data": {
            "id": "$transactionId"
          }
        }
    """.trimIndent()

    @Test
    @DisplayName("서명 검증 실패 시 UnauthorizedException이 발생해야 한다")
    fun `서명 검증 실패 - UnauthorizedException`() {
        // given
        val rawBody = buildSubscriptionCreatedPayload()
        val signature = createSignature()

        every { paddleGateway.verifyWebhookSignature(rawBody, signature) } returns false

        // when & then
        assertThrows(UnauthorizedException::class.java) {
            paddleWebhookService.handleWebhook(rawBody, signature)
        }
    }

    @Test
    @DisplayName("이미 처리된 이벤트는 스킵되어야 한다")
    fun `이미 처리된 이벤트 스킵`() {
        // given
        val rawBody = buildSubscriptionCreatedPayload(eventId = "evt_already_processed")
        val signature = createSignature()

        every { paddleGateway.verifyWebhookSignature(rawBody, signature) } returns true
        every { webhookEventRepository.findByEventId("evt_already_processed") } returns WebhookEvent(
            id = 1L,
            eventId = "evt_already_processed",
            eventType = "subscription.created",
            payload = rawBody,
            status = "PROCESSED",
        )

        // when
        paddleWebhookService.handleWebhook(rawBody, signature)

        // then - 구독 처리가 일어나지 않아야 함
        verify(exactly = 0) { subscriptionRepository.save(any()) }
        verify(exactly = 0) { subscriptionRepository.update(any()) }
    }

    @Test
    @DisplayName("subscription.created 이벤트 시 구독이 생성/업데이트되어야 한다 (기존 구독 없는 경우)")
    fun `subscription created - 신규 구독 생성`() {
        // given
        val rawBody = buildSubscriptionCreatedPayload(
            eventId = "evt_new_sub",
            paddleSubId = "sub_new_123",
            customerId = "ctm_new",
            userId = 10L,
            priceId = "pri_starter",
        )
        val signature = createSignature()
        val user = createUser(10L)

        every { paddleGateway.verifyWebhookSignature(rawBody, signature) } returns true
        every { webhookEventRepository.findByEventId("evt_new_sub") } returns null
        every { subscriptionRepository.findByUserId(10L) } returns null
        every { subscriptionRepository.save(any()) } answers { firstArg() }
        every { userRepository.findById(10L) } returns user
        every { userRepository.update(any()) } answers { firstArg() }
        // resolvePlanType 이 월간·연간 가격 ID 를 모두 대조하므로 결제 주기를 가리지 않고 stub 한다.
        every { paddleGateway.getPriceIdForPlan("FREE", any()) } returns null
        every { paddleGateway.getPriceIdForPlan("STARTER", any()) } returns "pri_starter"
        every { paddleGateway.getPriceIdForPlan("PRO", any()) } returns "pri_pro"
        every { paddleGateway.getPriceIdForPlan("BUSINESS", any()) } returns "pri_business"

        // when
        paddleWebhookService.handleWebhook(rawBody, signature)

        // then
        val subSlot = slot<Subscription>()
        verify { subscriptionRepository.save(capture(subSlot)) }
        assertEquals(10L, subSlot.captured.userId)
        assertEquals(PlanType.STARTER, subSlot.captured.planType)
        assertEquals(SubscriptionStatus.ACTIVE, subSlot.captured.status)
        assertEquals("sub_new_123", subSlot.captured.paddleSubscriptionId)
        assertEquals("ctm_new", subSlot.captured.paddleCustomerId)

        val userSlot = slot<User>()
        verify { userRepository.update(capture(userSlot)) }
        assertEquals(PlanType.STARTER, userSlot.captured.planType)
        assertEquals("ctm_new", userSlot.captured.paddleCustomerId)
        verify { creditService.applyPlanEntitlement(10L, PlanType.STARTER, "PADDLE_SUBSCRIPTION_PAID") }
    }

    @Test
    @DisplayName("subscription.created 이벤트 시 기존 구독이 있으면 업데이트되어야 한다")
    fun `subscription created - 기존 구독 업데이트`() {
        // given
        val rawBody = buildSubscriptionCreatedPayload(
            eventId = "evt_update_sub",
            paddleSubId = "sub_update_123",
            customerId = "ctm_update",
            userId = 20L,
            priceId = "pri_pro",
        )
        val signature = createSignature()
        val existingSub = Subscription(
            id = 5L,
            userId = 20L,
            planType = PlanType.FREE,
            status = SubscriptionStatus.FREE,
        )
        val user = createUser(20L)

        every { paddleGateway.verifyWebhookSignature(rawBody, signature) } returns true
        every { webhookEventRepository.findByEventId("evt_update_sub") } returns null
        every { subscriptionRepository.findByUserId(20L) } returns existingSub
        every { subscriptionRepository.update(any()) } answers { firstArg() }
        every { userRepository.findById(20L) } returns user
        every { userRepository.update(any()) } answers { firstArg() }
        // resolvePlanType 이 월간·연간 가격 ID 를 모두 대조하므로 결제 주기를 가리지 않고 stub 한다.
        every { paddleGateway.getPriceIdForPlan("FREE", any()) } returns null
        every { paddleGateway.getPriceIdForPlan("STARTER", any()) } returns "pri_starter"
        every { paddleGateway.getPriceIdForPlan("PRO", any()) } returns "pri_pro"
        every { paddleGateway.getPriceIdForPlan("BUSINESS", any()) } returns "pri_business"

        // when
        paddleWebhookService.handleWebhook(rawBody, signature)

        // then
        val subSlot = slot<Subscription>()
        verify { subscriptionRepository.update(capture(subSlot)) }
        assertEquals(20L, subSlot.captured.userId)
        assertEquals(PlanType.PRO, subSlot.captured.planType)
        assertEquals(SubscriptionStatus.ACTIVE, subSlot.captured.status)
        assertEquals("sub_update_123", subSlot.captured.paddleSubscriptionId)
        verify { creditService.applyPlanEntitlement(20L, PlanType.PRO, "PADDLE_SUBSCRIPTION_PAID") }
    }

    // ── 기간 없는 ACTIVE 유료 구독 차단 ──────────────────────────────────────
    //
    // 운영에서 `status=ACTIVE, plan_type=BUSINESS, current_period_*=NULL,
    // next_billing_date=NULL` 인 구독이 실제로 발견됐다. 그 행은 SQL 의 NULL 비교가
    // UNKNOWN 이라 findDueForBilling·findTrialExpired 등 모든 만료·갱신 조회를 빠져나가
    // **청구되지도 만료되지도 않는 유료 권한**으로 남는다. 아래 테스트들이 이 웹훅 경로가
    // 그 상태를 다시 만들지 못하게 고정한다.

    private fun givenCreatedEventFails(rawBody: String, eventId: String) {
        val signature = createSignature()
        every { paddleGateway.verifyWebhookSignature(rawBody, signature) } returns true
        every { webhookEventRepository.findByEventId(eventId) } returns null
        every { subscriptionRepository.findByUserId(any()) } returns null
        stubPriceIds()

        assertFailsWith<IllegalStateException> {
            paddleWebhookService.handleWebhook(rawBody, signature)
        }
        verifyNothingPersisted()
    }

    @Test
    @DisplayName("subscription.created - next_billed_at 이 없으면 저장도 크레딧 지급도 하지 않는다")
    fun `created - next_billed_at 누락이면 실패한다`() {
        givenCreatedEventFails(
            buildSubscriptionCreatedPayload(eventId = "evt_no_next", nextBilledAt = null),
            "evt_no_next",
        )
    }

    @Test
    @DisplayName("subscription.created - ends_at 이 없으면 저장도 크레딧 지급도 하지 않는다")
    fun `created - ends_at 누락이면 실패한다`() {
        givenCreatedEventFails(
            buildSubscriptionCreatedPayload(eventId = "evt_no_end", endsAt = null),
            "evt_no_end",
        )
    }

    @Test
    @DisplayName("subscription.created - starts_at 이 없으면 저장도 크레딧 지급도 하지 않는다")
    fun `created - starts_at 누락이면 실패한다`() {
        givenCreatedEventFails(
            buildSubscriptionCreatedPayload(eventId = "evt_no_start", startsAt = null),
            "evt_no_start",
        )
    }

    @Test
    @DisplayName("subscription.created - current_billing_period 블록 자체가 없으면 실패한다")
    fun `created - 청구기간 블록 누락이면 실패한다`() {
        givenCreatedEventFails(
            buildSubscriptionCreatedPayload(eventId = "evt_no_period", includeBillingPeriod = false),
            "evt_no_period",
        )
    }

    /**
     * **형식 오류는 누락과 같이 다룬다.**
     *
     * `parseDateTime` 이 파싱 실패를 조용히 null 로 만들기 때문에, 이 케이스는 검증이
     * 없으면 누락과 똑같이 기간 없는 ACTIVE 행이 된다.
     */
    @Test
    @DisplayName("subscription.created - 날짜 형식이 틀리면 실패한다")
    fun `created - 날짜 형식 오류면 실패한다`() {
        givenCreatedEventFails(
            buildSubscriptionCreatedPayload(eventId = "evt_bad_date", endsAt = "not-a-timestamp"),
            "evt_bad_date",
        )
    }

    /** 정상 이벤트는 기간 세 값이 그대로 저장돼야 한다 — 차단이 과하지 않은지 본다. */
    @Test
    @DisplayName("subscription.created - 정상 이벤트는 기간 세 값을 그대로 저장한다")
    fun `created - 정상 이벤트는 기간을 저장한다`() {
        val rawBody = buildSubscriptionCreatedPayload(eventId = "evt_ok_period", userId = 30L)
        val signature = createSignature()

        every { paddleGateway.verifyWebhookSignature(rawBody, signature) } returns true
        every { webhookEventRepository.findByEventId("evt_ok_period") } returns null
        every { subscriptionRepository.findByUserId(30L) } returns null
        every { subscriptionRepository.save(any()) } answers { firstArg() }
        every { userRepository.findById(30L) } returns createUser(30L)
        every { userRepository.update(any()) } answers { firstArg() }
        stubPriceIds()

        paddleWebhookService.handleWebhook(rawBody, signature)

        val slot = slot<Subscription>()
        verify { subscriptionRepository.save(capture(slot)) }
        assertEquals(LocalDateTime.of(2024, 1, 1, 0, 0), slot.captured.currentPeriodStart)
        assertEquals(LocalDateTime.of(2024, 2, 1, 0, 0), slot.captured.currentPeriodEnd)
        assertEquals(LocalDateTime.of(2024, 2, 1, 0, 0), slot.captured.nextBillingDate)
    }

    // ── subscription.updated ─────────────────────────────────────────────────

    private fun givenUpdatedEvent(rawBody: String, eventId: String, existing: Subscription) {
        val signature = createSignature()
        every { paddleGateway.verifyWebhookSignature(rawBody, signature) } returns true
        every { webhookEventRepository.findByEventId(eventId) } returns null
        every { subscriptionRepository.findByPaddleSubscriptionId(any()) } returns existing
        every { subscriptionRepository.update(any()) } answers { firstArg() }
        every { userRepository.findById(existing.userId) } returns createUser(existing.userId)
        every { userRepository.update(any()) } answers { firstArg() }
        stubPriceIds()
    }

    /** 이벤트에도 기존 구독에도 기간이 없으면 ACTIVE 로 되돌리지 않는다. */
    @Test
    @DisplayName("subscription.updated - ACTIVE 인데 기간을 어디서도 구할 수 없으면 실패한다")
    fun `updated - ACTIVE 기간 부재면 실패한다`() {
        val rawBody = buildSubscriptionUpdatedPayload(eventId = "evt_upd_null", status = "active")
        givenUpdatedEvent(
            rawBody,
            "evt_upd_null",
            Subscription(
                id = 9L,
                userId = 40L,
                planType = PlanType.STARTER,
                status = SubscriptionStatus.ACTIVE,
                paddleSubscriptionId = "sub_paddle_123",
            ),
        )

        assertFailsWith<IllegalStateException> {
            paddleWebhookService.handleWebhook(rawBody, createSignature())
        }
        verifyNothingPersisted()
    }

    /**
     * **기존 기간이 유효하면 통과한다.**
     *
     * 갱신 이벤트가 기간을 싣지 않는 경우(플랜만 바뀌는 등)에 이미 가진 기간을 쓰는 것은
     * 종전 동작이다. 차단이 그 경로까지 막으면 정상 갱신이 전부 실패한다.
     */
    @Test
    @DisplayName("subscription.updated - 이벤트에 기간이 없어도 기존 유효 기간이 있으면 보존하며 통과한다")
    fun `updated - 기존 유효 기간을 보존한다`() {
        val existingStart = LocalDateTime.of(2026, 1, 1, 0, 0)
        val existingEnd = LocalDateTime.of(2026, 2, 1, 0, 0)
        val rawBody = buildSubscriptionUpdatedPayload(
            eventId = "evt_upd_keep", priceId = "pri_pro", status = "active",
        )
        givenUpdatedEvent(
            rawBody,
            "evt_upd_keep",
            Subscription(
                id = 9L,
                userId = 41L,
                planType = PlanType.STARTER,
                status = SubscriptionStatus.ACTIVE,
                currentPeriodStart = existingStart,
                currentPeriodEnd = existingEnd,
                nextBillingDate = existingEnd,
                paddleSubscriptionId = "sub_paddle_123",
            ),
        )

        paddleWebhookService.handleWebhook(rawBody, createSignature())

        val slot = slot<Subscription>()
        verify { subscriptionRepository.update(capture(slot)) }
        assertEquals(existingStart, slot.captured.currentPeriodStart)
        assertEquals(existingEnd, slot.captured.currentPeriodEnd)
        assertEquals(existingEnd, slot.captured.nextBillingDate)
        assertEquals(PlanType.PRO, slot.captured.planType)
        verify { creditService.applyPlanEntitlement(41L, PlanType.PRO, "PADDLE_SUBSCRIPTION_PAID") }
    }

    /**
     * **PAST_DUE 는 기간이 필수가 아니다.**
     *
     * 연체는 남은 기간을 몰라도 상태를 내리는 것이 맞다. 여기서 막으면 오히려 유료 권한이
     * 유지된다 — 차단의 의도와 정반대다.
     */
    @Test
    @DisplayName("subscription.updated - PAST_DUE 는 기간이 없어도 종전대로 처리한다")
    fun `updated - PAST_DUE 는 기간 없이도 통과한다`() {
        val rawBody = buildSubscriptionUpdatedPayload(eventId = "evt_upd_pastdue", status = "past_due")
        givenUpdatedEvent(
            rawBody,
            "evt_upd_pastdue",
            Subscription(
                id = 9L,
                userId = 42L,
                planType = PlanType.STARTER,
                status = SubscriptionStatus.ACTIVE,
                paddleSubscriptionId = "sub_paddle_123",
            ),
        )

        paddleWebhookService.handleWebhook(rawBody, createSignature())

        val slot = slot<Subscription>()
        verify { subscriptionRepository.update(capture(slot)) }
        assertEquals(SubscriptionStatus.PAST_DUE, slot.captured.status)
        verify(exactly = 0) { creditService.applyPlanEntitlement(any(), any(), any()) }
    }

    /** 해지도 같은 이유로 기간을 요구하지 않는다. */
    @Test
    @DisplayName("subscription.updated - CANCELLED 는 기간이 없어도 종전대로 처리한다")
    fun `updated - CANCELLED 는 기간 없이도 통과한다`() {
        val rawBody = buildSubscriptionUpdatedPayload(eventId = "evt_upd_cancel", status = "canceled")
        givenUpdatedEvent(
            rawBody,
            "evt_upd_cancel",
            Subscription(
                id = 9L,
                userId = 43L,
                planType = PlanType.STARTER,
                status = SubscriptionStatus.ACTIVE,
                paddleSubscriptionId = "sub_paddle_123",
            ),
        )

        paddleWebhookService.handleWebhook(rawBody, createSignature())

        val slot = slot<Subscription>()
        verify { subscriptionRepository.update(capture(slot)) }
        assertEquals(SubscriptionStatus.CANCELLED, slot.captured.status)
        verify(exactly = 0) { creditService.applyPlanEntitlement(any(), any(), any()) }
    }

    @Test
    @DisplayName("subscription.canceled 이벤트 시 구독 상태가 CANCELLED로 변경되어야 한다")
    fun `subscription canceled - 상태 변경`() {
        // given
        val rawBody = buildSubscriptionCanceledPayload(
            eventId = "evt_cancel_test",
            paddleSubId = "sub_cancel_123",
        )
        val signature = createSignature()
        val existingSub = Subscription(
            id = 10L,
            userId = 30L,
            planType = PlanType.STARTER,
            status = SubscriptionStatus.ACTIVE,
            paddleSubscriptionId = "sub_cancel_123",
        )

        every { paddleGateway.verifyWebhookSignature(rawBody, signature) } returns true
        every { webhookEventRepository.findByEventId("evt_cancel_test") } returns null
        every { subscriptionRepository.findByPaddleSubscriptionId("sub_cancel_123") } returns existingSub
        every { subscriptionRepository.update(any()) } answers { firstArg() }

        // when
        paddleWebhookService.handleWebhook(rawBody, signature)

        // then
        val subSlot = slot<Subscription>()
        verify { subscriptionRepository.update(capture(subSlot)) }
        assertEquals(SubscriptionStatus.CANCELLED, subSlot.captured.status)
    }

    @Test
    @DisplayName("transaction.completed 이벤트 시 Payment가 생성되어야 한다 (구독 결제)")
    fun `transaction completed - 구독 결제 Payment 생성`() {
        // given
        val rawBody = buildTransactionCompletedPayload(
            eventId = "evt_txn_sub",
            transactionId = "txn_sub_123",
            userId = 40L,
            subscriptionId = "sub_paddle_40",
            totalAmount = "19900",
            currencyCode = "KRW",
        )
        val signature = createSignature()

        every { paddleGateway.verifyWebhookSignature(rawBody, signature) } returns true
        every { webhookEventRepository.findByEventId("evt_txn_sub") } returns null
        every { paymentRepository.findByPaddleTransactionId("txn_sub_123") } returns null
        every { subscriptionRepository.findByPaddleSubscriptionId("sub_paddle_40") } returns null
        every { paddleGateway.getTransactionInvoice("txn_sub_123") } returns "https://paddle.com/invoice/123"
        every { paymentRepository.save(any()) } answers {
            firstArg<Payment>().copy(id = 100L)
        }

        // when
        paddleWebhookService.handleWebhook(rawBody, signature)

        // then
        val paymentSlot = slot<Payment>()
        verify { paymentRepository.save(capture(paymentSlot)) }
        assertEquals(40L, paymentSlot.captured.userId)
        assertEquals(PaymentType.SUBSCRIPTION, paymentSlot.captured.type)
        assertEquals(19900, paymentSlot.captured.amount)
        assertEquals("KRW", paymentSlot.captured.currency)
        assertEquals(PaymentStatus.COMPLETED, paymentSlot.captured.status)
        assertEquals("paddle", paymentSlot.captured.pgProvider)
        assertEquals("txn_sub_123", paymentSlot.captured.paddleTransactionId)
        assertEquals("https://paddle.com/invoice/123", paymentSlot.captured.paddleInvoiceUrl)

        // 구독 결제이므로 크레딧 추가 안 됨
        verify(exactly = 0) { creditService.addPurchasedCredits(any(), any(), any()) }
    }

    @Test
    @DisplayName("transaction.completed 구독 결제는 기존 ACTIVE 구독의 권한을 재적용해야 한다")
    fun `transaction completed - 기존 활성 구독 권한 재적용`() {
        val rawBody = buildTransactionCompletedPayload(
            eventId = "evt_txn_existing_sub",
            transactionId = "txn_existing_sub",
            userId = 41L,
            subscriptionId = "sub_paddle_41",
            totalAmount = "19900",
        )
        val subscription = Subscription(
            id = 41L,
            userId = 41L,
            planType = PlanType.PRO,
            status = SubscriptionStatus.ACTIVE,
            paddleSubscriptionId = "sub_paddle_41",
        )
        val user = createUser(41L, PlanType.FREE)

        every { paddleGateway.verifyWebhookSignature(rawBody, any()) } returns true
        every { webhookEventRepository.findByEventId("evt_txn_existing_sub") } returns null
        every { paymentRepository.findByPaddleTransactionId("txn_existing_sub") } returns null
        every { paymentRepository.save(any()) } answers { firstArg<Payment>().copy(id = 101L) }
        every { paddleGateway.getTransactionInvoice("txn_existing_sub") } returns null
        every { subscriptionRepository.findByPaddleSubscriptionId("sub_paddle_41") } returns subscription
        every { userRepository.findById(41L) } returns user
        every { userRepository.update(any()) } answers { firstArg() }

        paddleWebhookService.handleWebhook(rawBody, createSignature())

        verify { creditService.applyPlanEntitlement(41L, PlanType.PRO, "PADDLE_SUBSCRIPTION_PAID") }
        val userSlot = slot<User>()
        verify { userRepository.update(capture(userSlot)) }
        assertEquals(PlanType.PRO, userSlot.captured.planType)
    }

    @Test
    @DisplayName("transaction.completed 이벤트 시 CREDIT 유형이면 크레딧이 추가되어야 한다")
    fun `transaction completed - 크레딧 구매 시 크레딧 추가`() {
        // given
        val rawBody = buildTransactionCompletedPayload(
            eventId = "evt_txn_credit",
            transactionId = "txn_credit_123",
            userId = 50L,
            subscriptionId = null, // subscription_id 없음 → CREDIT 유형
            // 실제 크레딧 패키지 가격이어야 한다. 결제 금액으로 패키지를 역산하기 때문에
            // 아무 금액이나 넣으면 지급 대상을 찾지 못한다.
            totalAmount = "4900", // CreditPackage.STARTER (500 크레딧)
            currencyCode = "KRW",
        )
        val signature = createSignature()

        every { paddleGateway.verifyWebhookSignature(rawBody, signature) } returns true
        every { webhookEventRepository.findByEventId("evt_txn_credit") } returns null
        every { paymentRepository.findByPaddleTransactionId("txn_credit_123") } returns null
        every { paddleGateway.getTransactionInvoice("txn_credit_123") } returns "https://paddle.com/invoice/credit"
        every { paymentRepository.save(any()) } answers {
            firstArg<Payment>().copy(id = 200L)
        }
        every { creditService.addPurchasedCredits(50L, CreditPackage.STARTER, 200L) } returns Unit

        // when
        paddleWebhookService.handleWebhook(rawBody, signature)

        // then
        val paymentSlot = slot<Payment>()
        verify { paymentRepository.save(capture(paymentSlot)) }
        assertEquals(PaymentType.CREDIT, paymentSlot.captured.type)
        assertEquals("AI 크레딧 구매", paymentSlot.captured.description)

        verify { creditService.addPurchasedCredits(50L, CreditPackage.STARTER, 200L) }
    }

    /**
     * **돈만 받고 크레딧을 주지 않는 경로를 막는다.**
     *
     * 예전에는 패키지를 못 찾으면 로그만 남기고 계속 진행했다. 그러면 바로 위에서 저장한
     * `status = COMPLETED` 결제가 그대로 커밋되고, 웹훅은 200 을 돌려주니 Paddle 도
     * 재시도하지 않는다. 사용자는 돈을 냈고 결제 내역에는 완료로 찍히는데 크레딧은 0 이다.
     *
     * 할인 쿠폰·세금 변동·통화 최소단위 표기처럼 총액이 정가와 달라지는 경우는 실제로
     * 생긴다. 그때 몇 크레딧을 줄지 추측할 근거가 없으므로 실패로 남겨야 한다.
     */
    @Test
    @DisplayName("크레딧 패키지를 식별할 수 없으면 결제를 완료로 남기지 않는다")
    fun `transaction completed - 패키지 미식별 시 실패`() {
        // 정가(4,900/9,900/19,900/49,900) 어디에도 없는 금액. 쿠폰 할인 등으로 실제 발생한다.
        val rawBody = buildTransactionCompletedPayload(
            eventId = "evt_txn_unknown",
            transactionId = "txn_unknown_1",
            userId = 50L,
            subscriptionId = null,
            totalAmount = "4410",
            currencyCode = "KRW",
        )
        val signature = createSignature()

        every { paddleGateway.verifyWebhookSignature(rawBody, signature) } returns true
        every { webhookEventRepository.findByEventId("evt_txn_unknown") } returns null
        every { paymentRepository.findByPaddleTransactionId("txn_unknown_1") } returns null
        every { paddleGateway.getTransactionInvoice("txn_unknown_1") } returns "https://paddle.com/invoice/x"
        every { paymentRepository.save(any()) } answers { firstArg<Payment>().copy(id = 201L) }

        // 예외가 밖으로 나가야 트랜잭션이 롤백되고 Paddle 이 재시도한다.
        assertFailsWith<IllegalStateException> {
            paddleWebhookService.handleWebhook(rawBody, signature)
        }

        // 지급 금액을 지어내지 않는다.
        verify(exactly = 0) { creditService.addPurchasedCredits(any(), any(), any()) }
        // 실패를 재시도 대상으로 기록한다. 단, 완료된 행은 덮지 않는 조건부 갱신이어야 한다.
        verify {
            webhookEventRepository.updateIfNotProcessed(
                match { it.status == "FAILED" && it.retryCount == 1 },
            )
        }
    }

    /** 총액을 읽지 못해 0 이 된 경우도 같은 경로로 막힌다(0 원짜리 패키지는 없다). */
    @Test
    @DisplayName("총액을 읽지 못하면 크레딧을 지급하지 않는다")
    fun `transaction completed - 총액 파싱 실패 시 지급 없음`() {
        val rawBody = buildTransactionCompletedPayload(
            eventId = "evt_txn_bad_total",
            transactionId = "txn_bad_total",
            userId = 50L,
            subscriptionId = null,
            totalAmount = "not-a-number",
            currencyCode = "KRW",
        )
        val signature = createSignature()

        every { paddleGateway.verifyWebhookSignature(rawBody, signature) } returns true
        every { webhookEventRepository.findByEventId("evt_txn_bad_total") } returns null
        every { paymentRepository.findByPaddleTransactionId("txn_bad_total") } returns null
        every { paddleGateway.getTransactionInvoice("txn_bad_total") } returns "https://paddle.com/invoice/y"
        every { paymentRepository.save(any()) } answers { firstArg<Payment>().copy(id = 202L) }

        assertFailsWith<IllegalStateException> {
            paddleWebhookService.handleWebhook(rawBody, signature)
        }

        verify(exactly = 0) { creditService.addPurchasedCredits(any(), any(), any()) }
    }

    /** 구독 결제는 크레딧 대상이 아니므로 정가와 달라도 실패하지 않는다. */
    @Test
    @DisplayName("구독 결제는 금액이 정가와 달라도 실패하지 않는다")
    fun `transaction completed - 구독은 금액 역산 대상이 아니다`() {
        val rawBody = buildTransactionCompletedPayload(
            eventId = "evt_txn_sub_discount",
            transactionId = "txn_sub_discount",
            userId = 50L,
            subscriptionId = "sub_123",
            totalAmount = "8910",
            currencyCode = "KRW",
        )
        val signature = createSignature()

        every { paddleGateway.verifyWebhookSignature(rawBody, signature) } returns true
        every { webhookEventRepository.findByEventId("evt_txn_sub_discount") } returns null
        every { paymentRepository.findByPaddleTransactionId("txn_sub_discount") } returns null
        every { subscriptionRepository.findByPaddleSubscriptionId("sub_123") } returns null
        every { paddleGateway.getTransactionInvoice("txn_sub_discount") } returns "https://paddle.com/invoice/z"
        every { paymentRepository.save(any()) } answers { firstArg<Payment>().copy(id = 203L) }

        paddleWebhookService.handleWebhook(rawBody, signature)

        verify(exactly = 0) { creditService.addPurchasedCredits(any(), any(), any()) }
        // 완료 표시는 event_id 로 상태·완료시각만 바꾼다. 수신 직후 이벤트에는 id 가 없다.
        verify { webhookEventRepository.markProcessed(any(), any()) }
    }

    @Test
    @DisplayName("transaction.refunded 이벤트 시 환불 처리 및 CREDIT 유형이면 크레딧이 회수되어야 한다")
    fun `transaction refunded - 환불 및 크레딧 회수`() {
        // given
        val rawBody = buildTransactionRefundedPayload(
            eventId = "evt_refund_test",
            transactionId = "txn_refund_123",
        )
        val signature = createSignature()
        val existingPayment = Payment(
            id = 300L,
            userId = 60L,
            type = PaymentType.CREDIT,
            // 회수량은 금액이 아니라 패키지의 크레딧 수로 결정되므로 실제 패키지 가격이어야 한다.
            amount = 4_900, // CreditPackage.STARTER (500 크레딧)
            status = PaymentStatus.COMPLETED,
            paddleTransactionId = "txn_refund_123",
        )

        every { paddleGateway.verifyWebhookSignature(rawBody, signature) } returns true
        every { webhookEventRepository.findByEventId("evt_refund_test") } returns null
        every { paymentRepository.findByPaddleTransactionId("txn_refund_123") } returns existingPayment
        // 회수는 멱등하지 않으므로 결제 행을 잠그고 다시 읽은 상태로 판정한다.
        every { paymentRepository.findByIdForUpdate(300L) } returns existingPayment
        every { paymentRepository.update(any()) } answers { firstArg() }
        every { creditService.revokeCredits(60L, 500, "REFUND_txn_refund_123") } returns Unit

        // when
        paddleWebhookService.handleWebhook(rawBody, signature)

        // then
        val paymentSlot = slot<Payment>()
        verify { paymentRepository.update(capture(paymentSlot)) }
        assertEquals(PaymentStatus.REFUNDED, paymentSlot.captured.status)

        verify { creditService.revokeCredits(60L, 500, "REFUND_txn_refund_123") }
    }

    @Test
    @DisplayName("transaction.refunded 이벤트 시 SUBSCRIPTION 유형이면 크레딧 회수가 일어나지 않아야 한다")
    fun `transaction refunded - 구독 환불은 크레딧 회수 안 함`() {
        // given
        val rawBody = buildTransactionRefundedPayload(
            eventId = "evt_refund_sub",
            transactionId = "txn_refund_sub_123",
        )
        val signature = createSignature()
        val existingPayment = Payment(
            id = 400L,
            userId = 70L,
            type = PaymentType.SUBSCRIPTION,
            amount = 9900,
            status = PaymentStatus.COMPLETED,
            paddleTransactionId = "txn_refund_sub_123",
        )

        every { paddleGateway.verifyWebhookSignature(rawBody, signature) } returns true
        every { webhookEventRepository.findByEventId("evt_refund_sub") } returns null
        every { paymentRepository.findByPaddleTransactionId("txn_refund_sub_123") } returns existingPayment
        every { paymentRepository.findByIdForUpdate(400L) } returns existingPayment
        every { paymentRepository.update(any()) } answers { firstArg() }

        // when
        paddleWebhookService.handleWebhook(rawBody, signature)

        // then
        verify { paymentRepository.update(any()) }
        verify(exactly = 0) { creditService.revokeCredits(any(), any(), any()) }
    }

    @Test
    @DisplayName("타임스탬프가 5분 이상 지난 경우 UnauthorizedException이 발생해야 한다")
    fun `타임스탬프 만료 - UnauthorizedException`() {
        // given
        val rawBody = buildSubscriptionCreatedPayload()
        val expiredTimestamp = currentTimestamp() - 600 // 10분 전
        val signature = createSignature(ts = expiredTimestamp)

        every { paddleGateway.verifyWebhookSignature(rawBody, signature) } returns true

        // when & then
        assertThrows(UnauthorizedException::class.java) {
            paddleWebhookService.handleWebhook(rawBody, signature)
        }
    }

    @Test
    @DisplayName("타임스탬프가 5분 이내이면 정상 처리되어야 한다")
    fun `타임스탬프 유효 - 정상 처리`() {
        // given
        val rawBody = buildSubscriptionCreatedPayload(
            eventId = "evt_valid_ts",
            userId = 80L,
            priceId = "pri_starter",
        )
        val validTimestamp = currentTimestamp() - 60 // 1분 전
        val signature = createSignature(ts = validTimestamp)
        val user = createUser(80L)

        every { paddleGateway.verifyWebhookSignature(rawBody, signature) } returns true
        every { webhookEventRepository.findByEventId("evt_valid_ts") } returns null
        every { subscriptionRepository.findByUserId(80L) } returns null
        every { subscriptionRepository.save(any()) } answers { firstArg() }
        every { userRepository.findById(80L) } returns user
        every { userRepository.update(any()) } answers { firstArg() }
        // resolvePlanType 이 월간·연간 가격 ID 를 모두 대조하므로 결제 주기를 가리지 않고 stub 한다.
        every { paddleGateway.getPriceIdForPlan("FREE", any()) } returns null
        every { paddleGateway.getPriceIdForPlan("STARTER", any()) } returns "pri_starter"
        every { paddleGateway.getPriceIdForPlan("PRO", any()) } returns "pri_pro"
        every { paddleGateway.getPriceIdForPlan("BUSINESS", any()) } returns "pri_business"

        // when - 예외 없이 정상 처리
        paddleWebhookService.handleWebhook(rawBody, signature)

        // then
        verify { subscriptionRepository.save(any()) }
    }

    @Test
    @DisplayName("중복 트랜잭션이 감지되면 Payment를 생성하지 않아야 한다")
    fun `중복 트랜잭션 스킵`() {
        // given
        val rawBody = buildTransactionCompletedPayload(
            eventId = "evt_dup_txn",
            transactionId = "txn_dup_123",
            userId = 90L,
        )
        val signature = createSignature()
        val existingPayment = Payment(
            id = 500L,
            userId = 90L,
            type = PaymentType.SUBSCRIPTION,
            amount = 9900,
            status = PaymentStatus.COMPLETED,
            paddleTransactionId = "txn_dup_123",
        )

        every { paddleGateway.verifyWebhookSignature(rawBody, signature) } returns true
        every { webhookEventRepository.findByEventId("evt_dup_txn") } returns null
        every { paymentRepository.findByPaddleTransactionId("txn_dup_123") } returns existingPayment

        // when
        paddleWebhookService.handleWebhook(rawBody, signature)

        // then - save가 호출되지 않아야 함 (이미 처리된 트랜잭션)
        verify(exactly = 0) { paymentRepository.save(any()) }
    }
}
