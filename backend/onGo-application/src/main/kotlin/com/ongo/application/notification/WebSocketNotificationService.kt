package com.ongo.application.notification

import org.slf4j.LoggerFactory
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service

@Service
class WebSocketNotificationService(
    private val messagingTemplate: SimpMessagingTemplate,
) {

    private val log = LoggerFactory.getLogger(WebSocketNotificationService::class.java)

    /**
     * Send a message to a specific user's personal queue.
     */
    fun sendToUser(userId: Long, type: String, payload: Any) {
        val message = mapOf("type" to type, "payload" to payload, "timestamp" to System.currentTimeMillis())
        messagingTemplate.convertAndSend("/queue/user/$userId", message)
        log.debug("Sent WebSocket message to user {}: type={}", userId, type)
    }

    /**
     * Broadcast a message to all members of a team.
     */
    fun broadcastToTeam(teamId: Long, type: String, payload: Any) {
        val message = mapOf("type" to type, "payload" to payload, "timestamp" to System.currentTimeMillis())
        messagingTemplate.convertAndSend("/topic/team/$teamId", message)
        log.debug("Broadcast WebSocket message to team {}: type={}", teamId, type)
    }

    /**
     * Broadcast a real-time activity event.
     */
    fun sendActivity(userId: Long, activity: ActivityEvent) {
        val message = mapOf(
            "type" to "ACTIVITY",
            "payload" to activity,
            "timestamp" to System.currentTimeMillis(),
        )
        messagingTemplate.convertAndSend("/queue/user/$userId", message)
    }
}

data class ActivityEvent(
    val id: String = java.util.UUID.randomUUID().toString(),
    val activityType: String,
    val userName: String,
    val message: String,
    val referenceType: String? = null,
    val referenceId: Long? = null,
    val timestamp: Long = System.currentTimeMillis(),
)
