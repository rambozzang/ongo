package com.ongo.api.fancommunity

import com.ongo.application.fancommunity.FanCommunityUseCase
import com.ongo.application.fancommunity.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "팬 커뮤니티", description = "팬 커뮤니티 게시판 관리")
@RestController
@RequestMapping("/api/v1/fan-community")
class FanCommunityController(
    private val useCase: FanCommunityUseCase
) {

    @Operation(summary = "게시글 목록 조회")
    @GetMapping("/posts")
    fun getPosts(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam(required = false) type: String?,
    ): ResponseEntity<ResData<List<CommunityPostResponse>>> {
        val result = useCase.getPosts(userId, type)
        return ResData.success(result)
    }

    @Operation(summary = "게시글 상세 조회")
    @GetMapping("/posts/{id}")
    fun getPost(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<CommunityPostResponse>> {
        val result = useCase.getPost(userId, id)
        return ResData.success(result)
    }

    @Operation(summary = "게시글 작성")
    @PostMapping("/posts")
    fun createPost(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: CreatePostRequest,
    ): ResponseEntity<ResData<CommunityPostResponse>> {
        val result = useCase.createPost(userId, "크리에이터", request)
        return ResData.success(result, "게시글이 작성되었습니다")
    }

    @Operation(summary = "게시글 삭제")
    @DeleteMapping("/posts/{id}")
    fun deletePost(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        useCase.deletePost(userId, id)
        return ResData.success(null, "게시글이 삭제되었습니다")
    }

    @Operation(summary = "게시글 좋아요")
    @PostMapping("/posts/{id}/like")
    fun likePost(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        useCase.likePost(userId, id)
        return ResData.success(null)
    }

    @Operation(summary = "게시글 고정/해제")
    @PostMapping("/posts/{id}/pin")
    fun pinPost(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        useCase.pinPost(userId, id)
        return ResData.success(null)
    }

    @Operation(summary = "댓글 목록 조회")
    @GetMapping("/posts/{postId}/comments")
    fun getComments(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable postId: Long,
    ): ResponseEntity<ResData<List<PostCommentResponse>>> {
        val result = useCase.getComments(postId)
        return ResData.success(result)
    }

    @Operation(summary = "댓글 작성")
    @PostMapping("/posts/{postId}/comments")
    fun createComment(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable postId: Long,
        @RequestBody request: CreateCommentRequest,
    ): ResponseEntity<ResData<PostCommentResponse>> {
        val result = useCase.createComment(postId, "크리에이터", request)
        return ResData.success(result, "댓글이 작성되었습니다")
    }

    @Operation(summary = "커뮤니티 요약")
    @GetMapping("/summary")
    fun getSummary(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<CommunitySummaryResponse>> {
        val result = useCase.getSummary(userId)
        return ResData.success(result)
    }
}
