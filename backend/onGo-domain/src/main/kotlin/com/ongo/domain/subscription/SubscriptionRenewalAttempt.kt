package com.ongo.domain.subscription

import java.time.LocalDateTime

/**
 * 구독 갱신 주기 하나의 처리 기록.
 *
 * `(subscriptionId, periodStart)` 가 주기의 신원이다. 같은 쌍이 두 번 들어오면 같은 주기를
 * 다시 처리하려는 것이고, 저장소가 그것을 거절한다.
 */
data class SubscriptionRenewalAttempt(
    val id: Long = 0,
    val subscriptionId: Long,
    /** 처리한 주기의 시작 시각. 만료된 주기의 끝을 그대로 쓴다. */
    val periodStart: LocalDateTime,
    val outcome: SubscriptionRenewalOutcome,
    /**
     * 이 주기의 내부 결제 원장 id. 외부 결제 id 는 `ongo-{paymentId}` 다.
     *
     * V103 이전에 만들어진 행은 null 이다. 그 주기는 내부 원장 없이 청구됐으므로
     * **재청구하지 않고** 운영 확인 대상으로 둔다.
     */
    val paymentId: Long? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)

/** 갱신 시도의 결과. DB `ck_subscription_renewal_attempts_outcome` 과 값이 일치해야 한다. */
enum class SubscriptionRenewalOutcome {
    /**
     * 이 주기를 선점했고 아직 결과를 모른다. **행을 만들 때의 값**이다.
     *
     * 청구보다 먼저 자리를 잡아야 이중 청구를 막을 수 있으므로 결과를 알기 전에 행이
     * 먼저 생긴다. 프로세스가 청구 도중 죽으면 이 값이 남는데, 그건 "결과를 모른다"는
     * 사실 그대로다 — 성공이나 실패로 단정하지 않는다.
     */
    ATTEMPTED,

    /** 빌링키로 청구에 성공해 기간을 연장했다. */
    CHARGED,

    /**
     * 청구를 시도했으나 PG 가 거절했다. 구독은 `PAST_DUE` 로 내려간다.
     *
     * [BILLING_KEY_MISSING] 과 구분한다 — 이쪽은 고객의 결제수단 문제이고, 운영자가
     * 할 일은 고객에게 카드 확인을 안내하는 것이다.
     */
    CHARGE_FAILED,

    /**
     * 저장된 결제수단이 없어 **청구를 시도조차 못 했다.**
     *
     * 원인이 고객이 아니라 우리 쪽 미비다. 현재 결제 UI 가 빌링키를 발급하지 않아
     * 기존 구독은 전부 이 경로로 떨어진다.
     */
    BILLING_KEY_MISSING,

    /**
     * 자동으로 판정할 수 없어 **사람이 봐야 한다.**
     *
     * PG 는 결제됐다고 하는데 승인 금액이 우리가 청구한 금액과 다른 경우가 대표적이다.
     * 돈이 이미 움직였으므로 실패로 처리해 구독을 내리면 결제한 고객의 권한을 뺏는 것이고,
     * 성공으로 처리하면 틀린 금액을 매출로 잡는다. 어느 쪽도 자동으로 정할 수 없다.
     */
    NEEDS_REVIEW,
}

/**
 * 갱신 주기 원장.
 *
 * 행을 지우지 않는다. 유일하게 허용하는 갱신은 **자기가 방금 만든 행의 결과를 채우는
 * 것**([completeOutcome])이며, 그것은 이력을 고치는 것이 아니라 시작한 기록을 끝내는 것이다.
 */
interface SubscriptionRenewalAttemptRepository {
    /**
     * 이 주기를 **선점한다.** 단일 SQL 이며 결과가 아니라 자리를 잡는 것이 목적이다.
     *
     * 조회 후 삽입은 인스턴스 둘이 모두 "아직 처리 안 됨"을 보고 통과해 같은 주기를 두 번
     * 청구한다. 유니크 인덱스를 판정자로 삼아 검사와 삽입을 한 문장에 둔다.
     *
     * 결과를 모르는 채 행이 먼저 생기므로 outcome 은 [SubscriptionRenewalOutcome.ATTEMPTED]
     * 로 들어간다. 청구가 끝나면 [completeOutcome] 으로 채운다.
     *
     * @return 이번 호출이 만든 행의 id. 이미 선점된 주기면 null. **충돌은 예외가 아니다.**
     */
    fun claimPeriod(attempt: SubscriptionRenewalAttempt): Long?

    /**
     * [claimPeriod] 가 만든 행의 결과를 채운다.
     *
     * 이미 ATTEMPTED 가 아닌 행은 건드리지 않는다 — 확정된 결과를 나중에 바꾸는 경로를
     * 열면 이 원장으로 이중 청구를 막았다고 말할 수 없다.
     */
    fun completeOutcome(attemptId: Long, outcome: SubscriptionRenewalOutcome)

    /**
     * 이 주기의 선점 행을 찾는다.
     *
     * [claimPeriod] 가 null 을 돌려줬을 때 **왜** 선점하지 못했는지 가르는 데 쓴다.
     * 이미 확정된 주기인지, 아니면 앞선 실행이 결과를 못 채우고 죽어
     * [SubscriptionRenewalOutcome.ATTEMPTED] 로 남았는지에 따라 할 일이 정반대다 —
     * 전자는 건너뛰고, 후자는 **재청구가 아니라 재조회**로 결말을 짓는다.
     */
    fun findByPeriod(subscriptionId: Long, periodStart: LocalDateTime): SubscriptionRenewalAttempt?

    /** 단건 조회. 운영자 재확인이 대상 주기를 특정할 때만 쓴다. */
    fun findById(id: Long): SubscriptionRenewalAttempt?

    /**
     * 결과별 조회. [SubscriptionRenewalOutcome.NEEDS_REVIEW] 목록을 보기 위한 것이다.
     *
     * 사람이 봐야 한다고 적어 두고 볼 방법이 없으면 그 원장은 없는 것과 같다.
     */
    fun findByOutcome(
        outcome: SubscriptionRenewalOutcome,
        limit: Int,
        offset: Int,
    ): List<SubscriptionRenewalAttempt>

    fun countByOutcome(outcome: SubscriptionRenewalOutcome): Long

    /**
     * **NEEDS_REVIEW 인 행만** 다른 결과로 확정한다.
     *
     * [completeOutcome] 은 ATTEMPTED 만 채우므로 확인 대상 행에는 쓸 수 없다. 그렇다고
     * 조건 없는 갱신을 열면 확정된 결과를 나중에 덮는 경로가 생긴다. 방향을 뒤집은 조건을
     * WHERE 에 두어, 확인 대상에서 나가는 전이 **한 방향만** 허용한다.
     *
     * 예외를 던지지 않는다. 운영자 둘이 같은 건을 동시에 눌렀을 때 진 쪽은 "이미 처리됨"
     * 이지 오류가 아니다. **경쟁의 승자는 이 조건부 갱신이 정한다.**
     *
     * @return 이번 호출이 전이시켰으면 true. 이미 다른 결과면 false.
     */
    fun resolveReviewOutcome(attemptId: Long, to: SubscriptionRenewalOutcome): Boolean

    /**
     * 선점 행에 결제 원장을 연결한다. [claimPeriod] 와 **같은 트랜잭션**에서 부른다.
     *
     * 선점이 먼저이고 결제 생성이 나중인 순서를 지켜야 한다. 결제를 먼저 만들면 선점에
     * 실패했을 때 아무도 가리키지 않는 PENDING 결제가 남고, 그건 결제 내역에 유령 행으로
     * 보인다.
     */
    fun linkPayment(attemptId: Long, paymentId: Long)
}
