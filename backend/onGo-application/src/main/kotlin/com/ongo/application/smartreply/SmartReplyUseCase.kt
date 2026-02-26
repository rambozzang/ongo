package com.ongo.application.smartreply

import com.ongo.application.smartreply.dto.*
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.smartreply.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class SmartReplyUseCase(
    private val smartReplyRuleRepository: SmartReplyRuleRepository,
    private val smartReplySuggestionRepository: SmartReplySuggestionRepository,
    private val smartReplyConfigRepository: SmartReplyConfigRepository,
) {

    fun getSuggestions(userId: Long): List<SmartReplySuggestionResponse> {
        return smartReplySuggestionRepository.findByUserId(userId).map { it.toResponse() }
    }

    @Transactional
    fun sendReply(userId: Long, request: SendReplyRequest) {
        val suggestion = smartReplySuggestionRepository.findById(request.suggestionId)
            ?: throw NotFoundException("답변 제안", request.suggestionId)
        smartReplySuggestionRepository.delete(request.suggestionId)
    }

    @Transactional
    fun dismissSuggestion(userId: Long, suggestionId: String) {
        smartReplySuggestionRepository.findById(suggestionId)
            ?: throw NotFoundException("답변 제안", suggestionId)
        smartReplySuggestionRepository.delete(suggestionId)
    }

    fun getRules(userId: Long): List<SmartReplyRuleResponse> {
        return smartReplyRuleRepository.findByUserId(userId).map { it.toResponse() }
    }

    @Transactional
    fun createRule(userId: Long, request: CreateRuleRequest): SmartReplyRuleResponse {
        val rule = SmartReplyRule(
            userId = userId,
            name = request.name,
            context = request.context,
            triggerKeywords = request.triggerKeywords.joinToString(","),
            sentiment = request.sentiment,
            tone = request.tone,
            templateText = request.templateText,
            useAi = request.useAi,
            autoSend = request.autoSend,
            platform = request.platform,
        )
        return smartReplyRuleRepository.save(rule).toResponse()
    }

    @Transactional
    fun updateRule(userId: Long, ruleId: Long, request: UpdateRuleRequest): SmartReplyRuleResponse {
        val rule = smartReplyRuleRepository.findById(ruleId)
            ?: throw NotFoundException("답변 규칙", ruleId)
        if (rule.userId != userId) throw ForbiddenException("해당 답변 규칙에 대한 권한이 없습니다")
        val updated = rule.copy(
            name = request.name ?: rule.name,
            isActive = request.isActive ?: rule.isActive,
            triggerKeywords = request.triggerKeywords?.joinToString(",") ?: rule.triggerKeywords,
            tone = request.tone ?: rule.tone,
            templateText = request.templateText ?: rule.templateText,
            useAi = request.useAi ?: rule.useAi,
            autoSend = request.autoSend ?: rule.autoSend,
        )
        return smartReplyRuleRepository.update(updated).toResponse()
    }

    @Transactional
    fun deleteRule(userId: Long, ruleId: Long) {
        val rule = smartReplyRuleRepository.findById(ruleId)
            ?: throw NotFoundException("답변 규칙", ruleId)
        if (rule.userId != userId) throw ForbiddenException("해당 답변 규칙에 대한 권한이 없습니다")
        smartReplyRuleRepository.delete(ruleId)
    }

    fun getStats(userId: Long): SmartReplyStatsResponse {
        return SmartReplyStatsResponse(
            totalRepliesSent = 0,
            avgResponseTime = 0.0,
            sentimentBreakdown = "{}",
            topKeywords = "[]",
            repliesByPlatform = "[]",
            automatedPercentage = 0.0,
            satisfactionScore = 0.0,
        )
    }

    fun getConfig(userId: Long): SmartReplyConfigResponse {
        val config = smartReplyConfigRepository.findByUserId(userId)
        return if (config != null) {
            config.toConfigResponse()
        } else {
            SmartReplyConfigResponse(
                defaultTone = "FRIENDLY",
                enableAutoReply = false,
                maxAutoRepliesPerDay = 50,
                excludeKeywords = "[]",
                replyDelay = 0,
                platforms = "[]",
            )
        }
    }

    @Transactional
    fun updateConfig(userId: Long, request: UpdateConfigRequest): SmartReplyConfigResponse {
        val existing = smartReplyConfigRepository.findByUserId(userId)
        if (existing != null) {
            val updated = existing.copy(
                defaultTone = request.defaultTone ?: existing.defaultTone,
                enableAutoReply = request.enableAutoReply ?: existing.enableAutoReply,
                maxAutoRepliesPerDay = request.maxAutoRepliesPerDay ?: existing.maxAutoRepliesPerDay,
                excludeKeywords = request.excludeKeywords?.joinToString(",") ?: existing.excludeKeywords,
                replyDelay = request.replyDelay ?: existing.replyDelay,
                platforms = request.platforms?.joinToString(",") ?: existing.platforms,
            )
            return smartReplyConfigRepository.update(updated).toConfigResponse()
        } else {
            val config = SmartReplyConfig(
                userId = userId,
                defaultTone = request.defaultTone ?: "FRIENDLY",
                enableAutoReply = request.enableAutoReply ?: false,
                maxAutoRepliesPerDay = request.maxAutoRepliesPerDay ?: 50,
                excludeKeywords = request.excludeKeywords?.joinToString(",") ?: "[]",
                replyDelay = request.replyDelay ?: 0,
                platforms = request.platforms?.joinToString(",") ?: "[]",
            )
            return smartReplyConfigRepository.save(config).toConfigResponse()
        }
    }

    private fun SmartReplySuggestion.toResponse() = SmartReplySuggestionResponse(
        id = id,
        originalText = originalText,
        originalAuthor = originalAuthor,
        platform = platform,
        context = context,
        sentiment = sentiment,
        suggestions = suggestions,
        videoId = videoId,
        videoTitle = videoTitle,
        createdAt = createdAt,
    )

    private fun SmartReplyRule.toResponse() = SmartReplyRuleResponse(
        id = id!!,
        name = name,
        isActive = isActive,
        context = context,
        triggerKeywords = triggerKeywords,
        sentiment = sentiment,
        tone = tone,
        templateText = templateText,
        useAi = useAi,
        autoSend = autoSend,
        platform = platform,
        replyCount = replyCount,
        lastUsed = lastUsed,
        createdAt = createdAt,
    )

    private fun SmartReplyConfig.toConfigResponse() = SmartReplyConfigResponse(
        defaultTone = defaultTone,
        enableAutoReply = enableAutoReply,
        maxAutoRepliesPerDay = maxAutoRepliesPerDay,
        excludeKeywords = excludeKeywords,
        replyDelay = replyDelay,
        platforms = platforms,
    )
}
