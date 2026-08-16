package com.ongo.infrastructure.ai

import com.ongo.application.ai.ChatClientRegistry
import com.ongo.common.enums.AiProvider
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class ChatClientRegistryImpl(
    @Qualifier("anthropicChatClient") private val anthropicChatClient: ChatClient,
    @Qualifier("openaiChatClient") private val openaiChatClient: ChatClient,
    @Autowired(required = false) @Qualifier("geminiChatClient") private val geminiChatClient: ChatClient?,
    @Qualifier("qwenChatClient") private val qwenChatClient: ChatClient,
    @Qualifier("kimiChatClient") private val kimiChatClient: ChatClient,
    @Qualifier("glmChatClient") private val glmChatClient: ChatClient,
    @Qualifier("minimaxChatClient") private val minimaxChatClient: ChatClient,
    @param:Value("\${spring.ai.anthropic.api-key:}") private val anthropicApiKey: String,
    @param:Value("\${spring.ai.openai.api-key:}") private val openAiApiKey: String,
    @param:Value("\${spring.ai.google.genai.api-key:}") private val geminiApiKey: String,
    @param:Value("\${dashscope.api-key:}") private val dashScopeApiKey: String,
) : ChatClientRegistry {

    private val clients: Map<AiProvider, ChatClient> = buildMap {
        put(AiProvider.CLAUDE, anthropicChatClient)
        put(AiProvider.OPENAI, openaiChatClient)
        if (geminiChatClient != null) {
            put(AiProvider.GEMINI, geminiChatClient)
        }
        put(AiProvider.QWEN, qwenChatClient)
        put(AiProvider.KIMI, kimiChatClient)
        put(AiProvider.GLM, glmChatClient)
        put(AiProvider.MINIMAX, minimaxChatClient)
    }

    private val configured: Set<AiProvider> = buildSet {
        if (isRealCredential(anthropicApiKey)) add(AiProvider.CLAUDE)
        if (isRealCredential(openAiApiKey)) add(AiProvider.OPENAI)
        if (geminiChatClient != null && isRealCredential(geminiApiKey)) add(AiProvider.GEMINI)
        if (isRealCredential(dashScopeApiKey)) {
            add(AiProvider.QWEN)
            add(AiProvider.KIMI)
            add(AiProvider.GLM)
            add(AiProvider.MINIMAX)
        }
    }

    override fun getClient(provider: AiProvider): ChatClient =
        clients[provider]?.takeIf { provider in configured }
            ?: error("AI provider is not configured: $provider")

    override fun isProviderAvailable(provider: AiProvider): Boolean =
        provider in configured && clients.containsKey(provider)

    private fun isRealCredential(value: String): Boolean {
        val normalized = value.trim().lowercase()
        return normalized.length >= MIN_CREDENTIAL_LENGTH &&
            listOf("dummy", "placeholder", "change-me", "your-", "localhost")
                .none(normalized::contains)
    }

    companion object {
        private const val MIN_CREDENTIAL_LENGTH = 8
    }
}
