package com.ongo.application.smartreply.dto

import java.time.LocalDateTime

data class CreateRuleRequest(
    val name: String,
    val context: String = "COMMENT",
    val triggerKeywords: List<String> = emptyList(),
    val sentiment: String? = null,
    val tone: String = "FRIENDLY",
    val templateText: String? = null,
    val useAi: Boolean = false,
    val autoSend: Boolean = false,
    val platform: String? = null,
)

data class UpdateRuleRequest(
    val name: String? = null,
    val isActive: Boolean? = null,
    val triggerKeywords: List<String>? = null,
    val tone: String? = null,
    val templateText: String? = null,
    val useAi: Boolean? = null,
    val autoSend: Boolean? = null,
)

data class SmartReplyRuleResponse(
    val id: Long,
    val name: String,
    val isActive: Boolean,
    val context: String,
    val triggerKeywords: String,
    val sentiment: String?,
    val tone: String,
    val templateText: String?,
    val useAi: Boolean,
    val autoSend: Boolean,
    val platform: String?,
    val replyCount: Int,
    val lastUsed: LocalDateTime?,
    val createdAt: LocalDateTime?,
)

data class SmartReplySuggestionResponse(
    val id: String,
    val originalText: String,
    val originalAuthor: String?,
    val platform: String,
    val context: String,
    val sentiment: String,
    val suggestions: String,
    val videoId: String?,
    val videoTitle: String?,
    val createdAt: LocalDateTime?,
)

data class SendReplyRequest(
    val suggestionId: String,
    val text: String,
)

data class SmartReplyStatsResponse(
    val totalRepliesSent: Int,
    val avgResponseTime: Double,
    val sentimentBreakdown: String,
    val topKeywords: String,
    val repliesByPlatform: String,
    val automatedPercentage: Double,
    val satisfactionScore: Double,
)

data class SmartReplyConfigResponse(
    val defaultTone: String,
    val enableAutoReply: Boolean,
    val maxAutoRepliesPerDay: Int,
    val excludeKeywords: String,
    val replyDelay: Int,
    val platforms: String,
)

data class UpdateConfigRequest(
    val defaultTone: String? = null,
    val enableAutoReply: Boolean? = null,
    val maxAutoRepliesPerDay: Int? = null,
    val excludeKeywords: List<String>? = null,
    val replyDelay: Int? = null,
    val platforms: List<String>? = null,
)
