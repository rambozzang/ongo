package com.ongo.api.paddle

import com.ongo.application.paddle.PaddleWebhookFormatException
import com.ongo.application.paddle.PaddleWebhookService
import com.ongo.common.ResData
import com.ongo.common.exception.UnauthorizedException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/paddle")
class PaddleWebhookController(
    private val webhookService: PaddleWebhookService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Paddle 웹훅 수신 엔드포인트.
     *
     * ## 응답 코드가 곧 재전송 여부다
     *
     * [com.ongo.application.webhook.WebhookInboundGuard] 는 업무 처리 실패를 기록한 뒤
     * **예외를 그대로 다시 던진다.** 그 KDoc 이 적어 둔 대로 "호출자(컨트롤러)가 2xx 가 아닌
     * 응답을 돌려줘야 PG 가 재전송한다" — 어떤 코드를 돌려줄지가 이 클래스의 책임이다.
     *
     * 예전에는 `catch (e: Exception)` 하나로 **전부 400** 을 돌려줬다. 그러면 DB 장애나
     * 게이트웨이 타임아웃처럼 다시 시도하면 풀릴 실패까지 "요청이 잘못됐다"고 답하게 된다.
     * 결제는 이미 승인됐는데 구독 권한과 크레딧은 반영되지 않은 채 남고, 재전송이 끊기면
     * 되찾을 방법이 없다. 포트원 쪽은 같은 이유로 이미 나눠 두었다
     * ([com.ongo.api.portone.PortOneController]).
     *
     *  - **400** — 서명·타임스탬프 실패, 본문 형식 오류. 같은 요청을 다시 보내도 결과가 같다.
     *  - **5xx** — 그 밖의 모든 실패. 재전송으로 복구될 수 있으므로 그렇게 알린다.
     *
     * 형식 오류를 [PaddleWebhookFormatException] 이라는 **전용 타입**으로만 좁히는 것이
     * 중요하다. 업무 처리도 `IllegalStateException` 을 던지므로, 타입을 넓게 잡으면 위
     * 사고가 그대로 재현된다.
     */
    @PostMapping("/webhooks")
    fun handleWebhook(
        @RequestBody rawBody: String,
        @RequestHeader("Paddle-Signature", required = false) paddleSignature: String?,
    ): ResponseEntity<ResData<Nothing>> {
        if (paddleSignature.isNullOrBlank()) {
            return ResponseEntity.badRequest().body(ResData(success = false, error = "Paddle-Signature 헤더가 누락되었습니다"))
        }
        return try {
            webhookService.handleWebhook(rawBody, paddleSignature)
            ResponseEntity.ok(ResData(success = true))
        } catch (e: UnauthorizedException) {
            // 서명·타임스탬프 검증 실패 — 재전송해도 같은 결과다.
            log.warn("Paddle 웹훅 서명 검증 실패: {}", e.message)
            ResponseEntity.badRequest().body(ResData(success = false, error = e.message))
        } catch (e: PaddleWebhookFormatException) {
            // 본문 형식 오류 — 같은 본문이 다시 올 뿐이다.
            log.warn("Paddle 웹훅 본문 오류: {}", e.message)
            ResponseEntity.badRequest().body(ResData(success = false, error = e.message))
        } catch (e: Exception) {
            // 업무 처리·DB·게이트웨이 실패 — 업무 트랜잭션은 이미 롤백됐다.
            // 5xx 를 돌려줘야 Paddle 이 재전송해 복구할 수 있다.
            log.error("Paddle 웹훅 처리 실패 — 재전송 유도를 위해 5xx 반환", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ResData(success = false, error = e.message))
        }
    }
}
