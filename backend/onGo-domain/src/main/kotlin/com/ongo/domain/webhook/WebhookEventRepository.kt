package com.ongo.domain.webhook

import java.time.LocalDateTime

interface WebhookEventRepository {
    fun save(event: WebhookEvent): WebhookEvent

    /**
     * `event_id`가 아직 없을 때만 삽입한다. **원자적 멱등 게이트**로 쓰기 위한 것이다.
     *
     * @return 삽입했으면 true, 이미 있으면 false
     *
     * `findByEventId` 후 `save`로 대신할 수 없다. 경합에서 두 트랜잭션이 모두 조회를 통과해
     * 둘 다 삽입을 시도하고, PostgreSQL에서 유니크 위반이 나면 **해당 트랜잭션 전체가 abort**되어
     * 이후 쿼리가 전부 실패한다.
     */
    fun saveIfAbsent(event: WebhookEvent): Boolean

    /**
     * 처리 성공을 기록한다. `event_id`로 갱신하므로 `saveIfAbsent` 시 id를 되받지 않아도 된다.
     *
     * `PROCESSED`로 옮기지 않으면 성공한 이력이 영구 `PENDING`으로 남는다.
     * `idx_webhook_events_status`가 `WHERE status != 'PROCESSED'` 부분 인덱스라 그대로 누적된다.
     *
     * @return 갱신된 행이 있으면 true
     */
    fun markProcessed(eventId: String, processedAt: LocalDateTime): Boolean
    fun findByEventId(eventId: String): WebhookEvent?
    fun findRetryable(now: LocalDateTime): List<WebhookEvent>
    fun update(event: WebhookEvent)
}
