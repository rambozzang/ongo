package com.ongo.api.creatornetwork

import com.ongo.application.creatornetwork.CreatorNetworkUseCase
import com.ongo.application.creatornetwork.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "크리에이터 네트워크", description = "크리에이터 간 협업 네트워크")
@RestController
@RequestMapping("/api/v1/creator-network")
class CreatorNetworkController(
    private val useCase: CreatorNetworkUseCase
) {

    @Operation(summary = "크리에이터 목록 조회")
    @GetMapping
    fun getCreators(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam(required = false) category: String?,
    ): ResponseEntity<ResData<List<CreatorProfileResponse>>> {
        val result = useCase.getCreators(userId, category)
        return ResData.success(result)
    }

    @Operation(summary = "크리에이터 상세 조회")
    @GetMapping("/{id}")
    fun getCreator(
        @PathVariable id: Long,
    ): ResponseEntity<ResData<CreatorProfileResponse?>> {
        val result = useCase.getCreator(id)
        return ResData.success(result)
    }

    @Operation(summary = "협업 요청 목록 조회")
    @GetMapping("/requests")
    fun getRequests(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<CollaborationRequestResponse>>> {
        val result = useCase.getRequests(userId)
        return ResData.success(result)
    }

    @Operation(summary = "협업 요청 응답")
    @PatchMapping("/requests/{id}")
    fun respondRequest(
        @PathVariable id: Long,
        @RequestParam accept: Boolean,
    ): ResponseEntity<ResData<Unit>> {
        useCase.respondRequest(id, accept)
        return ResData.success(Unit)
    }

    @Operation(summary = "네트워크 요약")
    @GetMapping("/summary")
    fun getSummary(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<CreatorNetworkSummaryResponse>> {
        val result = useCase.getSummary(userId)
        return ResData.success(result)
    }
}
