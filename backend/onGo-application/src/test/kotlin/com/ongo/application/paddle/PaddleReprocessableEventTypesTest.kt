package com.ongo.application.paddle

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.ongo.application.credit.CreditService
import com.ongo.application.subscription.DummyTransactionManagerForTest
import com.ongo.application.webhook.WebhookEventRecorder
import com.ongo.application.webhook.WebhookInboundGuard
import com.ongo.common.enums.BillingCycle
import com.ongo.common.enums.PlanType
import com.ongo.common.enums.SubscriptionStatus
import com.ongo.domain.subscription.Subscription
import com.ongo.domain.payment.PaymentRepository
import com.ongo.domain.subscription.SubscriptionRepository
import com.ongo.domain.user.UserRepository
import com.ongo.domain.webhook.WebhookEvent
import com.ongo.domain.webhook.WebhookEventRepository
import io.mockk.MockKVerificationScope
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `REPROCESSABLE_EVENT_TYPES` 가 **실제 분기와 일치하는지** 고정한다.
 *
 * 이 목록은 방치된 PENDING 을 되살릴 때의 범위다. 여기 있는데 `when` 에 분기가 없으면
 * 재처리기가 아무 일도 하지 않은 채 PROCESSED 로 찍혀 **처리된 적 없는 결제가 완료로
 * 남는다.** 목록만 보고는 알 수 없으니 타입마다 실제 호출을 확인한다.
 */
class PaddleReprocessableEventTypesTest {

    private val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())

    private class Fixture {
        val gateway = mockk<PaddleGateway>(relaxed = true)
        val subscriptionRepository = mockk<SubscriptionRepository>(relaxed = true)
        val paymentRepository = mockk<PaymentRepository>(relaxed = true)
        val webhookEventRepository = mockk<WebhookEventRepository>(relaxed = true)

        init {
            // 필수 필드 누락은 이제 예외다. 분기 **도달** 여부만 보려는 테스트이므로
            // 각 핸들러가 요구하는 최소 조건을 갖춰 둔다.
            every { gateway.getPriceIdForPlan(any(), any()) } returns "pri_x"
            every { subscriptionRepository.findByPaddleSubscriptionId(any()) } returns Subscription(
                id = 1L,
                userId = 1L,
                planType = PlanType.STARTER,
                status = SubscriptionStatus.ACTIVE,
                price = PlanType.STARTER.price,
                billingCycle = BillingCycle.MONTHLY,
                paddleSubscriptionId = "sub_1",
            )
        }

        fun service(objectMapper: ObjectMapper) = PaddleWebhookService(
            gateway,
            subscriptionRepository,
            paymentRepository,
            mockk<UserRepository>(relaxed = true),
            mockk<CreditService>(relaxed = true),
            objectMapper,
            webhookEventRepository,
            WebhookInboundGuard(
                webhookEventRepository,
                WebhookEventRecorder(webhookEventRepository, DummyTransactionManagerForTest()),
                DummyTransactionManagerForTest(),
            ),
        )
    }

    /**
     * 타입별 최소 페이로드와, 그 분기가 실행됐음을 드러내는 호출.
     *
     * `transaction.completed` 에는 `subscription_id` 를 넣어 구독 결제로 태운다. 크레딧 분기는
     * 패키지 식별을 요구하는데(fail-closed) 여기서 검증하려는 것은 분기 도달 여부뿐이다.
     *
     * `subscription.created` 에는 청구 기간과 `next_billed_at` 을 넣는다. 이 셋은 실제
     * 이벤트에 항상 있고, 없으면 핸들러가 구독 조회 **전에** fail-closed 로 끝나 이 프로브가
     * 재려는 "분기 도달"을 잴 수 없다(기간 없는 ACTIVE 유료 구독을 막는 검증이다).
     */
    private val dispatchProbes: List<Triple<String, String, MockKVerificationScope.(Fixture) -> Unit>> = listOf(
        Triple(
            "subscription.created",
            """{"data":{"id":"sub_1","customer_id":"ctm_1","custom_data":{"user_id":1},""" +
                """"current_billing_period":{"starts_at":"2024-01-01T00:00:00Z",""" +
                """"ends_at":"2024-02-01T00:00:00Z"},"next_billed_at":"2024-02-01T00:00:00Z",""" +
                """"items":[{"price":{"id":"pri_x"}}]}}""",
        ) { it.subscriptionRepository.findByUserId(1L) },
        Triple(
            "subscription.updated",
            """{"data":{"id":"sub_1","items":[{"price":{"id":"pri_x"}}]}}""",
        ) { it.subscriptionRepository.findByPaddleSubscriptionId("sub_1") },
        Triple(
            "subscription.canceled",
            """{"data":{"id":"sub_1","items":[{"price":{"id":"pri_x"}}]}}""",
        ) { it.subscriptionRepository.findByPaddleSubscriptionId("sub_1") },
        Triple(
            "subscription.past_due",
            """{"data":{"id":"sub_1","items":[{"price":{"id":"pri_x"}}]}}""",
        ) { it.subscriptionRepository.findByPaddleSubscriptionId("sub_1") },
        Triple(
            "transaction.completed",
            """{"data":{"id":"txn_1","subscription_id":"sub_1","custom_data":{"user_id":1}}}""",
        ) { it.paymentRepository.findByPaddleTransactionId("txn_1") },
        Triple(
            "transaction.payment_failed",
            """{"data":{"id":"txn_1","custom_data":{"user_id":1}}}""",
        ) { it.paymentRepository.save(any()) },
        Triple(
            "transaction.refunded",
            """{"data":{"id":"txn_1"}}""",
        ) { it.paymentRepository.findByPaddleTransactionId("txn_1") },
    )

    @Test
    @DisplayName("목록의 모든 타입이 실제 처리 분기를 탄다")
    fun everyListedTypeReachesAHandler() {
        assertEquals(
            PaddleWebhookService.REPROCESSABLE_EVENT_TYPES,
            dispatchProbes.map { it.first }.toSet(),
            "되살리기 목록과 검증 대상이 어긋났다. 새 타입을 추가했다면 여기도 확인해야 한다",
        )

        dispatchProbes.forEach { (eventType, body, expectCall) ->
            val fixture = Fixture()
            val payload = """{"event_type":"$eventType",${body.removePrefix("{")}"""

            // 분기 **도달**만 본다. 그 뒤에 fail-closed 로 터지는 것은 여기 관심사가 아니다.
            runCatching {
                fixture.service(objectMapper).reprocessWebhookEvent(
                    WebhookEvent(id = 1L, eventId = "evt_$eventType", eventType = eventType, payload = payload),
                )
            }

            verify(atLeast = 1) { expectCall(fixture) }
        }
    }

    @Test
    @DisplayName("포트원 이벤트 타입은 목록에 없다 — 같은 테이블을 공유한다")
    fun portOneTypesAreExcluded() {
        listOf("Transaction.Paid", "Transaction.Cancelled", "Transaction.PartialCancelled").forEach {
            assertTrue(
                it !in PaddleWebhookService.REPROCESSABLE_EVENT_TYPES,
                "포트원 타입 $it 이 Paddle 되살리기 목록에 있다",
            )
        }
    }
}
