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
    @GetMapping("/creators")
    fun getCreatorsList(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam(required = false) category: String?,
        @RequestParam(required = false) search: String?,
        @RequestParam(required = false) sortBy: String?,
    ): ResponseEntity<ResData<List<CreatorProfileResponse>>> {
        val result = useCase.getCreators(userId, category)
        return ResData.success(result)
    }

    @Operation(summary = "크리에이터 목록 조회 (루트)")
    @GetMapping
    fun getCreators(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam(required = false) category: String?,
    ): ResponseEntity<ResData<List<CreatorProfileResponse>>> {
        val result = useCase.getCreators(userId, category)
        return ResData.success(result)
    }

    @Operation(summary = "크리에이터 상세 조회")
    @GetMapping("/creators/{id}")
    fun getCreatorDetail(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<CreatorProfileResponse?>> {
        val result = useCase.getCreator(id)
        return ResData.success(result)
    }

    @Operation(summary = "크리에이터 상세 조회 (레거시)")
    @GetMapping("/{id}")
    fun getCreator(
        @PathVariable id: Long,
    ): ResponseEntity<ResData<CreatorProfileResponse?>> {
        val result = useCase.getCreator(id)
        return ResData.success(result)
    }

    @Operation(summary = "크리에이터 연결 요청")
    @PostMapping("/creators/{creatorId}/connect")
    fun connectCreator(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable creatorId: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        useCase.connectCreator(userId, creatorId)
        return ResData.success(null, "연결 요청이 전송되었습니다")
    }

    @Operation(summary = "크리에이터 연결 해제")
    @DeleteMapping("/creators/{creatorId}/connect")
    fun disconnectCreator(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable creatorId: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        useCase.disconnectCreator(userId, creatorId)
        return ResData.success(null, "연결이 해제되었습니다")
    }

    @Operation(summary = "협업 요청 목록 조회")
    @GetMapping("/collabs")
    fun getCollabs(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<CollaborationRequestResponse>>> {
        val result = useCase.getRequests(userId)
        return ResData.success(result)
    }

    @Operation(summary = "협업 요청 목록 조회 (레거시)")
    @GetMapping("/requests")
    fun getRequests(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<CollaborationRequestResponse>>> {
        val result = useCase.getRequests(userId)
        return ResData.success(result)
    }

    @Operation(summary = "협업 요청 생성")
    @PostMapping("/collabs")
    fun createCollabRequest(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: CreateCollabRequest,
    ): ResponseEntity<ResData<CollaborationRequestResponse>> {
        val result = useCase.createCollabRequest(userId, request)
        return ResData.success(result, "협업 요청이 전송되었습니다")
    }

    @Operation(summary = "협업 요청 응답")
    @PostMapping("/collabs/{id}/respond")
    fun respondCollabRequest(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
        @RequestBody request: RespondCollabRequest,
    ): ResponseEntity<ResData<CollaborationRequestResponse>> {
        val result = useCase.respondCollabRequest(userId, id, request.accept)
        return ResData.success(result)
    }

    @Operation(summary = "협업 요청 응답 (레거시)")
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
