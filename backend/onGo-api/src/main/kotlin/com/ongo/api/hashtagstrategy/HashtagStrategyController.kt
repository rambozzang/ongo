package com.ongo.api.hashtagstrategy

import com.ongo.application.hashtagstrategy.HashtagStrategyUseCase
import com.ongo.application.hashtagstrategy.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "해시태그 전략", description = "해시태그 세트 생성 및 관리")
@RestController
@RequestMapping("/api/v1/hashtag-strategy")
class HashtagStrategyController(
    private val hashtagStrategyUseCase: HashtagStrategyUseCase
) {

    @Operation(summary = "해시태그 세트 목록 조회")
    @GetMapping("/sets")
    fun listHashtagSets(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<HashtagSetResponse>>> {
        val result = hashtagStrategyUseCase.listHashtagSets(userId)
        return ResData.success(result)
    }

    @Operation(summary = "해시태그 세트 생성")
    @PostMapping("/sets")
    fun createHashtagSet(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: CreateHashtagSetRequest,
    ): ResponseEntity<ResData<HashtagSetResponse>> {
        val result = hashtagStrategyUseCase.createHashtagSet(userId, request)
        return ResData.success(result, "해시태그 세트가 생성되었습니다")
    }

    @Operation(summary = "해시태그 세트 삭제")
    @DeleteMapping("/sets/{id}")
    fun deleteHashtagSet(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        hashtagStrategyUseCase.deleteHashtagSet(userId, id)
        return ResData.success(null, "해시태그 세트가 삭제되었습니다")
    }
}
