package com.ongo.api.contentcluster

import com.ongo.application.contentcluster.ContentClusterUseCase
import com.ongo.application.contentcluster.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "콘텐츠 클러스터", description = "관련 콘텐츠 자동 클러스터링 및 분석")
@RestController
@RequestMapping("/api/v1/content-clusters")
class ContentClusterController(
    private val useCase: ContentClusterUseCase
) {

    @Operation(summary = "클러스터 목록 조회")
    @GetMapping
    fun getClusters(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<ContentClusterResponse>>> {
        val result = useCase.getClusters(userId)
        return ResData.success(result)
    }

    @Operation(summary = "클러스터 상세 조회")
    @GetMapping("/{id}")
    fun getCluster(
        @PathVariable id: Long,
    ): ResponseEntity<ResData<ContentClusterResponse?>> {
        val result = useCase.getCluster(id)
        return ResData.success(result)
    }

    @Operation(summary = "클러스터 내 콘텐츠 목록 조회")
    @GetMapping("/{clusterId}/contents")
    fun getContents(
        @PathVariable clusterId: Long,
    ): ResponseEntity<ResData<List<ClusterContentResponse>>> {
        val result = useCase.getContents(clusterId)
        return ResData.success(result)
    }

    @Operation(summary = "클러스터 요약")
    @GetMapping("/summary")
    fun getSummary(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<ContentClusterSummaryResponse>> {
        val result = useCase.getSummary(userId)
        return ResData.success(result)
    }
}
