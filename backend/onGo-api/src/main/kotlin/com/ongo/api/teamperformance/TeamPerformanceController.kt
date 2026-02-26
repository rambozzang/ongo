package com.ongo.api.teamperformance

import com.ongo.application.teamperformance.TeamPerformanceUseCase
import com.ongo.application.teamperformance.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "팀 성과", description = "팀 멤버 성과 분석 및 활동 추적")
@RestController
@RequestMapping("/api/v1/team-performance")
class TeamPerformanceController(
    private val useCase: TeamPerformanceUseCase
) {

    @Operation(summary = "팀 멤버 목록 조회")
    @GetMapping("/members")
    fun getMembers(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<TeamMemberPerformanceResponse>>> {
        val result = useCase.getMembers(userId)
        return ResData.success(result)
    }

    @Operation(summary = "팀 멤버 상세 조회")
    @GetMapping("/members/{id}")
    fun getMember(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<TeamMemberPerformanceResponse>> {
        val result = useCase.getMember(id) ?: throw IllegalArgumentException("멤버를 찾을 수 없습니다")
        return ResData.success(result)
    }

    @Operation(summary = "팀 활동 목록 조회")
    @GetMapping("/activities")
    fun getActivities(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam(defaultValue = "20") limit: Int,
    ): ResponseEntity<ResData<List<TeamActivityResponse>>> {
        val result = useCase.getActivities(userId, limit)
        return ResData.success(result)
    }

    @Operation(summary = "팀 성과 요약")
    @GetMapping("/summary")
    fun getSummary(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<TeamPerformanceSummaryResponse>> {
        val result = useCase.getSummary(userId)
        return ResData.success(result)
    }
}
