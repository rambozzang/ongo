package com.ongo.api.video

import com.ongo.api.config.CurrentUser
import com.ongo.application.video.VideoFavoriteResponse
import com.ongo.application.video.VideoFavoriteUseCase
import com.ongo.common.ResData
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "영상 즐겨찾기", description = "사용자별 영상 즐겨찾기 관리")
@RestController
@RequestMapping("/api/v1/videos/favorites")
class VideoFavoriteController(
    private val useCase: VideoFavoriteUseCase,
) {
    @GetMapping
    fun list(@Parameter(hidden = true) @CurrentUser userId: Long): ResponseEntity<ResData<List<Long>>> =
        ResData.success(useCase.list(userId))

    @PutMapping("/{videoId}")
    fun toggle(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable videoId: Long,
    ): ResponseEntity<ResData<VideoFavoriteResponse>> = ResData.success(useCase.toggle(userId, videoId))

    @DeleteMapping("/{videoId}")
    fun remove(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable videoId: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        useCase.remove(userId, videoId)
        return ResData.success(null)
    }

    @DeleteMapping
    fun removeAll(@Parameter(hidden = true) @CurrentUser userId: Long): ResponseEntity<ResData<Nothing?>> {
        useCase.removeAll(userId)
        return ResData.success(null)
    }
}
