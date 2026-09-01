package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.subscription.SubscriptionRenewalAttempt
import com.ongo.domain.subscription.SubscriptionRenewalAttemptRepository
import com.ongo.domain.subscription.SubscriptionRenewalOutcome
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.OUTCOME
import com.ongo.infrastructure.persistence.jooq.Fields.PAYMENT_ID
import com.ongo.infrastructure.persistence.jooq.Fields.PERIOD_START
import com.ongo.infrastructure.persistence.jooq.Fields.SUBSCRIPTION_ID
import com.ongo.infrastructure.persistence.jooq.Tables.SUBSCRIPTION_RENEWAL_ATTEMPTS
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

/**
 * append-only 갱신 원장.
 *
 * update/delete 를 제공하지 않는다. 고칠 수 있는 이력으로는 이중 청구를 막았다고 말할 수 없다.
 */
@Repository
class SubscriptionRenewalAttemptJooqRepository(
    private val dsl: DSLContext,
) : SubscriptionRenewalAttemptRepository {

    /**
     * `INSERT ... ON CONFLICT (subscription_id, period_start) DO NOTHING`.
     *
     * 판정자는 `uq_subscription_renewal_attempts_period` 다. 조건이 없는 일반 유니크
     * 인덱스라 컬럼만 적으면 된다 — 부분 인덱스가 아니므로 WHERE 를 붙이지 않는다.
     *
     * 조회 후 삽입이 아닌 이유: 인스턴스 둘이 동시에 같은 주기를 보면 둘 다 "아직 처리
     * 안 됨"으로 통과해 청구가 두 번 나간다. 검사와 삽입을 한 문장에 둔다.
     */
    override fun claimPeriod(attempt: SubscriptionRenewalAttempt): Long? =
        dsl.insertInto(SUBSCRIPTION_RENEWAL_ATTEMPTS)
            .set(SUBSCRIPTION_ID, attempt.subscriptionId)
            .set(PERIOD_START, attempt.periodStart)
            .set(OUTCOME, SubscriptionRenewalOutcome.ATTEMPTED.name)
            .set(CREATED_AT, attempt.createdAt)
            .onConflict(SUBSCRIPTION_ID, PERIOD_START)
            .doNothing()
            // 충돌로 0행이면 returning 도 비어 있다. 그 빈 결과가 곧 "이미 선점됨"이다.
            .returningResult(ID)
            .fetchOne()
            ?.get(ID)

    /**
     * ATTEMPTED 인 행만 채운다.
     *
     * 조건을 WHERE 에 두어, 확정된 결과를 나중에 덮어쓰는 경로가 아예 생기지 않게 한다.
     * 이 원장으로 이중 청구를 막았다고 말하려면 결과가 한 번만 확정돼야 한다.
    */
    override fun completeOutcome(attemptId: Long, outcome: SubscriptionRenewalOutcome) {
        val updated = dsl.update(SUBSCRIPTION_RENEWAL_ATTEMPTS)
            .set(OUTCOME, outcome.name)
            .where(ID.eq(attemptId))
            .and(OUTCOME.eq(SubscriptionRenewalOutcome.ATTEMPTED.name))
            .execute()
        check(updated == 1) {
            "갱신 원장 결과를 확정하지 못했습니다: attemptId=$attemptId outcome=${outcome.name} updated=$updated"
        }
    }

    /**
     * 아직 연결되지 않은 행에만 붙인다.
     *
     * 조건을 WHERE 에 두어, 이미 결제가 붙은 주기에 다른 결제를 덧씌우는 경로가 생기지
     * 않게 한다. 그런 일이 생기면 한 주기에 두 결제가 대응돼 대사가 불가능해진다.
     */
    override fun linkPayment(attemptId: Long, paymentId: Long) {
        val updated = dsl.update(SUBSCRIPTION_RENEWAL_ATTEMPTS)
            .set(PAYMENT_ID, paymentId)
            .where(ID.eq(attemptId))
            .and(PAYMENT_ID.isNull)
            .execute()
        check(updated == 1) {
            "갱신 원장에 결제 원장을 연결하지 못했습니다: attemptId=$attemptId paymentId=$paymentId updated=$updated"
        }
    }

    /**
     * NEEDS_REVIEW 에서만 나가는 조건부 갱신.
     *
     * [completeOutcome] 과 방향이 반대인 조건을 건다. 조건을 WHERE 에 두는 것이 핵심이다 —
     * 읽고 확인한 뒤 쓰면 운영자 둘이 동시에 눌렀을 때 둘 다 통과해 정산이 두 번 일어난다.
     * 여기서는 DB 가 승자를 정하고, 진 쪽은 0행을 받아 아무것도 하지 않는다.
     *
     * 0행이 예외가 아닌 이유: "이미 다른 운영자가 처리함" 은 오류가 아니라 결과다.
     */
    override fun resolveReviewOutcome(attemptId: Long, to: SubscriptionRenewalOutcome): Boolean =
        dsl.update(SUBSCRIPTION_RENEWAL_ATTEMPTS)
            .set(OUTCOME, to.name)
            .where(ID.eq(attemptId))
            .and(OUTCOME.eq(SubscriptionRenewalOutcome.NEEDS_REVIEW.name))
            .execute() == 1

    override fun findById(id: Long): SubscriptionRenewalAttempt? =
        dsl.select()
            .from(SUBSCRIPTION_RENEWAL_ATTEMPTS)
            .where(ID.eq(id))
            .fetchOne()
            ?.let(::toAttempt)

    override fun findByOutcome(
        outcome: SubscriptionRenewalOutcome,
        limit: Int,
        offset: Int,
    ): List<SubscriptionRenewalAttempt> =
        dsl.select()
            .from(SUBSCRIPTION_RENEWAL_ATTEMPTS)
            .where(OUTCOME.eq(outcome.name))
            // 오래된 건이 먼저다. 확인이 밀린 주기일수록 고객이 오래 기다렸다.
            .orderBy(CREATED_AT.asc(), ID.asc())
            .limit(limit)
            .offset(offset)
            .fetch()
            .map(::toAttempt)

    override fun countByOutcome(outcome: SubscriptionRenewalOutcome): Long =
        dsl.selectCount()
            .from(SUBSCRIPTION_RENEWAL_ATTEMPTS)
            .where(OUTCOME.eq(outcome.name))
            .fetchOne(0, Long::class.java) ?: 0L

    override fun findByPeriod(
        subscriptionId: Long,
        periodStart: LocalDateTime,
    ): SubscriptionRenewalAttempt? =
        dsl.select()
            .from(SUBSCRIPTION_RENEWAL_ATTEMPTS)
            .where(SUBSCRIPTION_ID.eq(subscriptionId))
            .and(PERIOD_START.eq(periodStart))
            .fetchOne()
            ?.let(::toAttempt)

    private fun toAttempt(record: org.jooq.Record) = SubscriptionRenewalAttempt(
        id = record.get(ID),
        subscriptionId = record.get(SUBSCRIPTION_ID),
        periodStart = record.get(PERIOD_START),
        outcome = SubscriptionRenewalOutcome.valueOf(record.get(OUTCOME)),
        paymentId = record.get(PAYMENT_ID),
        createdAt = record.get(CREATED_AT),
    )
}
