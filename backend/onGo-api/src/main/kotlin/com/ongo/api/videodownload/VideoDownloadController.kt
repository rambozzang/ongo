package com.ongo.api.videodownload

import com.ongo.api.videodownload.dto.VideoDownloadRequest
import com.ongo.api.videodownload.dto.VideoDownloadResponse
import com.ongo.application.videodownload.VideoDownloadUseCase
import com.ongo.common.ResData
import com.ongo.common.annotation.RequiresPermission
import com.ongo.common.enums.Permission
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "영상 URL 임포트", description = "YouTube, TikTok, Instagram URL에서 영상을 가져옵니다")
@RestController
@RequestMapping("/api/v1/videos")
class VideoDownloadController(
    private val videoDownloadUseCase: VideoDownloadUseCase,
) {
    @Operation(
        summary = "영상 URL 임포트",
        description = "지원 플랫폼의 URL을 추출해 스토리지에 저장하고 내 영상 레코드를 생성합니다.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "영상 임포트 완료"),
        ApiResponse(responseCode = "400", description = "지원하지 않는 URL 또는 영상 형식"),
        ApiResponse(responseCode = "401", description = "인증 실패"),
    )
    @RequiresPermission(Permission.VIDEO_CREATE)
    @PostMapping("/import-url")
    fun importUrl(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @Valid @RequestBody request: VideoDownloadRequest,
    ): ResponseEntity<ResData<VideoDownloadResponse>> {
        val result = videoDownloadUseCase.importVideo(
            userId,
            com.ongo.application.videodownload.VideoDownloadRequest(
                url = request.url,
                title = request.title,
            ),
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(
            ResData(
                data = VideoDownloadResponse(
                    videoId = result.videoId,
                    title = result.title,
                    provider = result.provider,
                    fileUrl = result.fileUrl,
                ),
            )
        )
    }
}
