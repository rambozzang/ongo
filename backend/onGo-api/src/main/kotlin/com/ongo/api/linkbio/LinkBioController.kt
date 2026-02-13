package com.ongo.api.linkbio

import com.ongo.application.linkbio.LinkBioUseCase
import com.ongo.application.linkbio.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "Link-in-bio", description = "링크 페이지 관리 및 공개 페이지 제공")
@RestController
@RequestMapping("/api/v1/linkbio")
class LinkBioController(
    private val linkBioUseCase: LinkBioUseCase
) {

    @Operation(summary = "내 링크 페이지 조회")
    @GetMapping
    fun getPage(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<LinkBioPageResponse?>> {
        val result = linkBioUseCase.getPage(userId)
        return ResData.success(result)
    }

    @Operation(summary = "링크 페이지 생성")
    @PostMapping
    fun createPage(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: CreatePageRequest,
    ): ResponseEntity<ResData<LinkBioPageResponse>> {
        val result = linkBioUseCase.createPage(userId, request)
        return ResData.success(result, "링크 페이지가 생성되었습니다")
    }

    @Operation(summary = "링크 페이지 수정")
    @PutMapping
    fun updatePage(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: UpdatePageRequest,
    ): ResponseEntity<ResData<LinkBioPageResponse>> {
        val result = linkBioUseCase.updatePage(userId, request)
        return ResData.success(result)
    }

    @Operation(summary = "링크 일괄 업데이트")
    @PutMapping("/links")
    fun updateLinks(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: UpdateLinksRequest,
    ): ResponseEntity<ResData<LinkBioPageResponse>> {
        val result = linkBioUseCase.updateLinks(userId, request)
        return ResData.success(result)
    }

    @Operation(summary = "발행 상태 변경")
    @PutMapping("/publish")
    fun togglePublish(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: PublishRequest,
    ): ResponseEntity<ResData<LinkBioPageResponse>> {
        val result = linkBioUseCase.togglePublish(userId, request)
        return ResData.success(result)
    }

    @Operation(summary = "링크 페이지 통계 조회")
    @GetMapping("/analytics")
    fun getAnalytics(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<LinkBioAnalyticsResponse>> {
        val result = linkBioUseCase.getAnalytics(userId)
        return ResData.success(result)
    }

    @Operation(summary = "공개 페이지 조회 (인증 불필요)")
    @GetMapping("/{slug}")
    fun getPublicPage(
        @PathVariable slug: String,
    ): ResponseEntity<ResData<LinkBioPublicResponse>> {
        val result = linkBioUseCase.getPublicPage(slug)
        return ResData.success(result)
    }
}
