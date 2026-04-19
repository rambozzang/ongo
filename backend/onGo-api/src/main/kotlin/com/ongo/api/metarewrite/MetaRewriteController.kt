package com.ongo.api.metarewrite

import com.ongo.application.metarewrite.MetaRewriteUseCase
import com.ongo.application.metarewrite.dto.MetaRewriteResponse
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "메타데이터 리라이트", description = "AI 기반 영상 메타데이터 트렌드 재작성")
@RestController
@RequestMapping("/api/v1/videos")
class MetaRewriteController(
    private val metaRewriteUseCase: MetaRewriteUseCase,
) {

    @Operation(summary = "AI 메타데이터 리라이트 생성", description = "고성과 영상의 제목/설명/태그를 현재 트렌드에 맞게 AI가 재작성합니다. AI 크레딧 3개 소모.")
    @PostMapping("/{videoId}/rewrite-meta")
    fun rewriteMeta(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable videoId: Long,
    ): ResponseEntity<ResData<MetaRewriteResponse>> {
        val result = metaRewriteUseCase.rewriteMeta(userId, videoId)
        return ResData.success(result, "메타데이터 리라이트가 완료되었습니다")
    }
}
