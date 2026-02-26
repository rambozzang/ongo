package com.ongo.api.creatorbenchmark

import com.ongo.application.creatorbenchmark.CreatorBenchmarkUseCase
import com.ongo.application.creatorbenchmark.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "크리에이터 벤치마크", description = "동일 분야 크리에이터와 성과 비교 분석")
@RestController
@RequestMapping("/api/v1/creator-benchmark")
class CreatorBenchmarkController(
    private val useCase: CreatorBenchmarkUseCase
) {

    @Operation(summary = "벤치마크 결과 조회")
    @GetMapping
    fun getResults(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam(required = false) platform: String?,
    ): ResponseEntity<ResData<List<BenchmarkResultResponse>>> {
        val result = useCase.getResults(userId, platform)
        return ResData.success(result)
    }

    @Operation(summary = "벤치마크 피어 목록 조회")
    @GetMapping("/peers")
    fun getPeers(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam(required = false) platform: String?,
    ): ResponseEntity<ResData<List<BenchmarkPeerResponse>>> {
        val result = useCase.getPeers(userId, platform)
        return ResData.success(result)
    }

    @Operation(summary = "벤치마크 요약")
    @GetMapping("/summary")
    fun getSummary(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<CreatorBenchmarkSummaryResponse>> {
        val result = useCase.getSummary(userId)
        return ResData.success(result)
    }
}
