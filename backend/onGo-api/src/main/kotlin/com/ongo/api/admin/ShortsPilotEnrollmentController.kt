package com.ongo.api.admin

import com.ongo.application.ugc.shorts.ShortsPilotEnrollmentUseCase
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
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
) {

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
