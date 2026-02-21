package com.ongo.api.competitor

import com.ongo.application.competitor.CompetitorUseCase
import com.ongo.application.competitor.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "경쟁자 분석", description = "경쟁 채널 추가, 수정, 삭제, 목록 조회")
@RestController
@RequestMapping("/api/v1/competitors")
class CompetitorController(
    private val competitorUseCase: CompetitorUseCase,
) {

    @Operation(summary = "채널 정보 조회", description = "플랫폼 채널 URL/핸들로 채널 정보를 조회합니다")
    @PostMapping("/lookup")
    fun lookupChannel(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @Valid @RequestBody request: ChannelLookupRequest,
    ): ResponseEntity<ResData<ChannelLookupResponse>> {
        val result = competitorUseCase.lookupChannel(userId, request)
        return ResData.success(result)
    }

    @Operation(summary = "경쟁자 목록 조회")
    @GetMapping
    fun listCompetitors(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<CompetitorListResponse>> {
        return ResData.success(competitorUseCase.listCompetitors(userId))
    }

    @Operation(summary = "경쟁자 추가")
    @PostMapping
    fun addCompetitor(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @Valid @RequestBody request: CreateCompetitorRequest,
    ): ResponseEntity<ResData<CompetitorResponse>> {
        val result = competitorUseCase.addCompetitor(userId, request)
        return ResData.success(result, "경쟁자가 추가되었습니다")
    }

    @Operation(summary = "경쟁자 정보 수정")
    @PutMapping("/{id}")
    fun updateCompetitor(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateCompetitorRequest,
    ): ResponseEntity<ResData<CompetitorResponse>> {
        val result = competitorUseCase.updateCompetitor(userId, id, request)
        return ResData.success(result, "경쟁자 정보가 수정되었습니다")
    }

    @Operation(summary = "경쟁자 삭제")
    @DeleteMapping("/{id}")
    fun removeCompetitor(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        competitorUseCase.removeCompetitor(userId, id)
        return ResData.success(null, "경쟁자가 삭제되었습니다")
    }

    @Operation(summary = "경쟁자 트렌드 데이터 조회")
    @PostMapping("/trends")
    fun getCompetitorTrends(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @Valid @RequestBody request: CompetitorTrendRequest,
    ): ResponseEntity<ResData<List<CompetitorTrendResponse>>> {
        return ResData.success(competitorUseCase.getCompetitorTrends(userId, request))
    }

    @Operation(summary = "벤치마크 비교 데이터 조회")
    @GetMapping("/benchmark")
    fun getBenchmark(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<BenchmarkResponse>> {
        return ResData.success(competitorUseCase.getBenchmark(userId))
    }

    @Operation(summary = "경쟁자 데이터 수동 동기화")
    @PostMapping("/sync")
    fun syncCompetitors(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<CompetitorListResponse>> {
        val competitors = competitorUseCase.listCompetitors(userId)
        return ResData.success(competitors, "동기화가 완료되었습니다")
    }
}
