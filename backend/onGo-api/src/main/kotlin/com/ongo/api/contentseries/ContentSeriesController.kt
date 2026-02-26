package com.ongo.api.contentseries

import com.ongo.application.contentseries.ContentSeriesUseCase
import com.ongo.application.contentseries.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "콘텐츠 시리즈", description = "콘텐츠 시리즈 관리 및 에피소드 추적")
@RestController
@RequestMapping("/api/v1/content-series")
class ContentSeriesController(
    private val contentSeriesUseCase: ContentSeriesUseCase
) {

    @Operation(summary = "시리즈 목록 조회")
    @GetMapping
    fun getAll(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<ContentSeriesResponse>>> {
        val result = contentSeriesUseCase.getAll(userId)
        return ResData.success(result)
    }

    @Operation(summary = "시리즈 상세 조회")
    @GetMapping("/{id}")
    fun getById(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<ContentSeriesResponse>> {
        val result = contentSeriesUseCase.getById(userId, id)
        return ResData.success(result)
    }

    @Operation(summary = "시리즈 생성")
    @PostMapping
    fun create(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: CreateSeriesRequest,
    ): ResponseEntity<ResData<ContentSeriesResponse>> {
        val result = contentSeriesUseCase.create(userId, request)
        return ResData.success(result, "시리즈가 생성되었습니다")
    }

    @Operation(summary = "시리즈 수정")
    @PutMapping("/{id}")
    fun update(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
        @RequestBody request: UpdateSeriesRequest,
    ): ResponseEntity<ResData<ContentSeriesResponse>> {
        val result = contentSeriesUseCase.update(userId, id, request)
        return ResData.success(result, "시리즈가 수정되었습니다")
    }

    @Operation(summary = "시리즈 삭제")
    @DeleteMapping("/{id}")
    fun delete(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        contentSeriesUseCase.delete(userId, id)
        return ResData.success(null, "시리즈가 삭제되었습니다")
    }

    @Operation(summary = "에피소드 추가")
    @PostMapping("/{seriesId}/episodes")
    fun addEpisode(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable seriesId: Long,
        @RequestBody request: AddEpisodeRequest,
    ): ResponseEntity<ResData<SeriesEpisodeResponse>> {
        val result = contentSeriesUseCase.addEpisode(userId, seriesId, request)
        return ResData.success(result, "에피소드가 추가되었습니다")
    }

    @Operation(summary = "에피소드 수정")
    @PutMapping("/{seriesId}/episodes/{episodeId}")
    fun updateEpisode(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable seriesId: Long,
        @PathVariable episodeId: Long,
        @RequestBody request: UpdateEpisodeRequest,
    ): ResponseEntity<ResData<SeriesEpisodeResponse>> {
        val result = contentSeriesUseCase.updateEpisode(userId, seriesId, episodeId, request)
        return ResData.success(result, "에피소드가 수정되었습니다")
    }

    @Operation(summary = "에피소드 삭제")
    @DeleteMapping("/{seriesId}/episodes/{episodeId}")
    fun deleteEpisode(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable seriesId: Long,
        @PathVariable episodeId: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        contentSeriesUseCase.deleteEpisode(userId, seriesId, episodeId)
        return ResData.success(null, "에피소드가 삭제되었습니다")
    }

    @Operation(summary = "시리즈 분석 데이터 조회")
    @GetMapping("/{seriesId}/analytics")
    fun getAnalytics(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable seriesId: Long,
    ): ResponseEntity<ResData<SeriesAnalyticsResponse>> {
        val result = contentSeriesUseCase.getAnalytics(userId, seriesId)
        return ResData.success(result)
    }
}
