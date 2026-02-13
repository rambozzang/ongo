package com.ongo.api.webhook

import com.ongo.api.config.CurrentUser
import com.ongo.application.webhook.WebhookUseCase
import com.ongo.application.webhook.dto.*
import com.ongo.common.ResData
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "웹훅", description = "웹훅 CRUD 및 테스트")
@RestController
@RequestMapping("/api/v1/webhooks")
class WebhookController(
    private val webhookUseCase: WebhookUseCase,
) {

    @Operation(summary = "웹훅 목록 조회")
    @GetMapping
    fun listWebhooks(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<WebhookResponse>>> {
        val result = webhookUseCase.listWebhooks(userId)
        return ResData.success(result)
    }

    @Operation(summary = "웹훅 생성")
    @PostMapping
    fun createWebhook(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: CreateWebhookRequest,
    ): ResponseEntity<ResData<WebhookResponse>> {
        val result = webhookUseCase.createWebhook(userId, request)
        return ResData.success(result, "웹훅이 생성되었습니다")
    }

    @Operation(summary = "웹훅 수정")
    @PutMapping("/{id}")
    fun updateWebhook(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
        @RequestBody request: UpdateWebhookRequest,
    ): ResponseEntity<ResData<WebhookResponse>> {
        val result = webhookUseCase.updateWebhook(userId, id, request)
        return ResData.success(result, "웹훅이 수정되었습니다")
    }

    @Operation(summary = "웹훅 삭제")
    @DeleteMapping("/{id}")
    fun deleteWebhook(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        webhookUseCase.deleteWebhook(userId, id)
        return ResData.success(null, "웹훅이 삭제되었습니다")
    }

    @Operation(summary = "웹훅 테스트 이벤트 전송")
    @PostMapping("/{id}/test")
    fun testWebhook(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<WebhookTestResponse>> {
        val result = webhookUseCase.testWebhook(userId, id)
        return ResData.success(result)
    }
}
