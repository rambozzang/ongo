package com.ongo.application.payment

import com.ongo.common.enums.PaymentType

/**
 * 결제가 **확정**됐다는 사실. PG 재조회로 상태·금액·통화를 검증하고, 크레딧 지급 또는
 * 구독 반영까지 성공한 뒤에만 발행된다.
 *
 * ## 무엇을 담지 않는가
 *
 * 이메일·이름·PG 원문 응답·거래 식별자·영수증 URL을 담지 않는다. 이 이벤트의 소비자는
 * 활동 로그이고, 활동 로그는 운영자가 SQL 로 훑는 테이블이다. 개인정보나 결제사 원문이
 * 거기로 새면 탈퇴 시 지울 대상이 늘고, 로그를 보는 사람의 권한 범위도 달라진다.
 *
 * 담는 것은 **누가·무엇을·어떤 내부 식별자로**뿐이다. 금액조차 넣지 않는다 — 필요하면
 * `payments` 를 `paymentId` 로 조인해 읽으면 되고, 그쪽이 단일 진실이다.
 *
 * @param paymentId 내부 `payments.id`. 포트원 식별자가 아니다.
 */
data class PaymentCompletedEvent(
    val userId: Long,
    val paymentId: Long,
    val type: PaymentType,
)
