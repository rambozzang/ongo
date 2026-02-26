package com.ongo.api.contentrewriter

import com.ongo.application.contentrewriter.ContentRewriterUseCase
import com.ongo.application.contentrewriter.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "콘텐츠 리라이터", description = "콘텐츠를 다양한 포맷으로 리라이트")
@RestController
@RequestMapping("/api/v1/content-rewriter")
class ContentRewriterController(
    private val contentRewriterUseCase: ContentRewriterUseCase
) {

    @Operation(summary = "콘텐츠 리라이트 실행")
    @PostMapping("/rewrite")
    fun rewrite(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: RewriteRequest,
    ): ResponseEntity<ResData<RewriteResponse>> {
        val result = contentRewriterUseCase.rewrite(userId, request)
        return ResData.success(result, "콘텐츠 리라이트가 시작되었습니다")
    }

    @Operation(summary = "리라이트 이력 조회")
    @GetMapping("/history")
    fun history(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<RewriteHistoryResponse>>> {
        val result = contentRewriterUseCase.history(userId)
        return ResData.success(result)
    }

    @Operation(summary = "리라이트 결과 삭제")
    @DeleteMapping("/results/{id}")
    fun deleteResult(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        contentRewriterUseCase.deleteResult(userId, id)
        return ResData.success(null, "리라이트 결과가 삭제되었습니다")
    }
}
