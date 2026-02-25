package com.ongo.api.copyrightcheck

import com.ongo.application.copyrightcheck.CopyrightCheckUseCase
import com.ongo.application.copyrightcheck.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "저작권 검사", description = "영상 저작권 검사 실행 및 결과 조회")
@RestController
@RequestMapping("/api/v1/copyright-check")
class CopyrightCheckController(
    private val copyrightCheckUseCase: CopyrightCheckUseCase
) {

    @Operation(summary = "저작권 검사 결과 목록 조회")
    @GetMapping("/results")
    fun listResults(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<CopyrightCheckResultResponse>>> {
        val result = copyrightCheckUseCase.listResults(userId)
        return ResData.success(result)
    }

    @Operation(summary = "저작권 검사 결과 상세 조회")
    @GetMapping("/results/{id}")
    fun getResult(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<CopyrightCheckResultResponse>> {
        val result = copyrightCheckUseCase.getResult(userId, id)
        return ResData.success(result)
    }

    @Operation(summary = "저작권 검사 실행")
    @PostMapping("/run")
    fun runCheck(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: RunCheckRequest,
    ): ResponseEntity<ResData<RunCheckResponse>> {
        val result = copyrightCheckUseCase.runCheck(userId, request)
        return ResData.success(result, "저작권 검사가 시작되었습니다")
    }
}
