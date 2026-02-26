package com.ongo.api.livestream

import com.ongo.application.livestream.LiveStreamUseCase
import com.ongo.application.livestream.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "라이브 스트림 관리", description = "멀티플랫폼 라이브 스트리밍 관리")
@RestController
@RequestMapping("/api/v1/live-streams")
class LiveStreamController(
    private val liveStreamUseCase: LiveStreamUseCase
) {

    @Operation(summary = "스트림 목록 조회")
    @GetMapping
    fun getStreams(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam(required = false) status: String?,
    ): ResponseEntity<ResData<List<LiveStreamResponse>>> {
        val result = liveStreamUseCase.getStreams(userId, status)
        return ResData.success(result)
    }

    @Operation(summary = "스트림 채팅 조회")
    @GetMapping("/{streamId}/chats")
    fun getChats(
        @PathVariable streamId: Long,
    ): ResponseEntity<ResData<List<StreamChatResponse>>> {
        val result = liveStreamUseCase.getChats(streamId)
        return ResData.success(result)
    }

    @Operation(summary = "스트림 생성")
    @PostMapping
    fun create(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: CreateStreamRequest,
    ): ResponseEntity<ResData<LiveStreamResponse>> {
        val result = liveStreamUseCase.create(userId, request)
        return ResData.success(result, "라이브 스트림이 예약되었습니다")
    }

    @Operation(summary = "스트림 종료")
    @PutMapping("/{id}/end")
    fun endStream(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<LiveStreamResponse>> {
        val result = liveStreamUseCase.endStream(userId, id)
        return ResData.success(result, "라이브 스트림이 종료되었습니다")
    }

    @Operation(summary = "스트림 요약")
    @GetMapping("/summary")
    fun getSummary(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<LiveStreamSummaryResponse>> {
        val result = liveStreamUseCase.getSummary(userId)
        return ResData.success(result)
    }
}
