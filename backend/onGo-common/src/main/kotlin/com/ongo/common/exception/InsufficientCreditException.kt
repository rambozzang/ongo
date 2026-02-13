package com.ongo.common.exception

class InsufficientCreditException(
    val required: Int,
    val available: Int,
) : BusinessException("CREDIT_INSUFFICIENT", "크레딧이 부족합니다. 필요: $required, 잔여: $available")
