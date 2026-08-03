package com.ongo.api.ugc

import com.ongo.application.ugc.shorts.ShortsScheduleSheetService
import com.ongo.application.ugc.shorts.dto.SheetPreviewResponse
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@Tag(name = "UGC 쇼츠 예약표", description = "클립 업로드 예약표 엑셀 내보내기·가져오기(preview/apply 2단계)")
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/ugc/shorts/runs")
class ShortsSheetController(
    private val sheetService: ShortsScheduleSheetService,
) {

    @Operation(summary = "예약표 엑셀 다운로드", description = "클립별 제목·후킹·캡션·예약시각이 담긴 .xlsx 를 내보낸다")
    @GetMapping("/{runId}/sheet")
    fun downloadSheet(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable workspaceId: Long,
        @PathVariable runId: Long,
    ): ResponseEntity<ByteArray> {
        val xlsx = sheetService.exportSheet(userId, workspaceId, runId)
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"shorts-run-$runId-schedule.xlsx\"")
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(xlsx)
    }

    @Operation(
        summary = "예약표 가져오기 1단계 — 미리보기",
        description = "수정한 .xlsx 를 올리면 변경 diff만 돌려준다. DB는 건드리지 않는다",
    )
    @PostMapping("/{runId}/sheet/preview", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun previewSheet(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable workspaceId: Long,
        @PathVariable runId: Long,
        @RequestParam("file") file: MultipartFile,
    ): ResponseEntity<ResData<SheetPreviewResponse>> =
        ResData.success(sheetService.previewSheet(userId, workspaceId, runId, file.inputStream))

    @Operation(
        summary = "예약표 가져오기 2단계 — 반영",
        description = "미리보기에서 확인한 .xlsx 를 다시 올려 제목·후킹문구·캡션·예약시각 변경을 실제로 반영한다",
    )
    @PostMapping("/{runId}/sheet/apply", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun applySheet(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable workspaceId: Long,
        @PathVariable runId: Long,
        @RequestParam("file") file: MultipartFile,
    ): ResponseEntity<ResData<SheetPreviewResponse>> =
        ResData.success(sheetService.applySheet(userId, workspaceId, runId, file.inputStream))
}
