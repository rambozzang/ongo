package com.ongo.application.ai

import com.ongo.common.enums.AiProvider
import com.ongo.common.exception.BusinessException
import com.ongo.domain.settings.UserSettingsRepository
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.stereotype.Component

@Component
class ChatClientResolver(
    private val chatClientRegistry: ChatClientRegistry,
    private val userSettingsRepository: UserSettingsRepository,
) {

    private val log = LoggerFactory.getLogger(ChatClientResolver::class.java)

    fun resolve(userId: Long): ChatClient {
        val requested = if (userId == 0L) {
            null
        } else {
            userSettingsRepository.findByUserId(userId)?.defaultAiProvider
        }

        val provider = requested
            ?.takeIf(chatClientRegistry::isProviderAvailable)
            ?: FALLBACK_ORDER.firstOrNull(chatClientRegistry::isProviderAvailable)
            ?: throw BusinessException(
                "AI_PROVIDER_NOT_CONFIGURED",
                "사용 가능한 AI 제공자가 설정되지 않았습니다. 관리자에게 API 키를 설정해 주세요.",
            )

        if (requested != null && requested != provider) {
            log.warn("AI 제공자 {} 사용 불가, 설정된 {}으로 대체: userId={}", requested, provider, userId)
        }

        return chatClientRegistry.getClient(provider)
    }

    companion object {
        // Prefer the providers that are normally configured for production, but
        // never assume a provider exists just because its Spring bean exists.
        private val FALLBACK_ORDER = listOf(
            AiProvider.CLAUDE,
            AiProvider.OPENAI,
            AiProvider.GEMINI,
            AiProvider.QWEN,
            AiProvider.KIMI,
            AiProvider.GLM,
            AiProvider.MINIMAX,
        )
    }
}
