package com.ongo.api.capability

import com.ongo.application.capability.CapabilityUseCase
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class CapabilityControllerTest {
    @Test
    fun `returns the server owned active menu contract`() {
        val response = CapabilityController(CapabilityUseCase()).list()

        assertTrue(response.success)
        assertEquals(true, response.data?.first { it.key == "compose" }?.enabled)
        assertTrue(response.data?.any { it.key == "ugc/shorts/runs" } == true)
        assertTrue(response.data?.none { it.key == "keyword-research" || it.key == "trends" } == true)
        assertTrue(response.data?.any { it.key == "competitors" } == true)
        assertEquals(37, response.data?.size)
    }

    /*
     * 이 응답이 프런트 메뉴의 유일한 근거다.
     *
     * 런타임 의존성이 주입되지 않은 배포에서 유료 기능이 `enabled = true` 로 나가면,
     * 프런트는 AI·UGC·결제를 열고 사용자는 클릭한 뒤에야 실패를 본다. API 경계에서
     * 그 값이 실제로 false 인지 고정한다 — 유스케이스 단위 테스트만으로는 응답이 어떻게
     * 나가는지 보장하지 못한다.
     */
    @Test
    fun `runtime dependencies missing closes paid features but keeps the subscription screen`() {
        val response = CapabilityController(CapabilityUseCase()).list()

        assertEquals(false, response.data?.first { it.key == "ai" }?.enabled)
        assertEquals(false, response.data?.first { it.key == "ugc/shorts/runs" }?.enabled)
        assertEquals(false, response.data?.first { it.key == "payment" }?.enabled)
        // 결제를 시작하지 못할 뿐 플랜·크레딧 조회까지 막지 않는다.
        assertEquals(true, response.data?.first { it.key == "subscription" }?.enabled)
        assertEquals(true, response.data?.first { it.key == "compose" }?.enabled)
        // 비활성 항목에는 사용자에게 보여줄 이유가 반드시 있다.
        assertTrue(response.data?.filterNot { it.enabled }?.all { !it.reason.isNullOrBlank() } == true)
    }

    @Test
    fun `deployment can disable a capability without changing the menu contract`() {
        val response = CapabilityController(CapabilityUseCase("ugc/shorts/runs,admin")).list()

        assertEquals(false, response.data?.first { it.key == "ugc/shorts/runs" }?.enabled)
        assertEquals(false, response.data?.first { it.key == "admin" }?.enabled)
        assertEquals(true, response.data?.first { it.key == "compose" }?.enabled)
    }
}
