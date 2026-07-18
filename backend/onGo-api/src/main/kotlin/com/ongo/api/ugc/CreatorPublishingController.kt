package com.ongo.api.ugc

import com.ongo.application.ugc.CampaignPublishingUseCase
import com.ongo.application.ugc.dto.CampaignPostListResponse
import com.ongo.application.ugc.dto.CampaignPostResponse
import com.ongo.application.ugc.dto.RegisterExternalPostRequest
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

@Tag(name = "UGC 게시 (크리에이터)", description = "외부 게시물 URL 등록·내 게시물 상태")
@RestController
@RequestMapping("/api/v1/ugc/me")
class CreatorPublishingController(
    private val publishingUseCase: CampaignPublishingUseCase,
) {

    @Operation(summary = "외부 게시물 등록", description = "이미 SNS에 게시한 게시물 URL을 캠페인에 연결합니다(허용 도메인만).")
    @PostMapping("/submissions/{submissionId}/external-posts")
    fun registerExternal(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable submissionId: Long,
        @RequestBody request: RegisterExternalPostRequest,
    ): ResponseEntity<ResData<CampaignPostResponse>> =
        ResData.success(publishingUseCase.registerExternalPost(userId, submissionId, request), "외부 게시물을 등록했습니다")

    @Operation(summary = "내 제출물 게시물 상태")
    @GetMapping("/submissions/{submissionId}/posts")
    fun myPosts(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable submissionId: Long,
    ): ResponseEntity<ResData<CampaignPostListResponse>> =
        ResData.success(publishingUseCase.listMySubmissionPosts(userId, submissionId))
}
