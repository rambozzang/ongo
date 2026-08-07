package com.ongo.api.ugc

import com.ongo.api.ugc.dto.ShortsRenderAvailabilityResponse
import com.ongo.application.ugc.shorts.ShortsRenderUseCase
import com.ongo.common.ResData
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 인코더 가용성 조회.
 *
 * 워크스페이스와 무관한 **서버 설비** 상태라 `/workspaces/{id}/...` 아래에 두지 않는다.
 * ffmpeg 는 배포 전제라 JVM 밖에 있고, 없으면 렌더를 눌러본 뒤에야 알게 된다.
 * 화면은 이 값으로 버튼을 감춘다.
 */
@Tag(name = "쇼츠 렌더", description = "서버 렌더 가용성")
@RestController
@RequestMapping("/api/v1/ugc/shorts/render")
class ShortsRenderAvailabilityController(
    private val renderUseCase: ShortsRenderUseCase,
) {
    @Operation(
        summary = "렌더 가용성 조회",
        description = "인코더가 호스트에 설치돼 있는지 확인한다. available=false 도 정상 응답(200)이다",
    )
    @GetMapping("/availability")
    fun availability(): ResponseEntity<ResData<ShortsRenderAvailabilityResponse>> {
        val a = renderUseCase.availability()
        return ResponseEntity.ok(
            ResData(data = ShortsRenderAvailabilityResponse(a.available, a.reason)),
        )
    }
}
