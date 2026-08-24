package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.ugc.shorts.ShortsPilotActorType
import com.ongo.domain.ugc.shorts.ShortsPilotEvent
import com.ongo.domain.ugc.shorts.ShortsPilotEventRepository
import com.ongo.domain.ugc.shorts.ShortsPilotEventType
import com.ongo.infrastructure.persistence.jooq.Fields.ACTOR_ID
import com.ongo.infrastructure.persistence.jooq.Fields.AMOUNT_KRW
import com.ongo.infrastructure.persistence.jooq.Fields.ACTOR_TYPE
import com.ongo.infrastructure.persistence.jooq.Fields.ATTEMPT_NO
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.EVENT_TYPE
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.OPERATOR_MINUTES
import com.ongo.infrastructure.persistence.jooq.Fields.RUN_ID
import com.ongo.infrastructure.persistence.jooq.Tables.UGC_SHORTS_PILOT_EVENTS
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * append-only 측정 이벤트 저장소.
 *
 * update/delete 를 제공하지 않는다. 고칠 수 있는 이력으로는 사업 판단을 할 수 없고,
 * 메서드가 없으면 실수로 부를 일도 없다.
 */
@Repository
class ShortsPilotEventJooqRepository(
    private val dsl: DSLContext,
) : ShortsPilotEventRepository {

    override fun save(event: ShortsPilotEvent): ShortsPilotEvent {
        val id = dsl.insertInto(UGC_SHORTS_PILOT_EVENTS)
            .set(RUN_ID, event.runId)
            .set(EVENT_TYPE, event.eventType.name)
            .set(ACTOR_TYPE, event.actorType.name)
            .set(ACTOR_ID, event.actorId)
            .set(ATTEMPT_NO, event.attemptNo)
            .set(OPERATOR_MINUTES, event.operatorMinutes)
            .set(AMOUNT_KRW, event.amountKrw)
            .set(CREATED_AT, LocalDateTime.ofInstant(event.createdAt, ZoneOffset.UTC))
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return findById(id) ?: error("파일럿 이벤트를 저장한 뒤 조회할 수 없습니다: $id")
    }

    /**
     * `INSERT ... ON CONFLICT (run_id) WHERE event_type = 'PILOT_ENROLLED' DO NOTHING`.
     *
     * ## 왜 arbiter 에 WHERE 가 붙는가
     *
     * `uq_shorts_pilot_events_enrollment` 는 **부분** 유니크 인덱스다. PostgreSQL 은 부분
     * 인덱스를 충돌 판정자로 쓰려면 인덱스의 조건식을 그대로 다시 적어야 인식한다. 조건을
     * 빼면 `run_id` 만으로 걸 유니크 인덱스가 없어 실행 시점에 실패한다.
     *
     * ## 왜 조회 대신 이것인가
     *
     * 조회 후 삽입은 동시 요청 둘이 모두 통과한다. 여기서는 검사와 삽입이 한 문장이라
     * 두 번째 요청은 예외 없이 0행을 반환하고, 그 0 이 곧 "이미 등록됨"이다.
     */
    override fun insertEnrollmentIfAbsent(event: ShortsPilotEvent): Boolean =
        dsl.insertInto(UGC_SHORTS_PILOT_EVENTS)
            .set(RUN_ID, event.runId)
            .set(EVENT_TYPE, ShortsPilotEventType.PILOT_ENROLLED.name)
            .set(ACTOR_TYPE, event.actorType.name)
            .set(ACTOR_ID, event.actorId)
            .set(ATTEMPT_NO, event.attemptNo)
            .set(CREATED_AT, LocalDateTime.ofInstant(event.createdAt, ZoneOffset.UTC))
            .onConflict(RUN_ID)
            .where(EVENT_TYPE.eq(ShortsPilotEventType.PILOT_ENROLLED.name))
            .doNothing()
            .execute() > 0

    override fun findEnrolledRunIds(): List<Long> =
        dsl.select(RUN_ID)
            .from(UGC_SHORTS_PILOT_EVENTS)
            .where(EVENT_TYPE.eq(ShortsPilotEventType.PILOT_ENROLLED.name))
            .orderBy(RUN_ID.asc())
            .fetch(RUN_ID)

    /** 실행별로 나눠 묻지 않는다. 한 번의 IN 질의로 가져와 애플리케이션에서 묶는다. */
    override fun findByRunIds(runIds: List<Long>): List<ShortsPilotEvent> {
        if (runIds.isEmpty()) return emptyList()
        return dsl.select()
            .from(UGC_SHORTS_PILOT_EVENTS)
            .where(RUN_ID.`in`(runIds))
            .orderBy(RUN_ID.asc(), CREATED_AT.asc(), ID.asc())
            .fetch()
            .map { it.toEvent() }
    }

    override fun findByRunId(runId: Long): List<ShortsPilotEvent> =
        dsl.select()
            .from(UGC_SHORTS_PILOT_EVENTS)
            .where(RUN_ID.eq(runId))
            .orderBy(CREATED_AT.asc(), ID.asc())
            .fetch()
            .map { it.toEvent() }

    private fun findById(id: Long): ShortsPilotEvent? =
        dsl.select()
            .from(UGC_SHORTS_PILOT_EVENTS)
            .where(ID.eq(id))
            .fetchOne()
            ?.toEvent()

    private fun Record.toEvent(): ShortsPilotEvent = ShortsPilotEvent(
        id = get(ID),
        runId = get(RUN_ID),
        eventType = ShortsPilotEventType.valueOf(get(EVENT_TYPE)),
        actorType = ShortsPilotActorType.valueOf(get(ACTOR_TYPE)),
        actorId = get(ACTOR_ID),
        attemptNo = get(ATTEMPT_NO),
        operatorMinutes = get(OPERATOR_MINUTES),
        amountKrw = get(AMOUNT_KRW),
        createdAt = get(CREATED_AT).atZone(ZoneOffset.UTC).toInstant(),
    )
}
