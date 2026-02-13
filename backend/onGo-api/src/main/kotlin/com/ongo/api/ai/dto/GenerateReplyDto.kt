package com.ongo.api.ai.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class GenerateReplyRequest(
    @field:NotBlank(message = "댓글 원문은 필수입니다")
    @field:Size(max = 2000, message = "댓글은 최대 2000자까지 입력할 수 있습니다")
    val comment: String,
    @field:Size(max = 100, message = "채널 톤은 최대 100자까지 입력할 수 있습니다")
    val channelTone: String = "friendly",
    @field:Size(max = 1000, message = "추가 맥락은 최대 1000자까지 입력할 수 있습니다")
    val context: String? = null,
)

data class GenerateReplyResponse(
    val replies: List<ToneReply>,
) {
    data class ToneReply(
        val tone: String,
        val reply: String,
    )
}
