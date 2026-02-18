package com.ongo.application.ai

import com.ongo.common.enums.AiProvider
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
        if (userId == 0L) {
            return chatClientRegistry.getClient(AiProvider.CLAUDE)
        }

        val provider = userSettingsRepository.findByUserId(userId)?.defaultAiProvider
            ?: AiProvider.CLAUDE

        if (!chatClientRegistry.isProviderAvailable(provider)) {
            log.warn("AI 제공자 {} 사용 불가, CLAUDE로 대체: userId={}", provider, userId)
            return chatClientRegistry.getClient(AiProvider.CLAUDE)
        }

        return chatClientRegistry.getClient(provider)
    }
}
