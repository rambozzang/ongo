package com.ongo.application.activitylog

/**
 * 활동 로그의 안정 action 값.
 *
 * 이 값들은 운영자가 SQL 로 직접 세는 대상이라 사실상 계약이다. 바꾸면 과거 행과
 * 새 행이 다른 이름으로 나뉘어 기간을 걸친 집계가 조용히 틀린다. 새 이름을 쓸 때는
 * 기존 이름을 남겨 두거나 마이그레이션 계획이 함께 있어야 한다.
 *
 * 이름은 자기설명적으로 둔다 — 조회하는 사람이 코드를 열지 않아도 무슨 사건인지 알아야 한다.
 */
object ActivityLogActions {

    /**
     * 쇼츠 실행을 **시작도 못 했다** — 지금 잔액으로는 완주가 불가능해 생성 단계에서 거절됐다.
     *
     * 전환 퍼널의 분모다. 이 사건이 기록되지 않으면 "막힌 사람 중 몇 명이 체험을 시작했나"를
     * 계산할 모수가 없다.
     */
    const val SHORTS_RUN_BLOCKED_INSUFFICIENT_CREDIT = "SHORTS_RUN_BLOCKED_INSUFFICIENT_CREDIT"

    /** 무료 체험이 실제로 시작됐다. 구독 상태와 크레딧 권한이 모두 반영된 뒤에만 기록한다. */
    const val SUBSCRIPTION_TRIAL_STARTED = "SUBSCRIPTION_TRIAL_STARTED"

    /** 쇼츠 실행이 실제로 만들어졌다. 멱등 재사용으로 기존 실행을 돌려준 경우는 제외한다. */
    const val SHORTS_RUN_CREATED = "SHORTS_RUN_CREATED"

    /**
     * 클립에 **접근 가능한 완성 영상이 연결됐다**. 첫 가치 도달의 근사치다.
     *
     * 서버 렌더와 외부 완성본 연결(보완 경로) 둘 다 이 사건을 낸다 — 사건의 정의가
     * "서버가 렌더했다"가 아니라 "연결이 성립했다"이기 때문이다.
     *
     * **고객이 열람·다운로드했다는 뜻이 아니다.** 그 계측은 아직 없다.
     *
     * 같은 클립을 재렌더·재연결하면 이 사건이 둘 이상 생긴다. 첫 가용 시점만 필요하면
     * 사용자·실행별 `MIN(created_at)` 을 쓴다(운영 문서 참조).
     */
    const val SHORTS_CLIP_AVAILABLE = "SHORTS_CLIP_AVAILABLE"

    /**
     * 크레딧 결제가 **확정**됐다. 포트원 재조회로 상태·금액·통화를 검증하고 크레딧을
     * 지급한 뒤에만 기록한다.
     *
     * 구독 결제와 이름을 나눈 이유: 두 결제는 퍼널에서 뜻이 다르다. 크레딧 구매는 이미
     * 쓰고 있는 사용자의 추가 구매이고, 구독 결제는 유료 전환 그 자체다. 한 이름으로
     * 합치면 "체험 → 유료 전환" 비율을 크레딧 재구매가 부풀린다.
     */
    const val PAYMENT_CREDIT_COMPLETED = "PAYMENT_CREDIT_COMPLETED"

    /**
     * 구독 결제가 **확정**됐다. 퍼널의 마지막 칸이다 — 체험을 시작한 사람 중 실제로 돈을
     * 낸 사람을 세는 유일한 근거다.
     */
    const val PAYMENT_SUBSCRIPTION_COMPLETED = "PAYMENT_SUBSCRIPTION_COMPLETED"

    /** 위 사건들의 대상 종류. 조회에서 action 과 함께 좁히는 데 쓴다. */
    const val ENTITY_SHORTS_RUN = "shorts_run"
    const val ENTITY_SUBSCRIPTION = "subscription"

    /** 결제 사건의 대상. `entity_id` 는 내부 `payments.id` 다 — PG 식별자가 아니다. */
    const val ENTITY_PAYMENT = "payment"
}
