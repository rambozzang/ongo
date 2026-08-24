package com.ongo.application.payment

import com.ongo.application.activitylog.ActivityLogActions
import com.ongo.application.activitylog.ActivityLogUseCase
import com.ongo.common.enums.PaymentType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 확정된 결제를 활동 로그 퍼널에 남긴다.
 *
 * ## 신뢰성 경계 — 이 클래스가 존재하는 이유
 *
 * **활동 로그는 결제를 되돌릴 수 없다.** 결제는 이미 포트원에서 승인됐고 크레딧·구독까지
 * 반영된 뒤다. 그 시점에 기록용 INSERT 가 실패했다고 결제 트랜잭션을 롤백하면, 고객은
 * 돈을 냈는데 권한을 못 받는다 — 측정 때문에 매출을 깨는 셈이다.
 *
 * 그래서 두 겹으로 끊는다.
 *
 * 1. **[TransactionPhase.AFTER_COMMIT]** — 결제 트랜잭션이 **커밋된 뒤에만** 불린다.
 *    여기서 무슨 일이 나도 결제는 이미 확정이라 되돌아가지 않는다. 롤백되면 이 리스너는
 *    아예 호출되지 않으므로, 일어나지 않은 결제가 기록되는 일도 없다.
 * 2. **[ActivityLogUseCase.logActivityIndependently]** — `REQUIRES_NEW` 로 자체 트랜잭션을
 *    열고 실패를 삼킨다. 커밋 뒤에는 참여할 바깥 트랜잭션이 없으므로 새로 여는 것이
 *    맞고, 삼키는 것은 위 1번의 보장을 코드로 한 번 더 못 박는 것이다.
 *
 * 결제 서비스 쪽에서 `logActivity` 를 직접 부르면 두 보장이 모두 사라진다. 그 호출은
 * 결제 트랜잭션 안에서 일어나 실패가 그대로 전파되고, 롤백되면 기록도 함께 사라진다.
 *
 * ## 비동기로 두지 않은 이유
 *
 * `ShortsPipelineEventListener` 는 `@Async` 를 쓰지만 그쪽은 파이프라인 전체를 돌린다.
 * 여기는 INSERT 한 번이라 스레드를 넘길 이득이 없고, 동기로 두면 테스트가 스케줄러
 * 타이밍에 기대지 않아도 된다. 지연이 문제가 되면 그때 `@Async` 를 붙이면 되고,
 * 위 신뢰성 경계는 그대로 유지된다.
 *
 * ## 스프링 없이 검증 가능하다
 *
 * 애노테이션은 **호출 시점**만 정한다. [onPaymentCompleted] 는 평범한 public 메서드라
 * 단위 테스트가 컨텍스트 없이 직접 부르면 되고, "커밋 뒤에 불린다"는 계약은 발행 쪽
 * 테스트가 따로 고정한다.
 */
@Component
class PaymentActivityListener(
    private val activityLogUseCase: ActivityLogUseCase,
) {

    private val log = LoggerFactory.getLogger(PaymentActivityListener::class.java)

    /**
     * ## 왜 여기서 한 번 더 막는가
     *
     * [ActivityLogUseCase.logActivityIndependently] 도 내부에서 실패를 삼킨다. 그런데 그것은
     * **그 클래스의 구현 세부**다. 삼킴이 사라지거나, 인자 계산·로깅처럼 그 안쪽에 닿기
     * 전에 던지는 경로가 생기면 예외가 이 콜백 밖으로 나간다.
     *
     * 커밋 뒤 콜백에서 던지면 결제 트랜잭션은 이미 커밋돼 롤백되지 않지만, 예외는 호출
     * 스택(웹훅 핸들러 또는 `complete` 응답)으로 전파된다. 웹훅이 5xx 를 받으면 포트원은
     * 재전송하고, `complete` 응답이 깨지면 결제한 사용자가 실패 화면을 본다. 둘 다
     * **이미 승인·지급이 끝난 결제**를 실패처럼 보이게 만든다.
     *
     * 그래서 경계를 협력자에게 위임하지 않고 여기서 닫는다. 이 클래스만 읽어도 "기록은
     * 결제에 영향을 줄 수 없다"가 성립해야 한다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onPaymentCompleted(event: PaymentCompletedEvent) {
        runCatching {
            activityLogUseCase.logActivityIndependently(
                userId = event.userId,
                action = actionOf(event.type),
                entityType = ActivityLogActions.ENTITY_PAYMENT,
                // 내부 payments.id 다. PG 식별자를 넣으면 로그가 결제사 데이터를 들고 있게 된다.
                entityId = event.paymentId,
            )
        }.onFailure { cause ->
            // 금액·PG 식별자는 남기지 않는다. 추적에 필요한 것은 내부 식별자뿐이다.
            log.warn(
                "결제 활동 로그 기록 실패. 결제는 이미 확정됐다. paymentId={} type={}",
                event.paymentId, event.type, cause,
            )
        }
    }

    /**
     * 결제 종류별 action.
     *
     * `when` 을 전수로 둔다(else 없음). [PaymentType] 에 값이 늘면 컴파일이 깨져서,
     * 새 결제 종류가 조용히 두 이름 중 하나로 섞여 들어가지 않는다.
     */
    private fun actionOf(type: PaymentType): String = when (type) {
        PaymentType.CREDIT -> ActivityLogActions.PAYMENT_CREDIT_COMPLETED
        PaymentType.SUBSCRIPTION -> ActivityLogActions.PAYMENT_SUBSCRIPTION_COMPLETED
    }
}
