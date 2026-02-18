package com.ongo.infrastructure.ai

import com.ongo.application.ai.ChatClientRegistry
import com.ongo.common.enums.AiProvider
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

@Component
class ChatClientRegistryImpl(
    @Qualifier("anthropicChatClient") private val anthropicChatClient: ChatClient,
    @Qualifier("openaiChatClient") private val openaiChatClient: ChatClient,
    @Autowired(required = false) @Qualifier("geminiChatClient") private val geminiChatClient: ChatClient?,
) : ChatClientRegistry {

    private val clients: Map<AiProvider, ChatClient> = buildMap {
        put(AiProvider.CLAUDE, anthropicChatClient)
        put(AiProvider.OPENAI, openaiChatClient)
        if (geminiChatClient != null) {
            put(AiProvider.GEMINI, geminiChatClient)
        }
    }

    override fun getClient(provider: AiProvider): ChatClient =
        clients[provider]
            ?: clients[AiProvider.CLAUDE]
            ?: error("Anthropic ChatClient is required but not available")

    override fun isProviderAvailable(provider: AiProvider): Boolean =
        clients.containsKey(provider)
}
