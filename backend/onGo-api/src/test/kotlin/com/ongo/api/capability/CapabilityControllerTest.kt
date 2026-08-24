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
        assertEquals(36, response.data?.size)
    }

    @Test
    fun `deployment can disable a capability without changing the menu contract`() {
        val response = CapabilityController(CapabilityUseCase("ugc/shorts/runs,admin")).list()

        assertEquals(false, response.data?.first { it.key == "ugc/shorts/runs" }?.enabled)
        assertEquals(false, response.data?.first { it.key == "admin" }?.enabled)
        assertEquals(true, response.data?.first { it.key == "compose" }?.enabled)
    }
}
