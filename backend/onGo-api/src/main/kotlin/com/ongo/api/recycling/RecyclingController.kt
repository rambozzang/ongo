package com.ongo.api.recycling

import com.ongo.api.config.CurrentUser
import com.ongo.api.recycling.dto.RecyclingSuggestionResponse
import com.ongo.application.recycling.RecyclingUseCase
import com.ongo.common.ResData
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "콘텐츠 재활용", description = "콘텐츠 재활용 제안 생성, 조회, 수락, 거절")
@RestController
@RequestMapping("/api/v1/recycling")
class RecyclingController(
    private val recyclingUseCase: RecyclingUseCase,
) {

    @Operation(
        summary = "재활용 제안 목록 조회",
        description = "사용자의 콘텐츠 재활용 제안 목록을 조회합니다. 상태별로 필터링할 수 있습니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "조회 성공"),
        ApiResponse(responseCode = "401", description = "인증 실패")
    )
    @GetMapping("/suggestions")
    fun getSuggestions(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @Parameter(description = "상태 필터 (PENDING, ACCEPTED, DISMISSED)") @RequestParam(required = false) status: String?,
    ): ResponseEntity<ResData<List<RecyclingSuggestionResponse>>> {
        val suggestions = recyclingUseCase.getSuggestions(userId, status)
        return ResData.success(
            suggestions.map { s ->
                RecyclingSuggestionResponse(
                    id = s.id!!,
                    videoId = s.videoId,
                    suggestionType = s.suggestionType,
                    reason = s.reason,
                    suggestedPlatforms = s.suggestedPlatforms,
                    priorityScore = s.priorityScore,
                    status = s.status,
                    createdAt = s.createdAt,
                )
            }
        )
    }

    @Operation(
        summary = "재활용 제안 생성",
        description = "사용자의 기존 영상을 분석하여 재활용 제안을 생성합니다. 30일 이상 지난 영상 중 성과가 좋은 영상을 대상으로 합니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "제안 생성 성공"),
        ApiResponse(responseCode = "401", description = "인증 실패")
    )
    @PostMapping("/suggestions/generate")
    fun generateSuggestions(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<RecyclingSuggestionResponse>>> {
        val suggestions = recyclingUseCase.generateSuggestions(userId)
        return ResData.success(
            suggestions.map { s ->
                RecyclingSuggestionResponse(
                    id = s.id!!,
                    videoId = s.videoId,
                    suggestionType = s.suggestionType,
                    reason = s.reason,
                    suggestedPlatforms = s.suggestedPlatforms,
                    priorityScore = s.priorityScore,
                    status = s.status,
                    createdAt = s.createdAt,
                )
            },
            "재활용 제안이 생성되었습니다"
        )
    }

    @Operation(
        summary = "재활용 제안 수락",
        description = "재활용 제안을 수락합니다. 수락된 제안은 재활용 큐에 추가됩니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "수락 성공"),
        ApiResponse(responseCode = "401", description = "인증 실패"),
        ApiResponse(responseCode = "404", description = "제안을 찾을 수 없음")
    )
    @PutMapping("/suggestions/{id}/accept")
    fun acceptSuggestion(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @Parameter(description = "제안 ID") @PathVariable id: Long,
    ): ResponseEntity<ResData<RecyclingSuggestionResponse>> {
        val suggestion = recyclingUseCase.acceptSuggestion(userId, id)
        return ResData.success(
            RecyclingSuggestionResponse(
                id = suggestion.id!!,
                videoId = suggestion.videoId,
                suggestionType = suggestion.suggestionType,
                reason = suggestion.reason,
                suggestedPlatforms = suggestion.suggestedPlatforms,
                priorityScore = suggestion.priorityScore,
                status = suggestion.status,
                createdAt = suggestion.createdAt,
            ),
            "제안이 수락되었습니다"
        )
    }

    @Operation(
        summary = "재활용 제안 거절",
        description = "재활용 제안을 거절합니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "거절 성공"),
        ApiResponse(responseCode = "401", description = "인증 실패"),
        ApiResponse(responseCode = "404", description = "제안을 찾을 수 없음")
    )
    @PutMapping("/suggestions/{id}/dismiss")
    fun dismissSuggestion(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @Parameter(description = "제안 ID") @PathVariable id: Long,
    ): ResponseEntity<ResData<RecyclingSuggestionResponse>> {
        val suggestion = recyclingUseCase.dismissSuggestion(userId, id)
        return ResData.success(
            RecyclingSuggestionResponse(
                id = suggestion.id!!,
                videoId = suggestion.videoId,
                suggestionType = suggestion.suggestionType,
                reason = suggestion.reason,
                suggestedPlatforms = suggestion.suggestedPlatforms,
                priorityScore = suggestion.priorityScore,
                status = suggestion.status,
                createdAt = suggestion.createdAt,
            ),
            "제안이 거절되었습니다"
        )
    }
}
