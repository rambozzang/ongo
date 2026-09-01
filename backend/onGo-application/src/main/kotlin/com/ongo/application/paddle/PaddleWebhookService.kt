package com.ongo.application.paddle

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.ongo.application.credit.CreditService
import com.ongo.application.webhook.WebhookInboundGuard
import com.ongo.application.webhook.WebhookInboundOutcome
import com.ongo.common.enums.BillingCycle
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
import com.ongo.domain.user.UserRepository
import com.ongo.domain.webhook.WebhookEvent
import com.ongo.domain.webhook.WebhookEventRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.OffsetDateTime

@Service
class PaddleWebhookService(
    private val paddleGateway: PaddleGateway,
    private val subscriptionRepository: SubscriptionRepository,
    private val paymentRepository: PaymentRepository,
    private val userRepository: UserRepository,
    private val creditService: CreditService,
    private val objectMapper: ObjectMapper,
    private val webhookEventRepository: WebhookEventRepository,
    /** 수신 기록·행 잠금·실패 기록의 순서를 정한다. 포트원과 공유한다. */
    private val webhookInboundGuard: WebhookInboundGuard,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        /**
         * [reprocessWebhookEvent] 가 실제로 처리하는 Paddle 이벤트 타입.
         *
         * `webhook_events` 테이블은 **포트원과 공유한다** — `event_id` 를 `portone:` 접두사로
         * 네임스페이스만 나눠 쓴다. 그런데 [WebhookRetryScheduler] 는 집어든 모든 행을
         * Paddle 페이로드로 파싱한다. 포트원 행이 그리로 넘어가면 `event_type` 키가 없어
         * 조용히 no-op 한 뒤 PROCESSED 로 찍힌다. **처리된 적 없는 결제 웹훅이 완료로
         * 남는다.** 되살릴 대상을 이 목록으로 좁혀 그 경로를 막는다.
         *
         * 양성 목록이라 새 타입을 여기 빠뜨리면 복구가 **안 되는 쪽으로** 실패한다.
         * 조용히 남의 이벤트를 처리하는 것보다 낫다.
         */
        /** 종착 상태. 여기서 되돌아가면 이미 반영된 처리가 두 번 실행된다. */
        const val PROCESSED = "PROCESSED"

        val REPROCESSABLE_EVENT_TYPES: Set<String> = setOf(
            "subscription.created",
            "subscription.updated",
            "subscription.canceled",
            "subscription.past_due",
            "transaction.completed",
            "transaction.payment_failed",
            "transaction.refunded",
        )
    }

    /**
     * Paddle 웹훅을 처리한다.
     *
     * **`@Transactional` 을 붙이면 안 된다.** 업무 트랜잭션은 [WebhookInboundGuard] 가 열고
     * 닫는다. 여기에 트랜잭션을 붙이면 그 안의 `TransactionTemplate` 이 이 트랜잭션에
     * 참여해 경계가 바깥으로 나가고, `catch` 시점에 아직 행 잠금이 살아 있어 REQUIRES_NEW
     * 실패 기록이 자기 자신과 교착한다. 자세한 근거는 그쪽 KDoc 에 있다.
     */
    fun handleWebhook(rawBody: String, paddleSignature: String) {
        // 서명 검증
        if (!paddleGateway.verifyWebhookSignature(rawBody, paddleSignature)) {
            throw UnauthorizedException("Paddle 웹훅 서명 검증 실패")
        }

        // 리플레이 공격 방지: 타임스탬프 검증 (5분 허용)
        val ts = paddleSignature.split(";")
            .associate { it.split("=", limit = 2).let { p -> p[0] to p.getOrElse(1) { "" } } }["ts"]
        if (ts != null) {
            val webhookTime = try { ts.toLong() } catch (_: NumberFormatException) { 0L }
            val now = System.currentTimeMillis() / 1000
            val toleranceSeconds = 300L // 5분
            if (kotlin.math.abs(now - webhookTime) > toleranceSeconds) {
                log.warn("Paddle 웹훅 타임스탬프 만료: ts=$ts, now=$now, diff=${now - webhookTime}s")
                throw UnauthorizedException("Paddle 웹훅 타임스탬프 만료")
            }
        }

        /*
         * 서명을 통과했다는 것은 **Paddle 이 보낸 진짜 이벤트**라는 뜻이다. 그런데 우리가
         * 해석하지 못한다면 그것은 무시해도 되는 잡음이 아니라 확인해야 할 신호다.
         * 예전에는 조용히 반환해 200 을 돌려줬고, Paddle 은 전달 성공으로 보고 재시도하지
         * 않았다. 결제·구독 이벤트가 그렇게 사라지면 되찾을 방법이 없다.
         */
        /*
         * 본문을 해석하지 못하는 것은 **형식 오류**다. 같은 바이트가 다시 와도 결과가 같으므로
         * 재전송으로 풀리지 않는다 — 아래 `event_type`·`event_id`·`data` 누락과 같은 층이다.
         *
         * 감싸지 않으면 Jackson 의 예외가 그대로 컨트롤러의 마지막 `catch` 로 가서 5xx 가
         * 되고, Paddle 은 못 고칠 본문을 계속 재전송한다. 더 나쁜 것은 그 실패가 인프라
         * 장애처럼 보여, 우리가 모르는 본문 형식이 왔다는 신호가 재시도 잡음에 묻힌다는 점이다.
         *
         * **`JsonProcessingException` 만 잡는다.** 이 타입은 깨진 JSON(`JsonParseException`)과
         * 형태 불일치(`MismatchedInputException`)를 함께 덮으면서, DB·게이트웨이 실패는
         * 건드리지 않는다. 넓게 잡으면 재전송으로 복구될 실패까지 400 으로 끊는다.
         *
         * 예외 원문을 메시지에 담지 않는다. Jackson 은 파싱 실패 지점의 본문 일부를 메시지에
         * 넣는데, 그 본문에는 결제 정보가 들어 있다.
         */
        val event = try {
            objectMapper.readValue<Map<String, Any>>(rawBody)
        } catch (_: JsonProcessingException) {
            throw PaddleWebhookFormatException("Paddle 웹훅 본문을 해석할 수 없습니다")
        }
        val eventType = (event["event_type"] as? String)?.takeIf { it.isNotBlank() }
            ?: throw PaddleWebhookFormatException("Paddle 웹훅에 event_type 이 없어 처리할 수 없습니다")
        /*
         * `event_id` 는 **멱등 키 그 자체**다. 없다고 새로 만들면 안 된다.
         *
         * 예전에는 `UUID.randomUUID()` 로 채웠다. 그러면 Paddle 이 같은 이벤트를 다시 보낼
         * 때마다 매번 다른 키가 생겨 멱등 게이트를 그대로 통과한다. 결제 하나가 여러 번
         * 반영되고 크레딧도 그만큼 중복 지급된다 — 멱등 장치가 있는 채로 멱등성이 사라진다.
         *
         * 서명을 통과한 Paddle 이벤트에 `event_id` 가 없다는 것은 우리가 모르는 형식이라는
         * 뜻이므로, 지어내지 말고 실패로 남겨 재시도와 운영 확인 대상이 되게 한다.
         */
        val eventId = (event["event_id"] as? String)?.takeIf { it.isNotBlank() }
            ?: throw PaddleWebhookFormatException(
                "Paddle 웹훅에 event_id 가 없어 멱등 처리를 보장할 수 없습니다 [eventType=$eventType]",
            )
        val data = event["data"] as? Map<*, *>
            ?: throw PaddleWebhookFormatException("Paddle 웹훅에 data 가 없어 처리할 수 없습니다 [eventType=$eventType]")

        log.info("Paddle 웹훅 수신: eventType=$eventType, eventId=$eventId")

        val outcome = webhookInboundGuard.handle(eventId, eventType, rawBody) {
            dispatch(eventType, data)
        }
        if (outcome == WebhookInboundOutcome.ALREADY_PROCESSED) {
            log.info("이미 처리된 웹훅 이벤트라 건너뜁니다: eventId=$eventId")
        }
    }

    private fun dispatch(eventType: String, data: Map<*, *>) {
        when (eventType) {
            "subscription.created" -> handleSubscriptionCreated(data)
            "subscription.updated" -> handleSubscriptionUpdated(data)
            "subscription.canceled" -> handleSubscriptionCanceled(data)
            "subscription.past_due" -> handleSubscriptionPastDue(data)
            "transaction.completed" -> handleTransactionCompleted(data)
            "transaction.payment_failed" -> handleTransactionPaymentFailed(data)
            "transaction.refunded" -> handleTransactionRefunded(data)
            else -> log.info("미처리 Paddle 이벤트: $eventType")
        }
    }

    /**
     * 실패한 웹훅을 재처리한다. [WebhookRetryScheduler] 가 호출한다.
     *
     * **`@Transactional` 이 반드시 있어야 한다.** 없으면 각 쓰기가 개별 auto-commit 되어
     * 부분 반영이 남는다. 예를 들어 `handleTransactionRefunded` 는 결제를 REFUNDED 로 바꾼 뒤
     * 크레딧을 회수하는데, 회수가 실패하면 결제만 환불 처리된 채 커밋되고 크레딧은 남는다.
     * 그 상태로 재시도하면 회수가 다시 실행되어 **과다 회수**가 된다.
     *
     * 호출자인 스케줄러에는 `@Transactional` 이 없다. 그래서 여기서 예외가 나면 이 트랜잭션만
     * 롤백되고, 스케줄러의 catch 가 재시도 상태를 기록하는 것은 정상 동작한다.
     * 스케줄러에 트랜잭션을 붙이면 이 구조가 깨지므로 붙이지 말 것.
     */
    @Transactional
    fun reprocessWebhookEvent(webhookEvent: WebhookEvent) {
        /*
         * 재처리도 인바운드 재전달과 **같은 행 잠금**으로 직렬화한다. 스케줄러가 FAILED 행을
         * 집어든 사이에 Paddle 이 같은 이벤트를 다시 보내면 두 경로가 동시에 처리한다.
         * 잠금을 얻은 뒤 완료돼 있으면 여기서 끝낸다 — 호출자가 PROCESSED 로 표시하는 것은
         * 이미 참인 사실을 다시 쓰는 것뿐이라 안전하다.
         */
        val locked = webhookEventRepository.findByEventIdForUpdate(webhookEvent.eventId)
        if (locked != null && locked.status == PROCESSED) {
            log.info("재처리 전에 이미 완료된 웹훅입니다: eventId=${webhookEvent.eventId}")
            return
        }

        /*
         * 해석하지 못하는 페이로드는 **조용히 넘기지 않는다.** 여기서 반환하면 호출자인
         * 스케줄러가 성공으로 보고 PROCESSED 로 찍는다. 처리된 적 없는 결제·구독 이벤트가
         * 완료로 남고 재시도 대상에서도 빠져 영영 반영되지 않는다.
         */
        val event = objectMapper.readValue<Map<String, Any>>(webhookEvent.payload)
        val eventType = (event["event_type"] as? String)?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("재처리할 웹훅 페이로드에 event_type 이 없습니다 [eventId=${webhookEvent.eventId}]")
        /*
         * **소유권 판정은 `event_type` 컬럼으로, 분기는 페이로드로 한다.** 둘이 어긋나면
         * Paddle 소유로 분류돼 뽑힌 행이 실제로는 다른 타입으로 처리된다. 우리가 쓰는 두
         * 경로(`recordReceived`)는 같은 페이로드에서 컬럼을 채우므로 정상이라면 항상 같다.
         * 다르다면 손으로 고쳤거나 다른 writer 가 생긴 것이니 처리하지 말고 드러내야 한다.
         */
        if (eventType != webhookEvent.eventType) {
            throw IllegalStateException(
                "저장된 event_type 과 페이로드가 다릅니다 " +
                    "[eventId=${webhookEvent.eventId}, 컬럼=${webhookEvent.eventType}, 페이로드=$eventType]",
            )
        }
        val data = event["data"] as? Map<*, *>
            ?: throw IllegalStateException("재처리할 웹훅 페이로드에 data 가 없습니다 [eventId=${webhookEvent.eventId}]")
        when (eventType) {
            "subscription.created" -> handleSubscriptionCreated(data)
            "subscription.updated" -> handleSubscriptionUpdated(data)
            "subscription.canceled" -> handleSubscriptionCanceled(data)
            "subscription.past_due" -> handleSubscriptionPastDue(data)
            "transaction.completed" -> handleTransactionCompleted(data)
            "transaction.payment_failed" -> handleTransactionPaymentFailed(data)
            "transaction.refunded" -> handleTransactionRefunded(data)
        }
    }

    /**
     * 필수 필드가 없으면 **예외로 끝낸다.** 조용히 반환하면 이벤트가 PROCESSED 로 찍혀,
     * 요금제 결제가 끝난 사용자의 구독이 어디에도 기록되지 않은 채 사라진다.
     */
    private fun handleSubscriptionCreated(data: Map<*, *>) {
        val paddleSubId = data["id"] as? String
            ?: throw IllegalStateException("구독 생성 이벤트에 구독 식별자가 없습니다")
        val customerId = data["customer_id"] as? String
            ?: throw IllegalStateException("구독 생성 이벤트에 고객 식별자가 없습니다 [paddleSubId=$paddleSubId]")
        val customData = data["custom_data"] as? Map<*, *>
        val userId = (customData?.get("user_id") as? Number)?.toLong()
            ?: throw IllegalStateException("구독 생성 이벤트에 user_id 가 없습니다 [paddleSubId=$paddleSubId]")

        log.info("Paddle 구독 생성: paddleSubId=$paddleSubId, userId=$userId")

        val planType = resolvePlanType(data)
        val billingPeriod = data["current_billing_period"] as? Map<*, *>
        /*
         * **기간 없이 ACTIVE 유료 구독을 만들지 않는다.**
         *
         * 아래는 status 를 ACTIVE 로 고정해 저장한다. 그런데 기간이 비면 그 행은 어떤
         * 만료·갱신 조회에도 걸리지 않는다 — SQL 에서 NULL 비교는 참이 아니라 UNKNOWN 이라
         * findDueForBilling(`next_billing_date <= now`) 도 findTrialExpired(`trial_end <= now`)
         * 도 그 행을 영원히 건너뛴다. 결과는 **청구되지도 만료되지도 않는 무기한 유료 권한**
         * 이고, 그 사이 findByPlanType 은 그 행을 잡으므로 유료 스케줄러 작업은 계속 나간다.
         *
         * 그래서 세 값을 필수로 읽는다. 하나라도 없으면 여기서 끝내 저장도 크레딧 지급도
         * 일어나지 않게 한다. WebhookInboundGuard 가 실패로 기록해 재시도·운영 확인 대상으로
         * 남기므로 이벤트가 사라지지는 않는다.
         */
        val periodStart = requireEventDateTime(
            billingPeriod?.get("starts_at") as? String, "current_billing_period.starts_at", "구독 생성", paddleSubId,
        )
        val periodEnd = requireEventDateTime(
            billingPeriod?.get("ends_at") as? String, "current_billing_period.ends_at", "구독 생성", paddleSubId,
        )
        val nextBilledAt = requireEventDateTime(
            data["next_billed_at"] as? String, "next_billed_at", "구독 생성", paddleSubId,
        )

        // 기존 구독 업데이트
        val existing = subscriptionRepository.findByUserId(userId)
        if (existing != null) {
            subscriptionRepository.update(existing.copy(
                planType = planType,
                status = SubscriptionStatus.ACTIVE,
                price = planType.price,
                billingCycle = BillingCycle.MONTHLY,
                currentPeriodStart = periodStart,
                currentPeriodEnd = periodEnd,
                nextBillingDate = nextBilledAt,
                paddleSubscriptionId = paddleSubId,
                paddleCustomerId = customerId,
                pendingPlanType = null,
                pendingBillingCycle = null,
                updatedAt = LocalDateTime.now(),
            ))
        } else {
            subscriptionRepository.save(Subscription(
                userId = userId,
                planType = planType,
                status = SubscriptionStatus.ACTIVE,
                price = planType.price,
                billingCycle = BillingCycle.MONTHLY,
                currentPeriodStart = periodStart,
                currentPeriodEnd = periodEnd,
                nextBillingDate = nextBilledAt,
                paddleSubscriptionId = paddleSubId,
                paddleCustomerId = customerId,
            ))
        }

        // 사용자 planType 업데이트
        val user = userRepository.findById(userId)
        if (user != null) {
            userRepository.update(user.copy(planType = planType, paddleCustomerId = customerId))
        }

        // Subscription state and AI entitlement must move together. Without
        // this, Paddle customers received the PRO/STARTER badge while their
        // ai_credits row stayed at the FREE allowance.
        creditService.applyPlanEntitlement(userId, planType, "PADDLE_SUBSCRIPTION_PAID")
    }

    private fun handleSubscriptionUpdated(data: Map<*, *>) {
        val paddleSubId = requirePaddleSubscriptionId(data, "구독 업데이트")
        log.info("Paddle 구독 업데이트: paddleSubId=$paddleSubId")

        val subscription = requireSubscription(paddleSubId, "구독 업데이트")

        val newPlanType = resolvePlanType(data)
        val status = data["status"] as? String
        val billingPeriod = data["current_billing_period"] as? Map<*, *>
        val periodStart = parseDateTime(billingPeriod?.get("starts_at") as? String)
        val periodEnd = parseDateTime(billingPeriod?.get("ends_at") as? String)
        val nextBilledAt = parseDateTime(data["next_billed_at"] as? String)

        val newStatus = when (status) {
            "active" -> SubscriptionStatus.ACTIVE
            "past_due" -> SubscriptionStatus.PAST_DUE
            "canceled" -> SubscriptionStatus.CANCELLED
            else -> subscription.status
        }

        /*
         * 들어온 값이 없으면 기존 값을 쓴다. **종전과 같은 규칙이다** — 갱신 이벤트가 기간을
         * 싣지 않는 경우(플랜만 바뀌는 등)에 이미 가지고 있던 기간을 지우지 않기 위한 것이다.
         */
        val effectiveStart = periodStart ?: subscription.currentPeriodStart
        val effectiveEnd = periodEnd ?: subscription.currentPeriodEnd
        val effectiveNextBilling = nextBilledAt ?: subscription.nextBillingDate

        /*
         * ACTIVE 로 **전환되거나 유지되는** 유료 구독은 기간이 반드시 있어야 한다.
         * 없으면 handleSubscriptionCreated 와 같은 이유로 영원히 청구·만료되지 않는다.
         * 기존 값으로도 채워지지 않는다는 것은 이 구독이 이미 그 상태이거나 그렇게 된다는
         * 뜻이므로, 조용히 다시 저장하지 않고 실패로 남겨 운영 확인 대상이 되게 한다.
         *
         * PAST_DUE·CANCELLED 는 기간이 필수가 아니다. 연체·해지는 남은 기간을 모르더라도
         * 상태를 내리는 것이 맞고, 여기서 막으면 오히려 유료 권한이 유지된다.
         */
        if (newStatus == SubscriptionStatus.ACTIVE && newPlanType != PlanType.FREE) {
            requireActiveBillingPeriod(
                what = "구독 업데이트",
                paddleSubId = paddleSubId,
                periodStart = effectiveStart,
                periodEnd = effectiveEnd,
                nextBillingDate = effectiveNextBilling,
            )
        }

        subscriptionRepository.update(subscription.copy(
            planType = newPlanType,
            status = newStatus,
            price = newPlanType.price,
            currentPeriodStart = effectiveStart,
            currentPeriodEnd = effectiveEnd,
            nextBillingDate = effectiveNextBilling,
            pendingPlanType = null,
            pendingBillingCycle = null,
            updatedAt = LocalDateTime.now(),
        ))

        // 사용자 planType 동기화
        val user = userRepository.findById(subscription.userId)
        if (user != null && newStatus == SubscriptionStatus.ACTIVE) {
            userRepository.update(user.copy(planType = newPlanType))
        }
        if (newStatus == SubscriptionStatus.ACTIVE) {
            creditService.applyPlanEntitlement(subscription.userId, newPlanType, "PADDLE_SUBSCRIPTION_PAID")
        }
    }

    private fun handleSubscriptionCanceled(data: Map<*, *>) {
        val paddleSubId = requirePaddleSubscriptionId(data, "구독 취소")
        log.info("Paddle 구독 취소: paddleSubId=$paddleSubId")

        val subscription = requireSubscription(paddleSubId, "구독 취소")

        subscriptionRepository.update(subscription.copy(
            status = SubscriptionStatus.CANCELLED,
            cancelledAt = LocalDateTime.now(),
            pendingPlanType = null,
            pendingBillingCycle = null,
            updatedAt = LocalDateTime.now(),
        ))
    }

    private fun handleSubscriptionPastDue(data: Map<*, *>) {
        val paddleSubId = requirePaddleSubscriptionId(data, "구독 연체")
        log.info("Paddle 구독 연체: paddleSubId=$paddleSubId")

        val subscription = requireSubscription(paddleSubId, "구독 연체")

        subscriptionRepository.update(subscription.copy(
            status = SubscriptionStatus.PAST_DUE,
            updatedAt = LocalDateTime.now(),
        ))
    }

    private fun handleTransactionCompleted(data: Map<*, *>) {
        val transactionId = data["id"] as? String
            ?: throw IllegalStateException("결제 완료 이벤트에 트랜잭션 식별자가 없어 처리할 수 없습니다")
        val customData = data["custom_data"] as? Map<*, *>
        val userId = (customData?.get("user_id") as? Number)?.toLong()

        log.info("Paddle 트랜잭션 완료: transactionId=$transactionId, userId=$userId")

        /*
         * **사용자를 특정하지 못하면 실패로 끝낸다.** 예전에는 경고만 남기고 반환했는데,
         * 그러면 이벤트가 PROCESSED 로 찍힌다. 사용자는 돈을 냈는데 payments 기록도,
         * 구독/크레딧 반영도 없이 아무도 모르게 끝난다. Paddle 도 200 을 받았으니
         * 재시도하지 않는다.
         */
        if (userId == null) {
            throw IllegalStateException(
                "결제 완료 이벤트에 user_id 가 없어 반영할 수 없습니다 [transactionId=$transactionId]",
            )
        }

        // 중복 처리 방지
        if (paymentRepository.findByPaddleTransactionId(transactionId) != null) {
            log.info("이미 처리된 트랜잭션: $transactionId")
            return
        }

        val details = data["details"] as? Map<*, *>
        val totals = details?.get("totals") as? Map<*, *>
        val totalAmount = (totals?.get("total") as? String)?.toIntOrNull() ?: 0

        // 인보이스 URL 조회
        val invoiceUrl = paddleGateway.getTransactionInvoice(transactionId)

        // 결제 유형 판별 (구독 vs 크레딧)
        val subscriptionId = data["subscription_id"] as? String
        val paymentType = if (subscriptionId != null) PaymentType.SUBSCRIPTION else PaymentType.CREDIT

        // Payment 기록 저장
        val payment = paymentRepository.save(Payment(
            userId = userId,
            type = paymentType,
            amount = totalAmount,
            currency = (totals?.get("currency_code") as? String) ?: "KRW",
            status = PaymentStatus.COMPLETED,
            pgProvider = "paddle",
            pgTransactionId = transactionId,
            paymentMethod = resolvePaymentMethod(data),
            receiptUrl = invoiceUrl,
            paddleTransactionId = transactionId,
            paddleInvoiceUrl = invoiceUrl,
            description = if (paymentType == PaymentType.CREDIT) "AI 크레딧 구매" else "구독 결제",
        ))

        /*
         * 크레딧 구매인 경우 크레딧 지급.
         *
         * 결제 금액을 크레딧 수로 넘기던 버그가 있었다(₩49,900 결제 -> 49,900 크레딧 시도).
         * payments 에 패키지 식별자가 없으므로 결제 금액으로 패키지를 역산한다
         * (CreditPackage 의 price 는 서로 겹치지 않는다).
         *
         * **패키지를 못 찾으면 예외로 끝낸다.** 예전에는 로그만 남기고 계속 진행했는데,
         * 그러면 바로 위에서 저장한 `status = COMPLETED` 결제가 그대로 커밋된다. 사용자는
         * 돈을 냈고 결제 내역에는 완료로 찍히는데 크레딧은 0 이다. 웹훅은 200 을 돌려주니
         * Paddle 도 재시도하지 않아 아무도 그 사실을 모른다.
         *
         * 할인 쿠폰·세금 변동·통화 최소단위 표기처럼 총액이 정가와 달라지는 경우는 실제로
         * 생긴다. 그때 몇 크레딧을 줘야 하는지 추측할 근거가 없으므로 **지급 금액을 지어내지
         * 않고** 실패로 남긴다. 바깥 [handleWebhook] 이 이 예외를 잡아 웹훅 이벤트를 FAILED
         * 로 표시하고 다시 던지므로, 트랜잭션이 롤백돼 "크레딧 없는 완료 결제" 가 남지
         * 않고 Paddle 재시도와 운영 확인 대상이 된다.
         *
         * PortOne 쪽 같은 자리(`PortOnePaymentService.completeCredit`)도 패키지를 식별하지
         * 못하면 예외를 던진다. 두 경로의 계약을 같게 유지한다.
         */
        val paymentId = payment.id
        if (paymentType == PaymentType.CREDIT) {
            checkNotNull(paymentId) {
                "결제 기록이 저장되지 않아 크레딧을 지급할 수 없습니다 [transactionId=$transactionId]"
            }
            val creditPackage = CreditPackage.entries.find { it.price == totalAmount }
                ?: throw IllegalStateException(
                    "크레딧 패키지를 식별할 수 없어 지급할 수 없습니다 " +
                        "[transactionId=$transactionId, paymentId=$paymentId, amount=$totalAmount]",
                )
            creditService.addPurchasedCredits(userId, creditPackage, paymentId)
        } else {
            /*
             * Paddle 은 구독 결제 완료와 구독 상태 이벤트를 별도로 보낼 수 있다. 기존에는
             * 여기서 결제 원장만 만들고 권한 반영을 `subscription.created`/`updated`에
             * 전적으로 맡겼다. 그 이벤트가 지연되거나 데드레터가 되면 돈을 받은
             * COMPLETED 결제만 남고 유료 권한은 영구히 복구되지 않는다.
             *
             * 구독 이벤트가 아직 오지 않은 첫 결제에는 구독을 추측해 만들지 않는다. 이미
             * 연결된 ACTIVE 구독만 소유자를 확인한 뒤 entitlement를 멱등 재적용한다. 기간과
             * 상태를 이 이벤트만으로 추측하지 않는 이유는 Paddle의 정본이 구독 이벤트이기
             * 때문이다.
             */
            synchronizeSubscriptionEntitlement(subscriptionId, userId)
        }
    }

    private fun synchronizeSubscriptionEntitlement(
        paddleSubscriptionId: String?,
        paymentUserId: Long,
    ) {
        if (paddleSubscriptionId == null) return

        val subscription = subscriptionRepository.findByPaddleSubscriptionId(paddleSubscriptionId)
            ?: return
        if (subscription.userId != paymentUserId) {
            throw IllegalStateException(
                "구독 결제의 사용자와 저장된 구독 소유자가 다릅니다 " +
                    "[transactionUserId=$paymentUserId, subscriptionUserId=${subscription.userId}, " +
                    "paddleSubscriptionId=$paddleSubscriptionId]",
            )
        }
        if (subscription.status != SubscriptionStatus.ACTIVE) {
            log.warn(
                "활성 구독이 아닌 Paddle 결제라 권한을 재적용하지 않습니다 [paddleSubscriptionId={}, status={}]",
                paddleSubscriptionId,
                subscription.status,
            )
            return
        }

        creditService.applyPlanEntitlement(
            subscription.userId,
            subscription.planType,
            "PADDLE_SUBSCRIPTION_PAID",
        )
        val user = userRepository.findById(subscription.userId)
        if (user != null && user.planType != subscription.planType) {
            userRepository.update(user.copy(planType = subscription.planType))
        }
    }

    private fun handleTransactionPaymentFailed(data: Map<*, *>) {
        val transactionId = data["id"] as? String ?: return
        val customData = data["custom_data"] as? Map<*, *>
        val userId = (customData?.get("user_id") as? Number)?.toLong() ?: return

        log.warn("Paddle 결제 실패: transactionId=$transactionId, userId=$userId")

        paymentRepository.save(Payment(
            userId = userId,
            type = PaymentType.SUBSCRIPTION,
            amount = 0,
            status = PaymentStatus.FAILED,
            pgProvider = "paddle",
            pgTransactionId = transactionId,
            paddleTransactionId = transactionId,
            description = "결제 실패",
        ))
    }

    private fun handleTransactionRefunded(data: Map<*, *>) {
        val transactionId = data["id"] as? String
            ?: throw IllegalStateException("환불 이벤트에 트랜잭션 식별자가 없어 처리할 수 없습니다")
        log.info("Paddle 환불 처리: transactionId=$transactionId")

        /*
         * 대상 결제가 없으면 **조용히 넘기지 않는다.** 예전에는 로그만 남기고 끝냈는데,
         * 그러면 이벤트가 PROCESSED 로 찍혀 환불이 우리 장부에 영영 반영되지 않는다.
         * 결제는 있는데 우리가 못 찾는 상황(식별자 불일치·기록 누락)은 사람이 봐야 한다.
         */
        val found = paymentRepository.findByPaddleTransactionId(transactionId)
            ?: throw IllegalStateException("환불 대상 결제를 찾을 수 없습니다 [transactionId=$transactionId]")
        val paymentId = checkNotNull(found.id) {
            "결제 식별자가 없어 환불을 반영할 수 없습니다 [transactionId=$transactionId]"
        }

        /*
         * **결제 행을 잠그고 다시 읽는다.**
         *
         * 크레딧 회수는 멱등하지 않다 — 부를 때마다 그만큼 더 깎는다. 그런데 같은 결제에
         * 대한 환불 통지가 서로 다른 `event_id` 로 두 번 올 수 있고(웹훅 이벤트 단위 멱등
         * 게이트는 이것을 막지 못한다), 동시에 들어오면 둘 다 COMPLETED 를 읽는다.
         * 잠금 뒤 상태로 판정해야 회수가 한 번만 일어난다.
         */
        val payment = paymentRepository.findByIdForUpdate(paymentId)
            ?: throw IllegalStateException("환불 대상 결제를 잠글 수 없습니다 [paymentId=$paymentId]")
        if (payment.status == PaymentStatus.REFUNDED) {
            log.info("이미 환불 처리된 결제라 건너뜁니다 [paymentId=$paymentId, transactionId=$transactionId]")
            return
        }

        paymentRepository.update(payment.copy(status = PaymentStatus.REFUNDED))

        // 회수량은 결제 금액이 아니라 크레딧 수다. 금액을 그대로 넘기면
        // (₩4,900 -> 4900 크레딧) 실제 지급량보다 훨씬 많이 회수된다.
        if (payment.type == PaymentType.CREDIT) {
            /*
             * **패키지를 못 찾으면 예외로 끝낸다 — 위의 REFUNDED 커밋도 함께 롤백된다.**
             *
             * 예전에는 로그만 남기고 넘어갔다. 그러면 결제는 환불로 찍히는데 지급했던
             * 크레딧은 그대로 남아, 사용자가 돈을 돌려받고 크레딧도 계속 쓴다. 웹훅은
             * PROCESSED 가 되니 아무도 그 사실을 모른다.
             *
             * 할인·부분환불·통화 표기 때문에 금액이 정가와 달라지는 경우는 실제로 생긴다.
             * 그때 몇 크레딧을 회수해야 하는지 추측할 근거가 없으므로 **회수량을 지어내지
             * 않고** 실패로 남겨 사람이 확인하게 한다. 지급 쪽(`handleTransactionCompleted`)
             * 과 같은 계약이다.
             */
            val creditPackage = CreditPackage.entries.find { it.price == payment.amount }
                ?: throw IllegalStateException(
                    "크레딧 패키지를 식별할 수 없어 회수량을 정할 수 없습니다 " +
                        "[transactionId=$transactionId, paymentId=$paymentId, amount=${payment.amount}]",
                )
            creditService.revokeCredits(payment.userId, creditPackage.credits, "REFUND_$transactionId")
        }
    }

    /**
     * 구독 식별자를 꺼낸다. 없으면 어떤 구독인지 알 수 없으므로 **실패로 남긴다.**
     *
     * 조용히 반환하면 이벤트가 PROCESSED 로 찍혀 구독 상태 변화(요금제 변경·취소·연체)가
     * 영영 반영되지 않는다. 우리 DB 와 Paddle 이 어긋난 채로 굳는다.
     */
    private fun requirePaddleSubscriptionId(data: Map<*, *>, what: String): String =
        data["id"] as? String
            ?: throw IllegalStateException("$what 이벤트에 구독 식별자가 없습니다")

    /**
     * 이벤트에서 **반드시 있어야 하는** 시각을 읽는다. 없거나 형식이 틀리면 실패로 끝낸다.
     *
     * [parseDateTime] 은 누락·형식오류를 조용히 null 로 만든다. 그 동작 자체는 선택적
     * 필드에 필요하므로 **그대로 둔다** — 필수 필드만 이 함수를 거친다.
     *
     * 조용한 null 이 위험한 이유는 그 값이 그대로 저장되기 때문이다. 기간이 빈 ACTIVE
     * 유료 구독은 SQL 의 NULL 비교가 UNKNOWN 이라 모든 만료·갱신 조회를 빠져나가고,
     * 청구되지도 만료되지도 않는 유료 권한으로 영구히 남는다.
     *
     * 메시지에는 **필드 이름과 구독 식별자만** 남긴다. 값이나 원문을 실으면 웹훅 본문이
     * 로그로 샌다.
     */
    private fun requireEventDateTime(
        raw: String?,
        field: String,
        what: String,
        paddleSubId: String,
    ): LocalDateTime =
        parseDateTime(raw)
            ?: throw IllegalStateException(
                "$what 이벤트의 $field 가 없거나 형식이 올바르지 않습니다 [paddleSubId=$paddleSubId]",
            )

    /**
     * ACTIVE 유료 구독에 필요한 기간 세 값이 모두 확보됐는지 확인한다.
     *
     * 들어온 값과 기존 값 중 무엇으로 채워졌는지는 묻지 않는다 — 저장 후에 남을 값만 본다.
     * 빠진 필드를 **전부** 모아 알린다. 하나씩 고쳐 재시도하는 왕복을 줄이기 위한 것이다.
     */
    private fun requireActiveBillingPeriod(
        what: String,
        paddleSubId: String,
        periodStart: LocalDateTime?,
        periodEnd: LocalDateTime?,
        nextBillingDate: LocalDateTime?,
    ) {
        val missing = buildList {
            if (periodStart == null) add("current_billing_period.starts_at")
            if (periodEnd == null) add("current_billing_period.ends_at")
            if (nextBillingDate == null) add("next_billed_at")
        }
        if (missing.isNotEmpty()) {
            throw IllegalStateException(
                "$what 이벤트로 기간 없는 ACTIVE 유료 구독을 만들 수 없습니다. " +
                    "이벤트와 기존 구독 어디에도 없는 값: ${missing.joinToString()} " +
                    "[paddleSubId=$paddleSubId]",
            )
        }
    }

    /**
     * 대상 구독을 찾는다. 없으면 **실패로 남긴다.**
     *
     * 우리가 모르는 구독의 상태 변경은 추측으로 넘길 일이 아니다. 재시도해도 계속 없으면
     * DEAD_LETTER 로 남아 운영자가 확인하게 된다 — 조용히 사라지는 것보다 낫다.
     */
    private fun requireSubscription(paddleSubId: String, what: String): Subscription =
        subscriptionRepository.findByPaddleSubscriptionId(paddleSubId)
            ?: throw IllegalStateException("$what 대상 구독을 찾을 수 없습니다 [paddleSubId=$paddleSubId]")

    /**
     * 구독 이벤트에서 요금제를 판별한다.
     *
     * **판별하지 못하면 예외로 끝낸다.** 예전에는 FREE 를 돌려줬는데, 그러면 결제가 성공한
     * 구독자가 조용히 무료 요금제로 강등된다 — 돈은 받고 권한은 회수하는 최악의 조합이다.
     * Paddle 에 새 price id 가 생겼을 때 전체 구독자가 한꺼번에 그렇게 될 수 있다.
     * 실패로 남겨 재시도와 운영 확인 대상이 되게 한다.
     */
    private fun resolvePlanType(data: Map<*, *>): PlanType {
        val items = data["items"] as? List<*>
            ?: throw IllegalStateException("구독 이벤트에 items 가 없어 요금제를 판별할 수 없습니다")
        val firstItem = items.firstOrNull() as? Map<*, *>
            ?: throw IllegalStateException("구독 이벤트의 items 가 비어 있어 요금제를 판별할 수 없습니다")
        val price = firstItem["price"] as? Map<*, *>
        val priceId = price?.get("id") as? String
            ?: throw IllegalStateException("구독 이벤트에 price id 가 없어 요금제를 판별할 수 없습니다")

        // 월간·연간 가격 ID 를 모두 대조해야 한다. 예전에는 기본값(MONTHLY)만 비교해서
        // 연간 구독으로 결제한 사용자의 price id 가 어디에도 걸리지 않고 else 로 떨어졌고,
        // 결제가 성공했는데도 플랜이 FREE 로 강등됐다.
        val planType = PlanType.entries.firstOrNull { plan ->
            priceId == paddleGateway.getPriceIdForPlan(plan.name, "MONTHLY") ||
                priceId == paddleGateway.getPriceIdForPlan(plan.name, "YEARLY")
        }
        return planType
            ?: throw IllegalStateException(
                "알 수 없는 Paddle price id 라 요금제를 판별할 수 없습니다 [priceId=$priceId]",
            )
    }

    private fun resolvePaymentMethod(data: Map<*, *>): String? {
        val payments = data["payments"] as? List<*> ?: return null
        val firstPayment = payments.firstOrNull() as? Map<*, *> ?: return null
        val methodDetails = firstPayment["method_details"] as? Map<*, *>
        return methodDetails?.get("type") as? String
    }

    private fun parseDateTime(dateStr: String?): LocalDateTime? {
        if (dateStr.isNullOrBlank()) return null
        return try {
            OffsetDateTime.parse(dateStr).toLocalDateTime()
        } catch (_: Exception) {
            null
        }
    }
}
