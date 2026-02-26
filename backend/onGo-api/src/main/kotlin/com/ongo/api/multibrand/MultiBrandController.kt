package com.ongo.api.multibrand

import com.ongo.application.multibrand.MultiBrandUseCase
import com.ongo.application.multibrand.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Tag(name = "다중 브랜드 캘린더", description = "에이전시/MCN 다중 브랜드 콘텐츠 캘린더 관리")
@RestController
@RequestMapping("/api/v1/multi-brand")
class MultiBrandController(
    private val multiBrandUseCase: MultiBrandUseCase
) {

    @Operation(summary = "다중 브랜드 요약 조회")
    @GetMapping("/summary")
    fun getSummary(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<MultiBrandSummaryResponse>> {
        val result = multiBrandUseCase.getSummary(userId)
        return ResData.success(result)
    }

    @Operation(summary = "브랜드 목록 조회")
    @GetMapping("/brands")
    fun getBrands(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<BrandResponse>>> {
        val result = multiBrandUseCase.getBrands(userId)
        return ResData.success(result)
    }

    @Operation(summary = "브랜드 생성")
    @PostMapping("/brands")
    fun createBrand(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: CreateBrandRequest,
    ): ResponseEntity<ResData<BrandResponse>> {
        val result = multiBrandUseCase.createBrand(userId, request)
        return ResData.success(result, "브랜드가 생성되었습니다")
    }

    @Operation(summary = "브랜드 수정")
    @PutMapping("/brands/{id}")
    fun updateBrand(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
        @RequestBody request: UpdateBrandRequest,
    ): ResponseEntity<ResData<BrandResponse>> {
        val result = multiBrandUseCase.updateBrand(userId, id, request)
        return ResData.success(result, "브랜드가 수정되었습니다")
    }

    @Operation(summary = "브랜드 삭제")
    @DeleteMapping("/brands/{id}")
    fun deleteBrand(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        multiBrandUseCase.deleteBrand(userId, id)
        return ResData.success(null, "브랜드가 삭제되었습니다")
    }

    @Operation(summary = "스케줄 조회")
    @GetMapping("/schedule")
    fun getSchedule(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam startDate: String,
        @RequestParam endDate: String,
        @RequestParam(required = false) brandId: Long?,
    ): ResponseEntity<ResData<List<BrandScheduleItemResponse>>> {
        val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
        val start = LocalDateTime.parse(startDate, formatter)
        val end = LocalDateTime.parse(endDate, formatter)
        val result = multiBrandUseCase.getSchedule(userId, start, end, brandId)
        return ResData.success(result)
    }

    @Operation(summary = "스케줄 생성")
    @PostMapping("/schedule")
    fun createSchedule(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: CreateScheduleRequest,
    ): ResponseEntity<ResData<BrandScheduleItemResponse>> {
        val result = multiBrandUseCase.createSchedule(userId, request)
        return ResData.success(result, "스케줄이 생성되었습니다")
    }

    @Operation(summary = "스케줄 수정")
    @PutMapping("/schedule/{id}")
    fun updateSchedule(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
        @RequestBody request: UpdateScheduleRequest,
    ): ResponseEntity<ResData<BrandScheduleItemResponse>> {
        val result = multiBrandUseCase.updateSchedule(userId, id, request)
        return ResData.success(result, "스케줄이 수정되었습니다")
    }

    @Operation(summary = "스케줄 삭제")
    @DeleteMapping("/schedule/{id}")
    fun deleteSchedule(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        multiBrandUseCase.deleteSchedule(userId, id)
        return ResData.success(null, "스케줄이 삭제되었습니다")
    }
}
