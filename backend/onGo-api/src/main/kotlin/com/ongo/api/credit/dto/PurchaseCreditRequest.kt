package com.ongo.api.credit.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class PurchaseCreditRequest(
    @field:NotBlank(message = "패키지 타입은 필수입니다")
    @field:Size(max = 50, message = "패키지 타입은 최대 50자까지 입력할 수 있습니다")
    val packageType: String,
    @field:NotBlank(message = "결제 수단은 필수입니다")
    @field:Size(max = 50, message = "결제 수단은 최대 50자까지 입력할 수 있습니다")
    val paymentMethod: String,
)
