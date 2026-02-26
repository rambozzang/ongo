package com.ongo.api.influencermatch

import com.ongo.application.influencermatch.InfluencerMatchUseCase
import com.ongo.application.influencermatch.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "인플루언서 매칭", description = "AI 기반 인플루언서 매칭 및 협업 요청 관리")
@RestController
@RequestMapping("/api/v1/influencer-match")
class InfluencerMatchController(
    private val influencerMatchUseCase: InfluencerMatchUseCase
) {

    @Operation(summary = "AI 인플루언서 매칭 검색")
    @PostMapping("/find")
    fun findMatches(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: FindMatchRequest,
    ): ResponseEntity<ResData<List<InfluencerMatchResponse>>> {
        val result = influencerMatchUseCase.findMatches(userId, request)
        return ResData.success(result)
    }

    @Operation(summary = "협업 요청 목록 조회")
    @GetMapping("/collabs")
    fun listCollabs(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<CollabRequestResponse>>> {
        val result = influencerMatchUseCase.listCollabs(userId)
        return ResData.success(result)
    }

    @Operation(summary = "협업 요청 보내기")
    @PostMapping("/collabs")
    fun sendCollabRequest(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: CreateCollabRequest,
    ): ResponseEntity<ResData<CollabRequestResponse>> {
        val result = influencerMatchUseCase.sendCollabRequest(userId, request)
        return ResData.success(result, "협업 요청이 전송되었습니다")
    }

    @Operation(summary = "협업 요청 상태 변경")
    @PutMapping("/collabs/{id}/status")
    fun updateCollabStatus(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
        @RequestBody request: UpdateCollabStatusRequest,
    ): ResponseEntity<ResData<CollabRequestResponse>> {
        val result = influencerMatchUseCase.updateCollabStatus(userId, id, request)
        return ResData.success(result)
    }
}
