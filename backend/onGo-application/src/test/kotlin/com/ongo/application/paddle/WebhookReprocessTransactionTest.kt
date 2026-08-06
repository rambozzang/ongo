package com.ongo.application.paddle

import com.ongo.domain.webhook.WebhookEvent
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Transactional

/**
 * 웹훅 재처리가 하나의 트랜잭션으로 묶이는지 고정한다.
 *
 * 이 불변식은 코드를 읽어야만 보이고 목 기반 테스트로는 드러나지 않는다. 그런데 깨지면
 * 돈이 틀어진다. `handleTransactionRefunded` 는 결제를 REFUNDED 로 바꾼 뒤 크레딧을
 * 회수하는데, 트랜잭션이 없으면 결제만 커밋되고 회수가 실패할 수 있다. 그 상태로 재시도하면
 * 회수가 다시 실행되어 **과다 회수**가 된다.
 *
 * 그래서 애노테이션 자체를 단언한다. 나중에 누가 떼면 여기서 걸린다.
 */
class WebhookReprocessTransactionTest {

    @Test
    @DisplayName("reprocessWebhookEvent 는 @Transactional 이어야 한다 — 없으면 부분 반영 후 과다 회수")
    fun reprocessIsTransactional() {
        val method = PaddleWebhookService::class.java
            .getDeclaredMethod("reprocessWebhookEvent", WebhookEvent::class.java)

        assertNotNull(
            method.getAnnotation(Transactional::class.java),
            "reprocessWebhookEvent 에서 @Transactional 이 사라졌다. " +
                "각 쓰기가 개별 커밋되어 결제만 환불 처리되고 크레딧 회수가 누락될 수 있다",
        )
    }

    @Test
    @DisplayName("재시도 스케줄러에는 @Transactional 이 없어야 한다 — 있으면 실패 기록이 함께 롤백된다")
    fun schedulerIsNotTransactional() {
        val method = WebhookRetryScheduler::class.java.getDeclaredMethod("retryFailedWebhooks")

        // 스케줄러가 트랜잭션이면 재처리 실패 시 내부 트랜잭션이 rollback-only 로 바뀌고,
        // catch 에서 기록하는 재시도 상태(FAILED/DEAD_LETTER)까지 함께 롤백된다.
        // 그러면 실패가 영원히 기록되지 않아 같은 이벤트를 무한 재시도한다.
        assert(method.getAnnotation(Transactional::class.java) == null) {
            "retryFailedWebhooks 에 @Transactional 이 붙었다. 재시도 상태 기록이 롤백된다"
        }
    }
}
