package com.ongo.api.videodownload

import com.ongo.api.videodownload.dto.VideoDownloadAvailabilityResponse
import com.ongo.api.videodownload.dto.VideoDownloadRequest
import com.ongo.api.videodownload.dto.VideoDownloadResponse
import com.ongo.application.videodownload.VideoDownloadUseCase
import com.ongo.application.videodownload.VideoImportJobService
import com.ongo.api.videodownload.dto.VideoImportJobResponse
import org.springframework.web.bind.annotation.PathVariable
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
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "영상 URL 임포트", description = "YouTube, TikTok, Instagram URL에서 영상을 가져옵니다")
@RestController
@RequestMapping("/api/v1/videos")
class VideoDownloadController(
    private val videoDownloadUseCase: VideoDownloadUseCase,
    private val videoImportJobService: VideoImportJobService,
) {
    @Operation(
        summary = "영상 URL 임포트 시작",
        description = "지원 플랫폼의 URL 을 추출해 스토리지에 저장하는 작업을 시작합니다. 수 GB 원본은 수십 분이 " +
            "걸리므로 작업 번호를 바로 돌려주고, 결과는 GET /import-url/jobs/{jobId} 로 확인합니다.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "202", description = "작업 접수"),
        ApiResponse(responseCode = "400", description = "지원하지 않는 URL 또는 월 업로드 한도 초과"),
        ApiResponse(responseCode = "401", description = "인증 실패"),
    )
    @RequiresPermission(Permission.VIDEO_CREATE)
    @PostMapping("/import-url")
    fun importUrl(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @Valid @RequestBody request: VideoDownloadRequest,
    ): ResponseEntity<ResData<VideoImportJobResponse>> {
        val job = videoImportJobService.start(
            userId,
            com.ongo.application.videodownload.VideoDownloadRequest(url = request.url, title = request.title),
        )
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ResData(data = job.toResponse()))
    }

    @Operation(summary = "영상 URL 임포트 작업 상태", description = "본인 작업만 조회할 수 있습니다.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "조회 성공"),
        ApiResponse(responseCode = "404", description = "없는 작업(또는 서버 재기동으로 사라진 작업)"),
    )
    @RequiresPermission(Permission.VIDEO_CREATE)
    @GetMapping("/import-url/jobs/{jobId}")
    fun importJob(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @PathVariable jobId: String,
    ): ResponseEntity<ResData<VideoImportJobResponse>> =
        ResponseEntity.ok(ResData(data = videoImportJobService.get(userId, jobId).toResponse()))

    private fun VideoImportJobService.Job.toResponse() = VideoImportJobResponse(
        jobId = id,
        status = status.name,
        result = result?.let {
            VideoDownloadResponse(videoId = it.videoId, title = it.title, provider = it.provider, fileUrl = it.fileUrl)
        },
        errorCode = errorCode,
        errorMessage = errorMessage,
    )

    @Operation(
        summary = "영상 URL 임포트 가용성 조회",
        description = "추출기 바이너리가 호스트에 설치돼 있는지 확인한다. " +
            "화면은 이 값으로 진입점을 감추거나 비활성화한다.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "조회 성공. available=false 도 정상 응답이다"),
        ApiResponse(responseCode = "401", description = "인증 실패"),
    )
    @GetMapping("/import-url/availability")
    fun importUrlAvailability(): ResponseEntity<ResData<VideoDownloadAvailabilityResponse>> {
        val availability = videoDownloadUseCase.checkAvailability()
        // 쓸 수 없다는 것은 오류가 아니라 상태다. 200 으로 돌려준다.
        return ResponseEntity.ok(
            ResData(
                data = VideoDownloadAvailabilityResponse(
                    available = availability.available,
                    reason = availability.reason,
                ),
            ),
        )
    }
}
