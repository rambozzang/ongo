package com.ongo.api.paddle

import com.ongo.application.paddle.PaddleWebhookService
import com.ongo.common.ResData
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
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(ResData(success = false, error = e.message))
        }
    }
}
