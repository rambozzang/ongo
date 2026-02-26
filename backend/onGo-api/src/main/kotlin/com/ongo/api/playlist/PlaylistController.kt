package com.ongo.api.playlist

import com.ongo.application.playlist.PlaylistUseCase
import com.ongo.application.playlist.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "재생목록 관리", description = "크로스 플랫폼 재생목록 관리")
@RestController
@RequestMapping("/api/v1/playlists")
class PlaylistController(
    private val playlistUseCase: PlaylistUseCase
) {

    @Operation(summary = "재생목록 조회")
    @GetMapping
    fun getPlaylists(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam(required = false) platform: String?,
    ): ResponseEntity<ResData<List<PlaylistResponse>>> {
        val result = playlistUseCase.getPlaylists(userId, platform)
        return ResData.success(result)
    }

    @Operation(summary = "재생목록 상세 조회")
    @GetMapping("/{id}")
    fun getPlaylist(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<PlaylistResponse>> {
        val result = playlistUseCase.getPlaylist(userId, id)
        return ResData.success(result)
    }

    @Operation(summary = "재생목록 생성")
    @PostMapping
    fun createPlaylist(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: CreatePlaylistRequest,
    ): ResponseEntity<ResData<PlaylistResponse>> {
        val result = playlistUseCase.createPlaylist(userId, request)
        return ResData.success(result, "재생목록이 생성되었습니다")
    }

    @Operation(summary = "재생목록 수정")
    @PutMapping("/{id}")
    fun updatePlaylist(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
        @RequestBody request: UpdatePlaylistRequest,
    ): ResponseEntity<ResData<PlaylistResponse>> {
        val result = playlistUseCase.updatePlaylist(userId, id, request)
        return ResData.success(result, "재생목록이 수정되었습니다")
    }

    @Operation(summary = "재생목록 삭제")
    @DeleteMapping("/{id}")
    fun deletePlaylist(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        playlistUseCase.deletePlaylist(userId, id)
        return ResData.success(null, "재생목록이 삭제되었습니다")
    }

    @Operation(summary = "재생목록 영상 조회")
    @GetMapping("/{playlistId}/videos")
    fun getVideos(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable playlistId: Long,
    ): ResponseEntity<ResData<List<PlaylistVideoResponse>>> {
        val result = playlistUseCase.getVideos(userId, playlistId)
        return ResData.success(result)
    }

    @Operation(summary = "재생목록 영상 추가")
    @PostMapping("/{playlistId}/videos")
    fun addVideo(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable playlistId: Long,
        @RequestBody request: AddVideoRequest,
    ): ResponseEntity<ResData<PlaylistVideoResponse>> {
        val result = playlistUseCase.addVideo(userId, playlistId, request)
        return ResData.success(result, "영상이 추가되었습니다")
    }

    @Operation(summary = "재생목록 영상 제거")
    @DeleteMapping("/{playlistId}/videos/{videoId}")
    fun removeVideo(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable playlistId: Long,
        @PathVariable videoId: String,
    ): ResponseEntity<ResData<Nothing?>> {
        playlistUseCase.removeVideo(userId, playlistId, videoId)
        return ResData.success(null, "영상이 제거되었습니다")
    }

    @Operation(summary = "재생목록 영상 순서 변경")
    @PutMapping("/{playlistId}/videos/reorder")
    fun reorderVideos(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable playlistId: Long,
        @RequestBody request: ReorderVideosRequest,
    ): ResponseEntity<ResData<Nothing?>> {
        playlistUseCase.reorderVideos(userId, playlistId, request)
        return ResData.success(null, "순서가 변경되었습니다")
    }

    @Operation(summary = "재생목록 싱크")
    @PostMapping("/{id}/sync")
    fun syncPlaylist(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<PlaylistResponse>> {
        val result = playlistUseCase.syncPlaylist(userId, id)
        return ResData.success(result, "재생목록이 동기화되었습니다")
    }

    @Operation(summary = "재생목록 요약 조회")
    @GetMapping("/summary")
    fun getSummary(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<PlaylistSummaryResponse>> {
        val result = playlistUseCase.getSummary(userId)
        return ResData.success(result)
    }
}
