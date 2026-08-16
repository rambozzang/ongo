package com.ongo.api.config

import com.ongo.common.exception.PlanLimitExceededException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class BusinessExceptionResponseTest {

    @Test
    @DisplayName("비즈니스 오류는 사용자 문구와 안정적인 코드를 분리해 반환한다")
    fun returnsMessageAndCodeSeparately() {
        val response = GlobalExceptionHandler().handleBusiness(
            PlanLimitExceededException(feature = "댓글", limit = 0),
        )

        assertEquals(400, response.statusCode.value())
        assertEquals("댓글 한도를 초과했습니다. 현재 플랜 한도: 0", response.body?.message)
        assertEquals("PLAN_LIMIT_EXCEEDED", response.body?.error)
    }
}
