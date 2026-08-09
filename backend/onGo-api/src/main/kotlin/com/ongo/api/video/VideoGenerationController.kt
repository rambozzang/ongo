package com.ongo.api.video

import com.ongo.api.config.CurrentUser
import com.ongo.application.publicapi.GeneratedVideoUseCase
import com.ongo.application.publicapi.PublicGenerateVideoRequest
import com.ongo.application.publicapi.PublicGeneratedVideoResponse
import com.ongo.common.ResData
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 로그인 사용자가 작성 화면에서 텍스트 슬라이드 영상을 만드는 API. */
@Tag(name = "영상 생성", description = "서버 기반 텍스트 슬라이드 영상 생성")
@RestController
@RequestMapping("/api/v1/videos")
class VideoGenerationController(
    private val generatedVideoUseCase: GeneratedVideoUseCase,
) {
    @Operation(summary = "텍스트 슬라이드 영상 생성")
    @PostMapping("/generate")
    fun generate(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: PublicGenerateVideoRequest,
    ): ResponseEntity<ResData<List<PublicGeneratedVideoResponse>>> =
        ResData.success(generatedVideoUseCase.generate(userId, request))
}
