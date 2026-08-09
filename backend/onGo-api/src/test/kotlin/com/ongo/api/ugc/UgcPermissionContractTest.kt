package com.ongo.api.ugc

import com.ongo.common.annotation.RequiresPermission
import kotlin.reflect.KClass
import kotlin.reflect.full.declaredFunctions
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Workspace-scoped UGC endpoints must participate in the same team permission
 * check as the rest of the product. Ownership checks in the use cases protect
 * the tenant boundary; this guard protects the role boundary at the HTTP edge.
 */
class UgcPermissionContractTest {
    private val workspaceControllers: List<KClass<*>> = listOf(
        CampaignAnalyticsController::class,
        CampaignAuditController::class,
        CampaignController::class,
        CampaignParticipationController::class,
        CampaignPublishingController::class,
        CampaignRewardController::class,
        CampaignSubmissionController::class,
        ShortsPipelineController::class,
        ShortsPromptController::class,
        ShortsSheetController::class,
        ShortsTemplateController::class,
    )

    @Test
    fun `every workspace scoped UGC route declares a role permission`() {
        val protectedRoutes = workspaceControllers.flatMap { controller ->
            controller.declaredFunctions
                .filter { function -> function.annotations.any { it.annotationClass.simpleName?.endsWith("Mapping") == true } }
                .map { function -> controller.simpleName.orEmpty() to function }
        }

        assertTrue(protectedRoutes.isNotEmpty())
        protectedRoutes.forEach { (controller, function) ->
            assertNotNull(
                function.annotations.filterIsInstance<RequiresPermission>().singleOrNull(),
                "$controller.${function.name} must declare @RequiresPermission",
            )
        }
    }
}
