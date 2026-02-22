package com.ongo.api.audience

import com.ongo.application.audience.AudienceUseCase
import com.ongo.application.audience.dto.*
import com.ongo.common.ResData
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@Tag(name = "오디언스 CRM", description = "팬 프로필 관리, 세그먼트 관리")
@RestController
@RequestMapping("/api/v1/audience")
class AudienceController(
    private val audienceUseCase: AudienceUseCase,
) {

    @Operation(summary = "팬 프로필 목록", description = "참여도순, 상호작용순, 최근 활동순으로 팬 프로필을 조회합니다.")
    @GetMapping("/profiles")
    fun getProfiles(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @RequestParam(defaultValue = "engagement_score") sortBy: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ResData<Map<String, Any>>> {
        val profiles = audienceUseCase.getProfiles(userId, sortBy, page, size)
        val total = audienceUseCase.getProfileCount(userId)
        return ResData.success(mapOf("profiles" to profiles, "total" to total))
    }

    @Operation(summary = "팬 프로필 상세", description = "특정 팬 프로필의 상세 정보를 조회합니다.")
    @GetMapping("/profiles/{profileId}")
    fun getProfile(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @PathVariable profileId: Long,
    ): ResponseEntity<ResData<AudienceProfileResponse>> =
        ResData.success(audienceUseCase.getProfile(userId, profileId))

    @Operation(summary = "팬 프로필 태그 수정", description = "팬 프로필에 태그를 추가/수정합니다.")
    @PutMapping("/profiles/{profileId}/tags")
    fun updateTags(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @PathVariable profileId: Long,
        @RequestBody request: UpdateProfileTagsRequest,
    ): ResponseEntity<ResData<AudienceProfileResponse>> =
        ResData.success(audienceUseCase.updateProfileTags(userId, profileId, request))

    @Operation(summary = "세그먼트 목록", description = "오디언스 세그먼트 목록을 조회합니다.")
    @GetMapping("/segments")
    fun getSegments(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
    ): ResponseEntity<ResData<List<AudienceSegmentResponse>>> =
        ResData.success(audienceUseCase.getSegments(userId))

    @Operation(summary = "세그먼트 생성", description = "새 오디언스 세그먼트를 생성합니다.")
    @PostMapping("/segments")
    fun createSegment(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @RequestBody request: CreateSegmentRequest,
    ): ResponseEntity<ResData<AudienceSegmentResponse>> =
        ResData.success(audienceUseCase.createSegment(userId, request))

    @Operation(summary = "세그먼트 수정", description = "세그먼트 정보를 수정합니다.")
    @PutMapping("/segments/{segmentId}")
    fun updateSegment(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @PathVariable segmentId: Long,
        @RequestBody request: UpdateSegmentRequest,
    ): ResponseEntity<ResData<AudienceSegmentResponse>> =
        ResData.success(audienceUseCase.updateSegment(userId, segmentId, request))

    @Operation(summary = "세그먼트 삭제", description = "세그먼트를 삭제합니다.")
    @DeleteMapping("/segments/{segmentId}")
    fun deleteSegment(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @PathVariable segmentId: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        audienceUseCase.deleteSegment(userId, segmentId)
        return ResData.success(null, "세그먼트가 삭제되었습니다")
    }
}
