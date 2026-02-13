package com.ongo.api.auth.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class SocialLoginRequest(
    @field:NotBlank(message = "인증 코드는 필수입니다")
    @field:Size(max = 2048, message = "인증 코드는 최대 2048자까지 입력할 수 있습니다")
    val code: String,
    @field:NotBlank(message = "리다이렉트 URI는 필수입니다")
    @field:Size(max = 2048, message = "리다이렉트 URI는 최대 2048자까지 입력할 수 있습니다")
    val redirectUri: String,
)
