package com.ongo.application.portone

/**
 * 포트원 웹훅 요청 자체가 잘못되어 **재전송해도 결과가 달라지지 않는** 경우.
 *
 * 이 예외만 400으로 응답한다. 결제 상태 미반영·금액 불일치·게이트웨이 장애처럼
 * 재전송으로 복구될 수 있는 실패를 400으로 돌려주면 포트원이 재전송을 멈춰
 * 결제와 크레딧 지급이 영구 누락된다.
 *
 * `IllegalArgumentException`을 상속하는 이유는 프론트엔드의 `/portone/payments/{id}/complete`
 * 경로 때문이다. 그쪽은 `GlobalExceptionHandler`가 `IllegalArgumentException`을 400으로
 * 매핑하고 있고, 잘못된 결제 ID는 사용자에게 400이 맞다.
 */
class PortOneWebhookFormatException(message: String) : IllegalArgumentException(message)
