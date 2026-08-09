package com.ongo.api.scheduleoptimizer

import com.ongo.application.scheduleoptimizer.ScheduleOptimizerUseCase
import com.ongo.common.annotation.RequiresPermission
import com.ongo.common.enums.Permission
import io.mockk.mockk
import kotlin.reflect.full.declaredFunctions
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.jupiter.api.Test

class ScheduleOptimizerControllerTest {
    private val controller = ScheduleOptimizerController(mockk<ScheduleOptimizerUseCase>())

    @Test
    fun `every optimizer endpoint declares the least privilege permission`() {
        val permissions = ScheduleOptimizerController::class.declaredFunctions
            .filter { it.name in setOf("generateSlots", "getSlots", "getRecommendations", "applyRecommendation", "getSummary") }
            .associate { function ->
                function.name to function.annotations.filterIsInstance<RequiresPermission>().singleOrNull()?.value
            }

        assertEquals(
            mapOf(
                "generateSlots" to Permission.AI_USE,
                "getSlots" to Permission.SCHEDULE_READ,
                "getRecommendations" to Permission.SCHEDULE_READ,
                "applyRecommendation" to Permission.SCHEDULE_UPDATE,
                "getSummary" to Permission.SCHEDULE_READ,
            ),
            permissions,
        )
        assertNotNull(controller)
    }
}
