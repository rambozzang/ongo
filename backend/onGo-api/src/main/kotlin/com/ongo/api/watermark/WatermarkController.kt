package com.ongo.api.watermark

import com.ongo.api.config.CurrentUser
import com.ongo.application.watermark.WatermarkUseCase
import com.ongo.application.watermark.dto.CreateWatermarkRequest
import com.ongo.application.watermark.dto.UpdateWatermarkRequest
import com.ongo.application.watermark.dto.WatermarkListResponse
import com.ongo.application.watermark.dto.WatermarkResponse
import com.ongo.common.ResData
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "워터마크 관리", description = "영상 워터마크 프리셋 CRUD 관리")
@RestController
@RequestMapping("/api/v1/watermarks")
class WatermarkController(
    private val watermarkUseCase: WatermarkUseCase,
) {

    @Operation(summary = "워터마크 목록 조회", description = "사용자의 워터마크 프리셋 목록을 조회합니다.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "워터마크 목록 조회 성공"),
    )
    @GetMapping
    fun listWatermarks(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<WatermarkListResponse>> {
        val result = watermarkUseCase.listWatermarks(userId)
        return ResData.success(result)
    }

    @Operation(summary = "워터마크 생성", description = "새로운 워터마크 프리셋을 생성합니다.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "워터마크 생성 성공"),
    )
    @PostMapping
    fun createWatermark(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @Valid @RequestBody request: CreateWatermarkRequest,
    ): ResponseEntity<ResData<WatermarkResponse>> {
        val result = watermarkUseCase.createWatermark(userId, request)
        return ResData.success(result, "워터마크가 생성되었습니다")
    }

    @Operation(summary = "워터마크 수정", description = "기존 워터마크 프리셋을 수정합니다.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "워터마크 수정 성공"),
        ApiResponse(responseCode = "404", description = "워터마크를 찾을 수 없음"),
    )
    @PutMapping("/{id}")
    fun updateWatermark(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateWatermarkRequest,
    ): ResponseEntity<ResData<WatermarkResponse>> {
        val result = watermarkUseCase.updateWatermark(userId, id, request)
        return ResData.success(result, "워터마크가 수정되었습니다")
    }

    @Operation(summary = "워터마크 삭제", description = "기존 워터마크 프리셋을 삭제합니다.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "워터마크 삭제 성공"),
        ApiResponse(responseCode = "404", description = "워터마크를 찾을 수 없음"),
    )
    @DeleteMapping("/{id}")
    fun deleteWatermark(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        watermarkUseCase.deleteWatermark(userId, id)
        return ResData.success(null, "워터마크가 삭제되었습니다")
    }

    @Operation(summary = "기본 워터마크 설정", description = "지정된 워터마크를 기본 워터마크로 설정합니다.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "기본 워터마크 설정 성공"),
        ApiResponse(responseCode = "404", description = "워터마크를 찾을 수 없음"),
    )
    @PutMapping("/{id}/default")
    fun setDefaultWatermark(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<WatermarkResponse>> {
        val result = watermarkUseCase.setDefault(userId, id)
        return ResData.success(result, "기본 워터마크가 설정되었습니다")
    }
}
