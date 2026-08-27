package com.ongo.api.admin

import com.ongo.application.ugc.shorts.ShortsPilotCandidateUseCase
import com.ongo.application.ugc.shorts.ShortsPilotEnrollmentUseCase
import com.ongo.application.ugc.shorts.dto.ShortsPilotCandidatePage
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 유료 파일럿 코호트 등록. **운영자 전용**이다.
 *
 * ## 왜 /admin 아래인가
 *
 * `SecurityConfig` 에 admin 하위 전체를 `hasRole("ADMIN")` 으로 막는 매처가 이미 있다.
 * 여기에 두면 URL 매처와 `@PreAuthorize` 가 모두 걸려, 어느 한쪽을 실수로 지워도 일반
 * 사용자에게 열리지 않는다. internal 하위는 매처가 없어 보안 설정을 함께 고쳐야 하고,
 * 그 파일은 이번 변경 범위 밖이다.
 *
 * ## 무엇을 주고받지 않는가
 *
 * 요청 본문이 없고 응답에도 runId 와 등록 여부만 담는다. 고객 이메일·영상·결제 정보는
 * 이 경로를 지나지 않는다.
 */
@Tag(name = "관리자", description = "쇼츠 유료 파일럿 코호트 등록")
@RestController
@RequestMapping("/api/v1/admin/shorts-pilot")
@PreAuthorize("hasRole('ADMIN')")
class ShortsPilotEnrollmentController(
    private val enrollmentUseCase: ShortsPilotEnrollmentUseCase,
    private val candidateUseCase: ShortsPilotCandidateUseCase,
) {

    @Operation(
        summary = "파일럿 미등록 실행 후보 조회",
        description = "아직 코호트에 없는 최근 실행을 최신순으로 돌려준다. 이미 등록된 실행은 " +
            "서버에서 제외하므로 목록에 나타나지 않는다. 응답에는 runId·상태·생성 시각·원본 제목만 " +
            "담기며 고객 정보·영상 URL·자막은 포함하지 않는다. 제목은 영상이 지워졌거나 비어 있으면 null 이다.",
    )
    @GetMapping("/runs/candidates")
    fun candidates(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ResData<ShortsPilotCandidatePage>> =
        ResData.success(candidateUseCase.candidates(page = page, size = size))

    data class EnrollmentResponse(
        val runId: Long,
        /** 이미 코호트에 있었으면 true. 두 경우 모두 성공이다. */
        val alreadyEnrolled: Boolean,
    )

    @Operation(
        summary = "파일럿 코호트 등록",
        description = "실행을 유료 파일럿 코호트에 넣는다. 이미 등록된 실행도 성공(alreadyEnrolled=true)이다.",
    )
    @PostMapping("/runs/{runId}/enrollment")
    fun enroll(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable runId: Long,
    ): ResponseEntity<ResData<EnrollmentResponse>> {
        val result = enrollmentUseCase.enroll(actorUserId = userId, runId = runId)
        return ResData.success(EnrollmentResponse(result.runId, result.alreadyEnrolled))
    }
}
