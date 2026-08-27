package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.subscription.SubscriptionRenewalAttempt
import com.ongo.domain.subscription.SubscriptionRenewalAttemptRepository
import com.ongo.domain.subscription.SubscriptionRenewalOutcome
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.OUTCOME
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
        dsl.update(SUBSCRIPTION_RENEWAL_ATTEMPTS)
            .set(OUTCOME, outcome.name)
            .where(ID.eq(attemptId))
            .and(OUTCOME.eq(SubscriptionRenewalOutcome.ATTEMPTED.name))
            .execute()
    }

    override fun findByPeriod(
        subscriptionId: Long,
        periodStart: LocalDateTime,
    ): SubscriptionRenewalAttempt? =
        dsl.select()
            .from(SUBSCRIPTION_RENEWAL_ATTEMPTS)
            .where(SUBSCRIPTION_ID.eq(subscriptionId))
            .and(PERIOD_START.eq(periodStart))
            .fetchOne()
            ?.let {
                SubscriptionRenewalAttempt(
                    id = it.get(ID),
                    subscriptionId = it.get(SUBSCRIPTION_ID),
                    periodStart = it.get(PERIOD_START),
                    outcome = SubscriptionRenewalOutcome.valueOf(it.get(OUTCOME)),
                    createdAt = it.get(CREATED_AT),
                )
            }
}
