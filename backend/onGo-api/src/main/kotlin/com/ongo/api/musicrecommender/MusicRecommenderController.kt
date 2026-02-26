package com.ongo.api.musicrecommender

import com.ongo.application.musicrecommender.MusicRecommenderUseCase
import com.ongo.application.musicrecommender.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "AI 음악 추천기", description = "AI 기반 배경음악 추천")
@RestController
@RequestMapping("/api/v1/music-recommender")
class MusicRecommenderController(
    private val musicRecommenderUseCase: MusicRecommenderUseCase
) {

    @Operation(summary = "음악 추천 목록 조회")
    @GetMapping
    fun getRecommendations(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<MusicRecommendationResponse>>> {
        val result = musicRecommenderUseCase.getRecommendations(userId)
        return ResData.success(result)
    }

    @Operation(summary = "음악 추천 요약")
    @GetMapping("/summary")
    fun getSummary(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<MusicRecommenderSummaryResponse>> {
        val result = musicRecommenderUseCase.getSummary(userId)
        return ResData.success(result)
    }
}
