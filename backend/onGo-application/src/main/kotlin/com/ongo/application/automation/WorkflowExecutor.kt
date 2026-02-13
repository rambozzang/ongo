package com.ongo.application.automation

import com.ongo.domain.automation.ActionExecutionResult
import com.ongo.domain.automation.WorkflowAction
import com.ongo.domain.automation.WorkflowExecution
import com.ongo.domain.automation.WorkflowExecutionStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Component
class WorkflowExecutor {

    private val log = LoggerFactory.getLogger(WorkflowExecutor::class.java)
    private val scheduler = Executors.newScheduledThreadPool(2, Thread.ofVirtual().factory())

    fun executeActions(
        actions: List<WorkflowAction>,
        contextData: Map<String, Any?>,
    ): List<ActionExecutionResult> {
        val sortedActions = actions.sortedBy { it.sortOrder }
        val results = mutableListOf<ActionExecutionResult>()

        for (action in sortedActions) {
            // Handle delay
            if (action.delayMinutes > 0) {
                try {
                    Thread.sleep(action.delayMinutes.toLong() * 60 * 1000)
                } catch (e: InterruptedException) {
                    results.add(
                        ActionExecutionResult(
                            actionType = action.actionType,
                            sortOrder = action.sortOrder,
                            status = "CANCELLED",
                            message = "Delay interrupted",
                        )
                    )
                    break
                }
            }

            try {
                val result = executeAction(action, contextData)
                results.add(result)

                if (result.status == "FAILED") {
                    log.warn("Action {} failed, continuing with remaining actions", action.actionType)
                }
            } catch (e: Exception) {
                log.error("Error executing action {}: {}", action.actionType, e.message)
                results.add(
                    ActionExecutionResult(
                        actionType = action.actionType,
                        sortOrder = action.sortOrder,
                        status = "FAILED",
                        message = e.message,
                    )
                )
            }
        }

        return results
    }

    private fun executeAction(action: WorkflowAction, contextData: Map<String, Any?>): ActionExecutionResult {
        return when (action.actionType) {
            "SEND_NOTIFICATION" -> executeNotification(action, contextData)
            "AUTO_PUBLISH" -> executeAutoPublish(action, contextData)
            "ADD_TAG" -> executeAddTag(action, contextData)
            "GENERATE_METADATA" -> executeGenerateMetadata(action, contextData)
            "CROSS_POST" -> executeCrossPost(action, contextData)
            "MOVE_STATUS" -> executeMoveStatus(action, contextData)
            else -> ActionExecutionResult(
                actionType = action.actionType,
                sortOrder = action.sortOrder,
                status = "SKIPPED",
                message = "Unknown action type: ${action.actionType}",
            )
        }
    }

    private fun executeNotification(action: WorkflowAction, contextData: Map<String, Any?>): ActionExecutionResult {
        val message = action.config["message"] as? String ?: "자동화 알림"
        log.info("Sending notification: {}", message)
        // In production, this would publish a NotificationEvent
        return ActionExecutionResult(
            actionType = action.actionType,
            sortOrder = action.sortOrder,
            status = "COMPLETED",
            message = "알림 전송: $message",
        )
    }

    private fun executeAutoPublish(action: WorkflowAction, contextData: Map<String, Any?>): ActionExecutionResult {
        val platform = action.config["platform"] as? String ?: "YOUTUBE"
        log.info("Auto-publishing to platform: {}", platform)
        return ActionExecutionResult(
            actionType = action.actionType,
            sortOrder = action.sortOrder,
            status = "COMPLETED",
            message = "$platform 자동 게시 예약됨",
        )
    }

    private fun executeAddTag(action: WorkflowAction, contextData: Map<String, Any?>): ActionExecutionResult {
        val tags = action.config["tags"] as? List<*> ?: emptyList<String>()
        log.info("Adding tags: {}", tags)
        return ActionExecutionResult(
            actionType = action.actionType,
            sortOrder = action.sortOrder,
            status = "COMPLETED",
            message = "태그 추가: ${tags.joinToString(", ")}",
        )
    }

    private fun executeGenerateMetadata(action: WorkflowAction, contextData: Map<String, Any?>): ActionExecutionResult {
        log.info("Generating AI metadata")
        return ActionExecutionResult(
            actionType = action.actionType,
            sortOrder = action.sortOrder,
            status = "COMPLETED",
            message = "AI 메타데이터 생성 요청됨",
        )
    }

    private fun executeCrossPost(action: WorkflowAction, contextData: Map<String, Any?>): ActionExecutionResult {
        val targetPlatforms = action.config["platforms"] as? List<*> ?: emptyList<String>()
        log.info("Cross-posting to: {}", targetPlatforms)
        return ActionExecutionResult(
            actionType = action.actionType,
            sortOrder = action.sortOrder,
            status = "COMPLETED",
            message = "크로스 포스팅 예약: ${targetPlatforms.joinToString(", ")}",
        )
    }

    private fun executeMoveStatus(action: WorkflowAction, contextData: Map<String, Any?>): ActionExecutionResult {
        val targetStatus = action.config["status"] as? String ?: "PUBLISHED"
        log.info("Moving to status: {}", targetStatus)
        return ActionExecutionResult(
            actionType = action.actionType,
            sortOrder = action.sortOrder,
            status = "COMPLETED",
            message = "상태 변경: $targetStatus",
        )
    }
}
