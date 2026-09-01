package com.ongo.domain.webhook

import java.time.LocalDateTime

/**
 * `webhook_events` 접근 계약.
 *
 * **평범한 `save`/`update` 는 의도적으로 없다.**
 *
 * - 삽입은 [saveIfAbsent] 뿐이다. 검사-후-삽입은 경합에서 두 트랜잭션이 모두 조회를 통과해
 *   유니크 위반을 일으키고, PostgreSQL 에서는 그 트랜잭션 전체가 abort 된다.
 * - 상태 변경은 [markProcessed] / [updateIfNotProcessed] / [recoverStalePending] 뿐이고,
 *   전부 **대상과 조건을 SQL 에 담는다.** `id` 로 행 전체를 덮어쓰는 갱신이 있으면 낡은
 *   스냅샷이 `PROCESSED` 를 되살려 이미 반영된 결제·환불이 다시 처리된다.
 */
interface WebhookEventRepository {

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

    /**
     * 이벤트 행을 **잠근 채로** 읽는다(`SELECT ... FOR UPDATE`).
     *
     * 수신 기록이 별도 트랜잭션에서 먼저 커밋되므로, 같은 이벤트가 동시에 두 번 전달되면
     * 두 번째 요청도 `PROCESSED` 가 아닌 행을 보고 **업무 처리를 함께 시작한다.** 그러면
     * 환불 크레딧 회수처럼 멱등하지 않은 처리가 두 번 실행된다.
     *
     * 반드시 업무 트랜잭션 안에서 호출해야 한다. 트랜잭션이 없으면 잠금이 SELECT 직후
     * 풀려 아무것도 직렬화하지 못한다.
     */
    fun findByEventIdForUpdate(eventId: String): WebhookEvent?

    /**
     * `PROCESSED` 가 아닐 때만 상태를 갱신한다.
     *
     * `PROCESSED` 는 **종착 상태**다. 되돌리면 이미 반영된 업무 처리가 재시도 대상이 되어
     * 두 번 실행된다. 경합에서 진 쪽이 실패를 기록하다가 이긴 쪽의 완료 표시를 덮는 일이
     * 실제로 가능하므로, 그 판정을 DB 에 맡긴다.
     *
     * `processed_at` 은 건드리지 않는다 — 실패 기록이 완료 시각을 지우면 안 된다.
     *
     * 대상은 `id` 가 아니라 **`event_id`** 로 찾는다. `saveIfAbsent` 는 영향 행수만 돌려주고
     * id 를 되받지 않으므로 수신 직후의 이벤트에는 id 가 없다. `event_id` 는 UNIQUE 라 같은
     * 행을 정확히 가리킨다.
     *
     * @return 갱신된 행이 있으면 true
     */
    fun updateIfNotProcessed(event: WebhookEvent): Boolean

    /**
     * 재처리할 `FAILED` 이벤트를 고른다.
     *
     * @param eventTypes 호출한 재처리기가 **실제로 해석할 수 있는** 이벤트 타입.
     *
     * `webhook_events` 는 여러 PG 가 공유하는 테이블이다(Paddle 과 포트원이 `event_id` 접두사로
     * 네임스페이스만 나눠 쓴다). 상태만으로 고르면 남의 `FAILED` 행이 딸려 오고, 호출한
     * 재처리기가 그것을 자기 형식으로 파싱한다. 형식이 맞지 않아 조용히 no-op 한 뒤
     * **처리된 적 없는 결제 웹훅이 PROCESSED 로 찍힌다.** 그래서 상태가 아니라
     * **상태 + 소유권**으로 고른다.
     *
     * 빈 목록이면 아무것도 돌려주지 않는다 — 조건이 사라진 전체 조회로 무너지지 않게.
     */
    fun findRetryable(now: LocalDateTime, eventTypes: Set<String>): List<WebhookEvent>

    /**
     * 오래 방치된 `PENDING` 이벤트를 재시도 대상(`FAILED`)으로 되살린다.
     *
     * 수신 기록은 업무 트랜잭션과 분리된 별도 트랜잭션에서 먼저 커밋된다. 그래서 업무 처리
     * 도중 프로세스가 죽거나 `catch (e: Exception)` 이 못 잡는 `Error` 가 나면 `PENDING` 행만
     * 남는다. [findRetryable] 은 `status = 'FAILED'` 인 행만 고르고, 새로 삽입된 행의
     * `next_retry_at` 은 NULL 이라 그 행은 **어느 조건으로도 다시 잡히지 않는다.**
     *
     * @param olderThan 이 시각보다 먼저 만들어진 행만 대상. **처리 중인 정상 수신을 건드리지
     *   않기 위한 기준**이므로 웹훅 한 건의 처리 시간보다 충분히 커야 한다.
     * @param retryAt 되살린 행에 넣을 `next_retry_at`
     * @param eventTypes 재처리기가 실제로 다룰 수 있는 이벤트 타입의 **양성 목록**. `webhook_events`
     *   는 포트원과 같은 테이블을 공유하므로 이 목록으로 좁히지 않으면 남의 행이 Paddle
     *   재처리기로 넘어간다.
     * @param limit 한 번에 되살릴 최대 행수. 대량 적체가 한 틱에 쏟아지는 것을 막는다.
     * @return 되살린 행 수
     */
    fun recoverStalePending(
        olderThan: LocalDateTime,
        retryAt: LocalDateTime,
        eventTypes: Set<String>,
        limit: Int,
    ): Int

    /**
     * 재시도를 모두 소진해 `DEAD_LETTER` 로 끝난 이벤트를 최근 순으로 조회한다.
     *
     * 이 행들은 **아무도 다시 처리하지 않는다.** 스케줄러는 `FAILED` 만 고르므로, 운영자가
     * 보지 않으면 결제·환불 이벤트가 반영되지 않은 채 영구히 남는다.
     */
    fun findDeadLettered(limit: Int): List<WebhookEvent>

    /**
     * `DEAD_LETTER` 이벤트 한 건을 재시도 대기열로 되돌린다. **운영자의 명시적 조치다.**
     *
     * 조건을 전부 SQL 에 담아 **DB가 판정**한다. 읽고 나서 판단하면 그 사이에 상태가 바뀐
     * 행을 덮어쓴다. 다음 경우는 갱신되지 않고 `false` 가 된다.
     *
     * - 이미 `PROCESSED` 인 이벤트 — 되살리면 반영된 결제가 다시 처리된다
     * - `DEAD_LETTER` 가 아닌 상태(PENDING·FAILED) — 이미 대기열에 있거나 처리 중이다
     * - [eventTypes] 에 없는 타입 — 우리가 재처리할 수 없는 남의 이벤트다
     *
     * `retry_count` 는 `max_retries - 1` 로 맞춘다. **정확히 한 번만** 다시 시도하고, 또
     * 실패하면 곧바로 `DEAD_LETTER` 로 돌아온다. 0 으로 되돌리면 자동 재시도 한 주기가
     * 통째로 다시 돌아 외부 API 를 그만큼 더 부르고 운영자가 결과를 늦게 안다.
     *
     * @param retryAt 되돌린 행의 `next_retry_at`. 지금으로 두면 다음 스케줄러 실행이 집어간다.
     * @return 되돌렸으면 true
     */
    fun requeueDeadLettered(id: Long, retryAt: LocalDateTime, eventTypes: Set<String>): Boolean
}
