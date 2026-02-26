package com.ongo.api.hashtaganalytics

import com.ongo.application.hashtaganalytics.HashtagAnalyticsUseCase
import com.ongo.application.hashtaganalytics.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "해시태그 분석기", description = "AI 해시태그 성과 분석 및 추천")
@RestController
@RequestMapping("/api/v1/hashtag-analytics")
class HashtagAnalyticsController(
    private val useCase: HashtagAnalyticsUseCase
) {

    @Operation(summary = "해시태그 성과 목록 조회")
    @GetMapping
    fun getPerformances(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam(required = false) platform: String?,
    ): ResponseEntity<ResData<List<HashtagPerformanceResponse>>> {
        val result = useCase.getPerformances(userId, platform)
        return ResData.success(result)
    }

    @Operation(summary = "해시태그 AI 분석")
    @PostMapping("/analyze")
    fun analyzeHashtags(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: AnalyzeHashtagsRequest,
    ): ResponseEntity<ResData<List<HashtagRecommendationResponse>>> {
        val result = useCase.analyzeHashtags(userId, request)
        return ResData.success(result)
    }

    @Operation(summary = "해시태그 그룹 목록 조회")
    @GetMapping("/groups")
    fun getGroups(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<HashtagGroupResponse>>> {
        val result = useCase.getGroups(userId)
        return ResData.success(result)
    }

    @Operation(summary = "해시태그 그룹 생성")
    @PostMapping("/groups")
    fun createGroup(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: CreateHashtagGroupRequest,
    ): ResponseEntity<ResData<HashtagGroupResponse>> {
        val result = useCase.createGroup(userId, request)
        return ResData.success(result, "해시태그 그룹이 생성되었습니다")
    }

    @Operation(summary = "해시태그 그룹 삭제")
    @DeleteMapping("/groups/{id}")
    fun deleteGroup(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        useCase.deleteGroup(userId, id)
        return ResData.success(null, "해시태그 그룹이 삭제되었습니다")
    }

    @Operation(summary = "해시태그 분석 요약")
    @GetMapping("/summary")
    fun getSummary(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<HashtagAnalyticsSummaryResponse>> {
        val result = useCase.getSummary(userId)
        return ResData.success(result)
    }
}
