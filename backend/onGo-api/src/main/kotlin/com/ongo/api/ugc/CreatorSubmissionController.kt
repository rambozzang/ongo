package com.ongo.api.ugc

import com.ongo.application.ugc.SubmissionUseCase
import com.ongo.application.ugc.dto.CreateSubmissionRequest
import com.ongo.application.ugc.dto.SubmissionListResponse
import com.ongo.application.ugc.dto.SubmissionResponse
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "UGC 제출 (크리에이터)", description = "콘텐츠 제출·재제출")
@RestController
@RequestMapping("/api/v1/ugc/me")
class CreatorSubmissionController(
    private val submissionUseCase: SubmissionUseCase,
) {

    @Operation(summary = "제출 생성/수정 (초안)")
    @PostMapping("/campaigns/{campaignId}/submissions")
    fun createOrUpdate(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable campaignId: Long,
        @RequestBody request: CreateSubmissionRequest,
    ): ResponseEntity<ResData<SubmissionResponse>> =
        ResData.success(submissionUseCase.createOrUpdateSubmission(userId, campaignId, request), "제출이 저장되었습니다")

    @Operation(summary = "내 제출 조회")
    @GetMapping("/campaigns/{campaignId}/submissions")
    fun listMine(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable campaignId: Long,
    ): ResponseEntity<ResData<SubmissionListResponse>> =
        ResData.success(submissionUseCase.listMySubmissions(userId, campaignId))

    @Operation(summary = "제출하기", description = "초안/수정요청 상태를 검수 대기(SUBMITTED)로 전환합니다.")
    @PostMapping("/submissions/{submissionId}/submit")
    fun submit(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable submissionId: Long,
    ): ResponseEntity<ResData<SubmissionResponse>> =
        ResData.success(submissionUseCase.submitSubmission(userId, submissionId), "제출되었습니다")
}
