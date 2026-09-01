package com.ongo.application.paddle

/**
 * Paddle 웹훅 본문이 우리가 아는 형식이 아니다.
 *
 * **이 예외만 400 으로 응답한다.** 재전송해도 같은 본문이 다시 올 뿐이라 재시도가 의미 없다.
 *
 * 반대로 DB·게이트웨이 장애나 업무 처리 실패처럼 **재전송으로 복구될 수 있는 실패**를
 * 400 으로 돌려주면, Paddle 은 우리가 요청을 거부했다고 보고 재전송을 멈춘다. 결제는
 * 이미 승인됐는데 구독 권한과 크레딧은 영영 반영되지 않는다 — 되찾을 방법이 없다.
 * [com.ongo.application.portone.PortOneWebhookFormatException] 이 같은 이유로 있다.
 *
 * `IllegalStateException` 이 아니라 별도 타입인 이유는, 업무 처리도 `IllegalStateException`
 * 을 던지기 때문이다. 그 둘을 한 덩어리로 400 처리하면 위 사고가 그대로 난다.
 */
class PaddleWebhookFormatException(message: String) : IllegalArgumentException(message)
