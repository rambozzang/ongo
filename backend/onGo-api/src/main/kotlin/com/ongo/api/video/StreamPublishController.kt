package com.ongo.api.video

import com.fasterxml.jackson.databind.ObjectMapper
import com.ongo.api.video.dto.PlatformPublishConfigRequest
import com.ongo.api.video.dto.StreamPublishMetadataRequest
import com.ongo.api.video.dto.StreamPublishResponse
import com.ongo.application.video.PlatformPublishRequest
import com.ongo.application.video.StreamPublishRequest
import com.ongo.application.video.StreamPublishUseCase
import com.ongo.common.ResData
import com.ongo.common.annotation.RequiresPermission
import com.ongo.common.enums.Permission
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@Tag(name = "영상 스트리밍 게시", description = "파일을 서버 저장 없이 각 플랫폼에 직접 스트리밍 업로드")
@RestController
@RequestMapping("/api/v1/videos")
class StreamPublishController(
    private val streamPublishUseCase: StreamPublishUseCase,
    private val objectMapper: ObjectMapper,
) {

    @Operation(summary = "플랫폼별 영상 업로드 기능 및 제약 조회")
    @RequiresPermission(Permission.VIDEO_CREATE)
    @GetMapping("/stream-publish/capabilities")
    fun capabilities() = ResData(data = streamPublishUseCase.getCapabilities())

    @Operation(
        summary = "스트리밍 게시",
        description = "영상 파일과 메타데이터를 받아 서버에 저장하지 않고 선택한 플랫폼에 직접 스트리밍 업로드합니다. 202 Accepted를 즉시 반환하며 실제 업로드는 백그라운드에서 진행됩니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "202", description = "업로드 수락됨 (videoId 반환)"),
        ApiResponse(responseCode = "400", description = "잘못된 요청"),
        ApiResponse(responseCode = "401", description = "인증 실패"),
    )
    @RequiresPermission(Permission.VIDEO_CREATE)
    @PostMapping("/stream-publish", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun streamPublish(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @RequestPart("metadata") metadataJson: String,
        @RequestPart("file") file: MultipartFile,
    ): ResponseEntity<ResData<StreamPublishResponse>> {
        val metadataDto = try {
            objectMapper.readValue(metadataJson, StreamPublishMetadataRequest::class.java)
        } catch (e: Exception) {
            throw IllegalArgumentException("게시 정보 형식이 올바르지 않습니다: ${e.message}", e)
        }

        val request = StreamPublishRequest(
            title = metadataDto.title,
            description = metadataDto.description,
            tags = metadataDto.tags,
            category = metadataDto.category,
            thumbnailUrl = metadataDto.thumbnailUrl,
            platforms = metadataDto.platforms.map { it.toDomain() },
        )

        val result = streamPublishUseCase.initiate(userId, file, request)

        return ResponseEntity
            .accepted()
            .body(ResData(data = StreamPublishResponse(videoId = result.videoId)))
    }

    private fun PlatformPublishConfigRequest.toDomain() = PlatformPublishRequest(
        platform = platform,
        channelId = channelId,
        title = title,
        description = description,
        tags = tags,
        visibility = visibility,
        scheduledAt = scheduledAt,
    )
}
