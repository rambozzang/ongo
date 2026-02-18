package com.ongo.infrastructure.external.vimeo

import com.ongo.infrastructure.external.vimeo.dto.VimeoCommentRequest
import com.ongo.infrastructure.external.vimeo.dto.VimeoCommentResponse
import com.ongo.infrastructure.external.vimeo.dto.VimeoCommentsResponse
import com.ongo.infrastructure.external.vimeo.dto.VimeoTokenResponse
import com.ongo.infrastructure.external.vimeo.dto.VimeoUploadRequest
import com.ongo.infrastructure.external.vimeo.dto.VimeoUserResponse
import com.ongo.infrastructure.external.vimeo.dto.VimeoVideoResponse
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.service.annotation.DeleteExchange
import org.springframework.web.service.annotation.GetExchange
import org.springframework.web.service.annotation.HttpExchange
import org.springframework.web.service.annotation.PatchExchange
import org.springframework.web.service.annotation.PostExchange

@HttpExchange
interface VimeoApi {

    @PostExchange("/me/videos")
    fun createVideo(
        @RequestHeader("Authorization") authorization: String,
        @RequestBody request: VimeoUploadRequest,
    ): VimeoVideoResponse

    @GetExchange("/videos/{videoId}")
    fun getVideo(
        @org.springframework.web.bind.annotation.PathVariable("videoId") videoId: String,
        @RequestHeader("Authorization") authorization: String,
    ): VimeoVideoResponse

    @DeleteExchange("/videos/{videoId}")
    fun deleteVideo(
        @org.springframework.web.bind.annotation.PathVariable("videoId") videoId: String,
        @RequestHeader("Authorization") authorization: String,
    )

    @PatchExchange("/videos/{videoId}")
    fun updateVideo(
        @org.springframework.web.bind.annotation.PathVariable("videoId") videoId: String,
        @RequestHeader("Authorization") authorization: String,
        @RequestBody metadata: Map<String, Any>,
    ): VimeoVideoResponse

    @GetExchange("/me")
    fun getUser(
        @RequestHeader("Authorization") authorization: String,
    ): VimeoUserResponse

    // --- Comment API ---

    @GetExchange("/videos/{videoId}/comments")
    fun getComments(
        @org.springframework.web.bind.annotation.PathVariable("videoId") videoId: String,
        @RequestParam("per_page") perPage: Int,
        @RequestParam("page", required = false) page: Int?,
        @RequestHeader("Authorization") authorization: String,
    ): VimeoCommentsResponse

    @PostExchange("/videos/{videoId}/comments")
    fun createComment(
        @org.springframework.web.bind.annotation.PathVariable("videoId") videoId: String,
        @RequestHeader("Authorization") authorization: String,
        @RequestBody body: VimeoCommentRequest,
    ): VimeoCommentResponse

    @DeleteExchange("/videos/{videoId}/comments/{commentId}")
    fun deleteComment(
        @org.springframework.web.bind.annotation.PathVariable("videoId") videoId: String,
        @org.springframework.web.bind.annotation.PathVariable("commentId") commentId: String,
        @RequestHeader("Authorization") authorization: String,
    )
}

@HttpExchange
interface VimeoOAuthApi {

    @PostExchange("/oauth/access_token")
    fun exchangeToken(
        @RequestBody body: Map<String, String>,
    ): VimeoTokenResponse
}
