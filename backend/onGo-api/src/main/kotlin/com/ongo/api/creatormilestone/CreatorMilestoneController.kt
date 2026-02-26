package com.ongo.api.creatormilestone

import com.ongo.application.creatormilestone.CreatorMilestoneUseCase
import com.ongo.application.creatormilestone.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "크리에이터 마일스톤", description = "크리에이터 성장 목표 및 달성 추적")
@RestController
@RequestMapping("/api/v1/creator-milestones")
class CreatorMilestoneController(
    private val useCase: CreatorMilestoneUseCase
) {

    @Operation(summary = "마일스톤 목록 조회")
    @GetMapping
    fun getMilestones(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam(required = false) status: String?,
    ): ResponseEntity<ResData<List<CreatorMilestoneResponse>>> {
        val result = useCase.getMilestones(userId, status)
        return ResData.success(result)
    }

    @Operation(summary = "마일스톤 상세 조회")
    @GetMapping("/{id}")
    fun getMilestone(
        @PathVariable id: Long,
    ): ResponseEntity<ResData<CreatorMilestoneResponse?>> {
        val result = useCase.getMilestone(id)
        return ResData.success(result)
    }

    @Operation(summary = "마일스톤 생성")
    @PostMapping
    fun createMilestone(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: CreateMilestoneRequest,
    ): ResponseEntity<ResData<CreatorMilestoneResponse>> {
        val result = useCase.createMilestone(userId, request)
        return ResData.success(result)
    }

    @Operation(summary = "마일스톤 삭제")
    @DeleteMapping("/{id}")
    fun deleteMilestone(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<Unit>> {
        useCase.deleteMilestone(userId, id)
        return ResData.success(Unit)
    }

    @Operation(summary = "마일스톤 요약")
    @GetMapping("/summary")
    fun getSummary(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<CreatorMilestoneSummaryResponse>> {
        val result = useCase.getSummary(userId)
        return ResData.success(result)
    }
}
